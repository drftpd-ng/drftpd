/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * DrFTPD is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with DrFTPD; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package org.drftpd.slave;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.zip.CRC32;
import java.util.zip.CheckedInputStream;
import java.util.zip.CheckedOutputStream;

import net.sf.drftpd.FileExistsException;
import net.sf.drftpd.util.AddAsciiOutputStream;

import org.apache.log4j.Logger;
import org.drftpd.slave.async.AsyncResponseDiskStatus;
import org.drftpd.slave.async.AsyncResponseTransferStatus;

import se.mog.io.File;


/**
 * @author zubov
 * @version $Id$
 */
public class Transfer {
    private static final Logger logger = Logger.getLogger(Transfer.class);
    private boolean _abort = false;
    private CRC32 _checksum = null;
    private Connection _conn;
    private char _direction;
    private long _finished = 0;
    private InputStream _in;
    private char _mode = 'I';
    private OutputStream _out;
    private Slave _slave;
    private Socket _sock;
    private long _started = 0;
    private long _transfered = 0;
    private TransferIndex _transferIndex;
    private boolean _transferIsFinished = false;
	public static final char TRANSFER_RECEIVING_UPLOAD = 'R';
	public static final char TRANSFER_SENDING_DOWNLOAD = 'S';
	public static final char TRANSFER_THROUGHPUT = 'A';
	public static final char TRANSFER_UNKNOWN = 'U';

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
        _direction = Transfer.TRANSFER_UNKNOWN;
        _transferIndex = transferIndex;
    }

    public int hashCode() {
        return _transferIndex.hashCode();
    }

    public void abort() {
        _abort = true;
        _transferIsFinished = true;

        if (_conn != null) {
            _conn.abort();
        }

        if (_sock == null) {
            // already closed
            return;
        }

        try {
            _sock.close();
        } catch (IOException e) {
            logger.warn("abort() failed to close() the socket", e);
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

        throw new IllegalStateException(
            "getLocalPort() called on a non-passive transfer");
    }

    public char getState() {
        return _direction;
    }

    public TransferStatus getTransferStatus() {
        return new TransferStatus(getElapsed(), getTransfered(), getChecksum(),
            _transferIsFinished, getTransferIndex());
    }

    public long getTransfered() {
        return _transfered;
    }

    public TransferIndex getTransferIndex() {
        return _transferIndex;
    }

    public int getXferSpeed() {
        long elapsed = getElapsed();

        if (_transfered == 0) {
            return 0;
        }

        if (elapsed == 0) {
            return 0;
        }

        return (int) (_transfered / ((float) elapsed / (float) 1000));
    }

    public boolean isReceivingUploading() {
        return _direction == Transfer.TRANSFER_RECEIVING_UPLOAD;
    }

    public boolean isSendingUploading() {
        return _direction == Transfer.TRANSFER_SENDING_DOWNLOAD;
    }

    public TransferStatus receiveFile(String dirname, char mode,
        String filename, long offset) throws IOException {
        try {
            _slave.getRoots().getFile(dirname + File.separator + filename);
            throw new FileExistsException("File exists");
        } catch (FileNotFoundException ex) {
        }

        String root = _slave.getRoots().getARootFileDir(dirname).getPath();

        _out = new FileOutputStream(root + File.separator + filename);

        if (_slave.getUploadChecksums()) {
            _checksum = new CRC32();
            _out = new CheckedOutputStream(_out, _checksum);
        }

        System.out.println("UL:" + root + "/" +dirname + "/" + filename);

        try {
            TransferStatus status = transfer();
            _slave.sendResponse(new AsyncResponseDiskStatus(_slave.getDiskStatus()));
            return status;
        } catch (IOException e) {
            // TODO really delete on IOException ?
            //_slave.delete(root + File.separator + filename);
            throw e; // so the Master can still handle the exception
        }
    }

    public TransferStatus sendFile(String path, char type, long resumePosition)
        throws IOException {
        _in = new FileInputStream(_slave.getRoots().getFile(path));

        if (_slave.getDownloadChecksums()) {
            _checksum = new CRC32();
            _in = new CheckedInputStream(_in, _checksum);
        }

        _in.skip(resumePosition);

        System.out.println("DL:" + path);

        return transfer();
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
     */
    private TransferStatus transfer() throws IOException {
        _started = System.currentTimeMillis();
        _sock = _conn.connect();
        _conn = null;

        int bufsize = _slave.getBufferSize();

        if (_in == null) {
            if (bufsize > 0) {
                _sock.setReceiveBufferSize(bufsize);
            }

            _in = _sock.getInputStream();
            _direction = Transfer.TRANSFER_RECEIVING_UPLOAD;
        } else if (_out == null) {
            if (bufsize > 0) {
                _sock.setSendBufferSize(bufsize);
            }

            _out = _sock.getOutputStream();
            _direction = Transfer.TRANSFER_SENDING_DOWNLOAD;
        } else {
            throw new IllegalStateException("neither in or out was null");
        }

        if (_mode == 'A') {
            _out = new AddAsciiOutputStream(_out);
        }

        try {
            byte[] buff = new byte[Math.max(_slave.getBufferSize(), 65535)];
            int count;
            long currentTime = System.currentTimeMillis();

            try {
                while (((count = _in.read(buff)) != -1) && !_abort) {
                    if ((System.currentTimeMillis() - currentTime) >= 1000) {
                        _slave.sendResponse(new AsyncResponseTransferStatus(
                                getTransferStatus()));
                        currentTime = System.currentTimeMillis();
                    }

                    _transfered += count;
                    _out.write(buff, 0, count);
                }

                _out.flush();
            } catch (IOException e) {
                throw new TransferFailedException(e, getTransferStatus());
            }
        } finally {
            _finished = System.currentTimeMillis();
            _slave.removeTransfer(this);
            _transferIsFinished = true;
            _in.close();
            _out.close();
            _sock.close();

            _in = null;
            _out = null;

            //_sock = null;
        }

        if (_abort) {
            throw new TransferFailedException("Transfer was aborted",
                getTransferStatus());
        }

        return getTransferStatus();
    }
}
