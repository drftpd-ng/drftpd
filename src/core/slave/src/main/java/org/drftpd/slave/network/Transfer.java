/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * DrFTPD is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with DrFTPD; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.drftpd.slave.network;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.drftpd.common.exceptions.TransferDeniedException;
import org.drftpd.common.exceptions.TransferFailedException;
import org.drftpd.common.exceptions.TransferSlowException;
import org.drftpd.common.io.AddAsciiOutputStream;
import org.drftpd.common.io.PhysicalFile;
import org.drftpd.common.network.PassiveConnection;
import org.drftpd.common.slave.Connection;
import org.drftpd.common.slave.TransferIndex;
import org.drftpd.common.slave.TransferStatus;
import org.drftpd.common.util.HostMask;
import org.drftpd.slave.Slave;
import org.drftpd.slave.exceptions.FileExistsException;
import org.drftpd.slave.exceptions.ObjectNotFoundException;

import javax.net.ssl.SSLSocket;
import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.regex.PatternSyntaxException;
import java.util.zip.CRC32;
import java.util.zip.CheckedInputStream;
import java.util.zip.CheckedOutputStream;

/**
 * @author zubov
 * @version $Id$
 */
public class Transfer {
    public static final char TRANSFER_RECEIVING_UPLOAD = 'R';
    public static final char TRANSFER_SENDING_DOWNLOAD = 'S';
    public static final char TRANSFER_UNKNOWN = 'U';
    private static final Logger logger = LogManager.getLogger(Transfer.class);
    private static final String separator = "/";
    private static final long _transferProgressAnnounce = 524288L; // Report every 512KB
    private String _abortReason = null;
    private CRC32 _checksum = null;
    private Connection _conn;
    private char _direction;
    private long _finished = 0;
    private ThrottledInputStream _int;
    private InputStream _in;
    private final char _mode = 'I';
    private OutputStream _out;
    private final Slave _slave;
    private Socket _sock;
    private long _started = 0;
    private long _transferred = 0;
    private final TransferIndex _transferIndex;
    private String _pathForUpload = null;
    private long _minSpeed = 0L;

    private long _maxSpeed = 0L;

    /**
     * Start undefined transfer.
     */
    public Transfer(Connection conn, Slave slave, TransferIndex transferIndex) {
        if (conn == null) {
            throw new RuntimeException();
        }

        if (slave == null) {
            throw new RuntimeException();
        }

        if (transferIndex == null) {
            throw new RuntimeException();
        }

        _slave = slave;
        _conn = conn;
        synchronized (this) {
            _direction = Transfer.TRANSFER_UNKNOWN;
        }
        _transferIndex = transferIndex;
    }

    public int hashCode() {
        return _transferIndex.hashCode();
    }

    public synchronized void abort(String reason) {
        logger.warn("Abort was requested, starting to abort transfer");
        try {
            _abortReason = reason;
            if (_int != null) {
                _int.wake();
            }

        } finally {
            if (_conn != null) {
                _conn.abort();
            }
            if (_sock != null) {
                try {
                    _sock.close();
                } catch (IOException ignored) {}
            }
            if (_out != null) {
                try {
                    _out.close();
                } catch (IOException ignored) {}
            }
            if (_in != null) {
                try {
                    _in.close();
                } catch (IOException ignored) {}
            }
        }
    }

    public long getChecksum() {
        if (_checksum == null) {
            return 0;
        }

        return _checksum.getValue();
    }

    public long getElapsed() {
        if (_finished == 0) {
            return System.currentTimeMillis() - _started;
        }

        return _finished - _started;
    }

    public int getLocalPort() {
        if (_conn instanceof PassiveConnection) {
            return ((PassiveConnection) _conn).getLocalPort();
        }

        throw new IllegalStateException("getLocalPort() called on a non-passive transfer");
    }

    public char getState() {
        return _direction;
    }

    public TransferStatus getTransferStatus() {
        return new TransferStatus(getElapsed(), getTransferred(), getChecksum(), isFinished(), getTransferIndex());
    }

    public boolean isFinished() {
        return (_finished != 0 || _abortReason != null);
    }

    public long getTransferred() {
        return _transferred;
    }

    public TransferIndex getTransferIndex() {
        return _transferIndex;
    }

    private Transfer getUploadForPath(String path) throws ObjectNotFoundException {
        for (Transfer transfer : _slave.getTransfersList()) {
            if (!transfer.isReceivingUploading()) {
                continue;
            }
            if (transfer._pathForUpload.equalsIgnoreCase(path)) {
                return transfer;
            }
        }
        throw new ObjectNotFoundException("Transfer not found");
    }

    public int getTransferSpeed() {
        long elapsed = getElapsed();

        if (_transferred == 0) {
            return 0;
        }

        if (elapsed == 0) {
            return 0;
        }

        return (int) (_transferred / ((float) elapsed / (float) 1000));
    }

    public long getMinSpeed() {
        return _minSpeed;
    }

    public void setMinSpeed(long minSpeed) {
        _minSpeed = minSpeed;
    }

    public long getMaxSpeed() {
        return _maxSpeed;
    }

    public void setMaxSpeed(long maxSpeed) {
        _maxSpeed = maxSpeed;
    }

    public boolean isReceivingUploading() {
        return _direction == Transfer.TRANSFER_RECEIVING_UPLOAD;
    }

    public boolean isSendingUploading() {
        return _direction == Transfer.TRANSFER_SENDING_DOWNLOAD;
    }

    /**
     * Receive a file
     *
     * @param dirname     The directory to place the file
     * @param mode        The transfer mode (ascii / binary), ascii not supported currently
     * @param filename    The file name to place in above directory
     * @param offset      This is for future use when we supported resume
     * @param inetAddress The source we are expecting the transfer from (security feature to not allow random connects)
     *
     * @return A TransferStatus object of how the transfer went
     *
     * @throws IOException If an issue is raised while we are receiving the file during transfer
     * @throws TransferDeniedException If the connection we have is not the expected source
     */
    public TransferStatus receiveFile(String dirname, char mode, String filename, long offset, String inetAddress)
            throws IOException, TransferDeniedException, FileExistsException, FileNotFoundException {
        _pathForUpload = dirname + separator + filename;
        try {
            _slave.getRoots().getFile(_pathForUpload);
            throw new FileExistsException("File " + dirname + separator + filename + " exists");
        } catch (FileNotFoundException ignored) {} // This is expected

        String root = _slave.getRoots().getARootFileDir(dirname).getPath();

        try {
            _out = new FileOutputStream(new File(root + separator + filename));

            if (_slave.getUploadChecksums()) {
                _checksum = new CRC32();
                _out = new CheckedOutputStream(_out, _checksum);
            }
            accept(_slave.getCipherSuites(), _slave.getSSLProtocols(), _slave.getBufferSize());

            if (isNotExpectedHostmask(inetAddress, _sock.getInetAddress())) {
                throw new TransferDeniedException("The IP that connected to the Socket was not the one that was expected.");
            }

            _in = _sock.getInputStream();
            synchronized (this) {
                _direction = Transfer.TRANSFER_RECEIVING_UPLOAD;
            }

            logger.info("UL: {}/{}{}", dirname, filename, getNegotiatedSSLString());
            transfer(null);
            _slave.sendResponse(new AsyncResponseDiskStatus(_slave.getDiskStatus()));
            return getTransferStatus();
        } finally {
            if (_sock != null) {
                try {
                    _sock.close();
                } catch (IOException ignored) {}
            }
            if (_out != null) {
                try {
                    _out.close();
                } catch (IOException ignored) {}
            }
            if (_in != null) {
                try {
                    _in.close();
                } catch (IOException ignored) {}
            }
        }
    }

    /**
     * Send a file
     *
     * @param path        The file we need to send
     * @param mode        The transfer mode (ascii / binary), ascii not supported currently
     * @param offset      This is for future use when we supported resume
     * @param inetAddress The destination we are sending the transfer to
     *
     * @return A TransferStatus object of how the transfer went
     *
     * @throws IOException If an issue is raised while we are receiving the file during transfer
     * @throws TransferDeniedException If the connection we have is not the expected source
     */
    public TransferStatus sendFile(String path, char mode, long offset, String inetAddress)
            throws IOException, TransferDeniedException, FileNotFoundException {
        try {

            _in = new FileInputStream(new PhysicalFile(_slave.getRoots().getFile(path)));

            if (_slave.getDownloadChecksums()) {
                _checksum = new CRC32();
                _in = new CheckedInputStream(_in, _checksum);
            }

            // TODO: Check if we actually support this and works as expected
            _in.skip(offset);

            accept(_slave.getCipherSuites(), _slave.getSSLProtocols(), _slave.getBufferSize());

            if (isNotExpectedHostmask(inetAddress, _sock.getInetAddress())) {
                throw new TransferDeniedException("The IP that connected to the Socket was not the one that was expected.");
            }

            _out = _sock.getOutputStream();
            synchronized (this) {
                _direction = Transfer.TRANSFER_SENDING_DOWNLOAD;
            }

            logger.info("DL: {}{}", path, getNegotiatedSSLString());
            try {
                transfer(getUploadForPath(path));
            } catch (ObjectNotFoundException e) {
                transfer(null);
            }
            return getTransferStatus();
        } finally {
            if (_sock != null) {
                try {
                    _sock.close();
                } catch (IOException ignored) {}
            }
            if (_out != null) {
                try {
                    _out.close();
                } catch (IOException ignored) {}
            }
            if (_in != null) {
                try {
                    _in.close();
                } catch (IOException ignored) {}
            }
        }
    }

    /**
     * Shows the SSL protocol and Cipher
     *
     * @return A string representation of the protocol and cipher
     */
    private String getNegotiatedSSLString() {
        String finalSSLNegotiation = "";
        if (_sock instanceof SSLSocket) {
            String protocol = ((SSLSocket) _sock).getSession().getProtocol();
            String cipher = ((SSLSocket) _sock).getSession().getCipherSuite();
            finalSSLNegotiation = " SSL: Protocol=" + protocol + " Cipher=" + cipher;
        }
        return finalSSLNegotiation;
    }

    private void accept(String[] cipherSuites, String[] sslProtocols, int bufferSize) throws IOException, SocketException {
        _sock = _conn.connect(cipherSuites, sslProtocols, bufferSize);
        _sock.setTcpNoDelay(true);
        _sock.setSoTimeout(100); // 0.1 seconds

        _conn = null;
    }

    /**
     * Call sock.connect() and start sending.
     *
     * Read about buffers here:
     * http://groups.google.com/groups?hl=sv&lr=&ie=UTF-8&oe=UTF-8&threadm=9eomqe%24rtr%241%40to-gate.itd.utech.de&rnum=22&prev=/groups%3Fq%3Dtcp%2Bgood%2Bbuffer%2Bsize%26start%3D20%26hl%3Dsv%26lr%3D%26ie%3DUTF-8%26oe%3DUTF-8%26selm%3D9eomqe%2524rtr%25241%2540to-gate.itd.utech.de%26rnum%3D22
     *
     * Quote: Short answer is: if memory is not limited make your buffer big;
     * TCP will flow control itself and only use what it needs.
     *
     * Longer answer: for optimal throughput (assuming TCP is not flow
     * controlling itself for other reasons) you want your buffer size to at
     * least be
     *
     * channel bandwidth * channel round-trip-delay.
     *
     * So on a long slow link, if you can get 100K bps throughput, but your
     * delay -s 8 seconds, you want:
     *
     * 100Kbps * / bits-per-byte * 8 seconds = 100 Kbytes
     *
     * That way TCP can keep transmitting data for 8 seconds before it would
     * have to stop and wait for an ack (to clear space in the buffer for new
     * data so it can put new TX data in there and on the line). (The idea is to
     * get the ack before you have to stop transmitting.)
     *
     * DrFTPD allows for a buffersize between 0 and 262144 (256K)
     *
     * @param associatedUpload If we are doing an upload this needs to be set otherwise we expect 'null'
     *
     * @throws IOException If anything happens during the transfer on the socket
     */
    private void transfer(Transfer associatedUpload) throws IOException, TransferFailedException {
        try {
            // Register when we started this transfer
            _started = System.currentTimeMillis();

            // TODO: Support for mode ASCII?
            if (_mode == 'A') {
                _out = new AddAsciiOutputStream(_out);
            }

            // Set the buffer size (uses ram) to whatever is configured with a minimum (hardcoded) as 32K
            byte[] buff = new byte[Math.max(_slave.getBufferSize(), 32768)];
            logger.debug("Buffer has been initialized with a size of [{}]", buff.length);

            int count;
            long currentTime = System.currentTimeMillis();

            // The ThrottledInputStream handles the max speed (traffic manager)
            _int = new ThrottledInputStream(_in, getMaxSpeed());

            // Minimum speed check
            boolean first = true;
            long lastCheck = 0;

            long _showTransferProgress = 0L;

            try {
                while (true) {
                    if ((getTransferred() - _showTransferProgress) >= _transferProgressAnnounce) {
                        logger.debug(_int);
                        _showTransferProgress = getTransferred();
                    }
                    if (_abortReason != null) {
                        throw new TransferFailedException("Transfer was aborted - " + _abortReason, getTransferStatus());
                    }
                    try {
                        count = _int.read(buff);
                    } catch(SocketTimeoutException e) {
                        logger.debug("SocketTimeoutException happened, this is expected to make other logic work");
                        count = 0;
                    }
                    if (count == -1) {
                        if (associatedUpload == null) {
                            logger.debug("Done transferring as associatedUpload == null");
                            break; // done transferring
                        }
                        if (associatedUpload.getTransferStatus().isFinished()) {
                            logger.debug("Done transferring as associatedUpload states it is finished");
                            break; // done transferring
                        }

                        continue; // waiting for upload to catch up
                    }

                    // Do some keepalive stuff
                    if ((System.currentTimeMillis() - currentTime) >= 1000) {
                        if (getTransferStatus().isFinished()) {
                            throw new TransferFailedException("Transfer idle timeout reached", getTransferStatus());
                        }
                        // Report progress to the master
                        _slave.sendResponse(new AsyncResponseTransferStatus(getTransferStatus()));
                        currentTime = System.currentTimeMillis();
                    }

                    // Min Speed Check
                    if (_minSpeed > 0) {
                        if (lastCheck == 0) {
                            lastCheck = System.currentTimeMillis();
                        }
                        long delay = System.currentTimeMillis() - lastCheck;

                        // Check every 0.2 seconds, but take into account tcp slow start so first check is 0.5 seconds
                        if (first ? delay >= 500 : delay >= 200) {
                            logger.debug("In minspeed check, delay: {}, current speed: {}, min speed is set to: {}", delay, getTransferSpeed(), _minSpeed);
                            first = false;
                            if (getTransferSpeed() < _minSpeed) {
                                throw new TransferSlowException("Transfer was aborted - '" + getTransferSpeed() + "' is < '" + _minSpeed + "'", getTransferStatus());
                            }
                            // Reset the last Check
                            lastCheck = System.currentTimeMillis();
                        }
                    }

                    // Only do these actions if we have read data
                    if (count > 0) {
                        _transferred += count;
                        _out.write(buff, 0, count);
                    }
                }

                _out.flush();
            } catch (SocketException e) {
                logger.warn("Caught SocketException, forwarding as TransferFailedException", e);
                logger.debug(_int);
                throw new TransferFailedException(e, getTransferStatus());
            } catch (IOException e) {
                logger.warn("Catched IOException, forwarding as TransferFailedException", e);
                logger.debug(_int);
                if (e instanceof TransferFailedException) {
                    throw e;
                }
                throw new TransferFailedException(e, getTransferStatus());
            }
        } finally {
            _finished = System.currentTimeMillis();
            _slave.removeTransfer(this); // transfers are added in setting up
            logger.debug("Transfer finalized (stats: {} bytes in {} seconds)", getTransferred(), getElapsed());
        }
    }

    private boolean isNotExpectedHostmask(String maskString, InetAddress connectedAddress) {
        HostMask mask = new HostMask(maskString);

        try {
            return !mask.matchesHost(connectedAddress);
        } catch (PatternSyntaxException e) {
            logger.debug("maskString {} raised a pattern syntax exception", maskString);
            // if it's not well formed, no need to worry about it, just ignore it.
            return false;
        }
    }
}
