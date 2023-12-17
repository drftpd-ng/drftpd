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
package org.drftpd.master.network;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.drftpd.common.exceptions.RemoteIOException;
import org.drftpd.common.exceptions.TransferFailedException;
import org.drftpd.common.slave.ConnectInfo;
import org.drftpd.common.slave.TransferIndex;
import org.drftpd.common.slave.TransferStatus;
import org.drftpd.master.exceptions.SlaveUnavailableException;
import org.drftpd.master.slavemanagement.RemoteSlave;
import org.drftpd.master.slavemanagement.SlaveManager;
import org.drftpd.master.vfs.TransferPointer;
import org.drftpd.slave.network.Transfer;

import java.io.IOException;
import java.net.InetSocketAddress;

/**
 * @author zubov
 * @author mog
 * @version $Id$
 * All calls to this class should be made through the TransferState object
 */
public class RemoteTransfer {

    private static final Logger logger = LogManager.getLogger(RemoteTransfer.class);

    private final InetSocketAddress _address;

    private final TransferIndex _transferIndex;

    private final RemoteSlave _rslave;

    private TransferStatus _status;

    private char _transferDirection = Transfer.TRANSFER_UNKNOWN;

    private String _path;

    private TransferPointer _pointer;

    private static final Object TRANSFER_LOCK = new Object();

    public RemoteTransfer(ConnectInfo ci, RemoteSlave rslave)
            throws SlaveUnavailableException {
        _transferIndex = ci.getTransferIndex();
        _address = new InetSocketAddress(rslave.getPASVIP(), ci.getPort());
        _rslave = rslave;
        _status = ci.getTransferStatus();
    }

    public void updateTransferStatus(TransferStatus ts) {
        _status = ts;

        if (_status.isFinished()) {
            logger.debug("updateTransferStatus() - [{}] is finished", toString());
            synchronized (TRANSFER_LOCK) {
                if (_pointer != null && _transferDirection != Transfer.TRANSFER_UNKNOWN) {
                    _pointer.unlinkPointer(this);
                }
                _pointer = null;
            }

            // Send out an event to inform people a transfer was complete
            _rslave.getGlobalContext().getEventService().publishAsync(new TransferCompleteEvent(_path, _status));
        }
    }

    public char getTransferDirection() {
        return _transferDirection;
    }

    public long getChecksum() {
        return _status.getChecksum();
    }

    /**
     * Returns how long this transfer has been running in milliseconds.
     */
    public long getElapsed() {
        return _status.getElapsed();
    }

    /**
     * For a passive connection, returns the port the serversocket is listening
     * on.
     */
    public int getLocalPort() {
        return _address.getPort();
    }

    public TransferStatus getTransferStatus() throws TransferFailedException {
        if (!_rslave.isOnline()) {
            throw new TransferFailedException("Slave is offline", _status);
        }

        if (_status.threwException()) {
            throw new TransferFailedException((Exception) _status.getThrowable(), _status);
        }

        return _status;
    }

    /**
     * Returns the number of bytes transfered.
     */
    public long getTransfered() {
        return _status.getTransfered();
    }

    /**
     * Returns how fast the transfer is going in bytes per second.
     */
    public long getXferSpeed() {
        return _status.getXferSpeed();
    }

    public String getPathNull() {
        return _path;
    }

    public TransferIndex getTransferIndex() {
        return _transferIndex;
    }

    public InetSocketAddress getAddress() {
        return _address;
    }

    public void abort(String reason) {
        if (_status.isFinished()) {
            // no need to abort a transfer that isn't transferring
            return;
        }
        logger.warn("Abort() called for [{}] with reason: {}", toString(), reason);

        // We need to catch the SlaveUnavailableException if thrown but need to unlink before we update _status
        Throwable t = null;

        try {
            SlaveManager.getBasicIssuer().issueAbortToSlave(_rslave, getTransferIndex(), reason);
        } catch (SlaveUnavailableException e) {
            t = e;
        }

        synchronized (TRANSFER_LOCK) {
            if (_pointer != null && _transferDirection != Transfer.TRANSFER_UNKNOWN) {
                _pointer.unlinkPointer(this);
            }
            _pointer = null;
        }

        // We aborted the upload so reset the TransferStatus
        if (t != null) {
            _status = new TransferStatus(getTransferIndex(), t);
        }
    }

    public void receiveFile(String path, char type, long position, String inetAddress, long minSpeed, long maxSpeed)
            throws IOException, SlaveUnavailableException {
        _path = path;

        String index = SlaveManager.getBasicIssuer().issueReceiveToSlave(
                _rslave, path, type, position, inetAddress, getTransferIndex(), minSpeed, maxSpeed);

        _transferDirection = Transfer.TRANSFER_RECEIVING_UPLOAD;
        try {
            _rslave.fetchResponse(index);
        } catch (RemoteIOException e) {
            throw e.getCause();
        }
        synchronized (TRANSFER_LOCK) {
            _pointer = new TransferPointer(_path, this);
        }
    }

    public void sendFile(String path, char type, long position, String inetAddress, long minSpeed, long maxSpeed)
            throws IOException, SlaveUnavailableException {
        _path = path;
        String index = SlaveManager.getBasicIssuer().issueSendToSlave(
                _rslave, path, type, position, inetAddress, getTransferIndex(), minSpeed, maxSpeed);
        _transferDirection = Transfer.TRANSFER_SENDING_DOWNLOAD;
        try {
            _rslave.fetchResponse(index);
        } catch (RemoteIOException e) {
            throw e.getCause();
        }
        synchronized (TRANSFER_LOCK) {
            _pointer = new TransferPointer(_path, this);
        }
    }

    public String toString() {
        try {
            return getClass().getName() + "[file=" + _path + ",status="
                    + getTransferStatus() + "]";
        } catch (TransferFailedException e) {
            return getClass().getName() + "[file=" + _path + ",status=failed]";
        }
    }
}
