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
package org.drftpd.master;

import net.sf.drftpd.SlaveUnavailableException;
import net.sf.drftpd.master.RemoteSlave;

import java.io.IOException;

import java.net.InetSocketAddress;

import org.drftpd.slave.ConnectInfo;
import org.drftpd.slave.RemoteIOException;
import org.drftpd.slave.Transfer;
import org.drftpd.slave.TransferFailedException;
import org.drftpd.slave.TransferIndex;
import org.drftpd.slave.TransferStatus;


/**
 * @author zubov
 * @author mog
 * @version $Id: RemoteTransfer.java,v 1.2 2004/11/09 21:49:58 zubov Exp $
 */
public class RemoteTransfer {
    private InetSocketAddress _address;
    private TransferIndex _transferIndex;
    private RemoteSlave _rslave;
    private TransferStatus _status;
    private char _state;

    public RemoteTransfer(ConnectInfo ci, RemoteSlave rslave) {
        _transferIndex = ci.getTransferIndex();
        _address = new InetSocketAddress(ci.getAddress(), ci.getPort());
        _rslave = rslave;
        _status = ci.getTransferStatus();
    }

    public void updateTransferStatus(TransferStatus ts) {
        _status = ts;
    }

    public char getState() {
        return _state;
    }

    public long getChecksum() {
        try {
            return getTransferStatus().getChecksum();
        } catch (IOException e) {
            return 0;
        } catch (SlaveUnavailableException e) {
            return 0;
        }
    }

    /**
     * Returns how long this transfer has been running in milliseconds.
     */
    public long getElapsed() {
        try {
            return getTransferStatus().getElapsed();
        } catch (IOException e) {
            return 0;
        } catch (SlaveUnavailableException e) {
            return 0;
        }
    }

    /**
     * For a passive connection, returns the port the serversocket is listening on.
     */
    public int getLocalPort() {
        return _address.getPort();
    }

    public TransferStatus getTransferStatus()
        throws TransferFailedException, SlaveUnavailableException {
        if (!_rslave.isOnline()) {
            throw new SlaveUnavailableException("Slave is offline");
        }

        if (_status.threwException()) {
            throw new TransferFailedException((IOException) _status.getThrowable(),
                _status);
        }

        return _status;
    }

    /**
     * Returns the number of bytes transfered.
     */
    public long getTransfered() {
        try {
            return getTransferStatus().getTransfered();
        } catch (TransferFailedException e) {
            return 0;
        } catch (SlaveUnavailableException e) {
            return 0;
        }
    }

    /**
     * Returns how fast the transfer is going in bytes per second.
     */
    public int getXferSpeed() {
        try {
            return getTransferStatus().getXferSpeed();
        } catch (TransferFailedException e) {
            return 0;
        } catch (SlaveUnavailableException e) {
            return 0;
        }
    }

    public TransferIndex getTransferIndex() {
        return _transferIndex;
    }

    public InetSocketAddress getAddress() {
        return _address;
    }

    public void abort() throws SlaveUnavailableException, IOException {
        _rslave.issueAbortToSlave(getTransferIndex());
    }

    public void receiveFile(String path, char type, long position)
        throws IOException, SlaveUnavailableException {
        String index = _rslave.issueReceiveToSlave(path, type, position,
                getTransferIndex());
        _state = Transfer.TRANSFER_RECEIVING_UPLOAD;
        try {
            _rslave.fetchResponse(index);
        } catch (RemoteIOException e) {
            throw (IOException) e.getCause();
        }
    }

    public void sendFile(String path, char type, long position)
        throws IOException, SlaveUnavailableException {
        String index = _rslave.issueSendToSlave(path, type, position,
                getTransferIndex());
        _state = Transfer.TRANSFER_SENDING_DOWNLOAD;
        try {
            _rslave.fetchResponse(index);
        } catch (RemoteIOException e) {
            throw (IOException) e.getCause();
        }
    }
}
