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
package org.drftpd.jobs.master;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.drftpd.common.exceptions.RemoteIOException;
import org.drftpd.common.exceptions.SSLUnavailableException;
import org.drftpd.common.exceptions.TransferFailedException;
import org.drftpd.common.slave.ConnectInfo;
import org.drftpd.master.exceptions.SlaveUnavailableException;
import org.drftpd.master.network.RemoteTransfer;
import org.drftpd.master.protocol.AbstractBasicIssuer;
import org.drftpd.master.slavemanagement.RemoteSlave;
import org.drftpd.master.slavemanagement.SlaveManager;
import org.drftpd.master.vfs.FileHandle;

import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * @author mog
 * @author zubov
 * @version $Id$
 */
public class SlaveTransfer {

    private static final Logger logger = LogManager.getLogger(SlaveTransfer.class);

    private final RemoteSlave _destSlave;

    private final FileHandle _file;

    private final RemoteSlave _srcSlave;

    private RemoteTransfer _destTransfer = null;

    private RemoteTransfer _srcTransfer = null;

    private final boolean _secureTransfer;

    /**
     * Slave to Slave Transfers
     */
    public SlaveTransfer(FileHandle file, RemoteSlave sourceSlave, RemoteSlave destSlave, boolean secureTransfer) {
        _file = file;
        _srcSlave = sourceSlave;
        _destSlave = destSlave;
        _secureTransfer = secureTransfer;
        logger.debug("Initialized for {} from {} to {} [Secure:{}]", _file.getName(), _srcSlave.getName(), _destSlave.getName(), secureTransfer);
    }

    long getTransfered() {
        if (_srcTransfer == null || _destTransfer == null)
            return 0;

        return (_srcTransfer.getTransfered() + _destTransfer.getTransfered()) / 2;
    }

    long getXferSpeed() {
        if (_srcTransfer == null || _destTransfer == null)
            return 0;

        return (_srcTransfer.getXferSpeed() + _destTransfer.getXferSpeed()) / 2;
    }

    public void abort(String reason) {
        logger.debug("Abort received for {} from {} to {}", _file.getName(), _srcSlave.getName(), _destSlave.getName());
        if (_srcTransfer != null) {
            _srcTransfer.abort(reason);
        }
        if (_destTransfer != null) {
            _destTransfer.abort(reason);
        }
    }

    /**
     * Returns the crc checksum of the destination transfer If CRC is disabled
     * on the destination slave, checksum = 0
     */
    protected boolean transfer() throws SlaveException {
        // can do encrypted slave2slave transfers by modifying the
        // first argument in issueListenToSlave() and the third option
        // in issueConnectToSlave(), maybe do an option later, is this wanted?

        AbstractBasicIssuer basicIssuer = SlaveManager.getBasicIssuer();

        // Setup destination Slave
        try {
            String destIndex = basicIssuer.issueListenToSlave(_destSlave, _secureTransfer, false);
            ConnectInfo ci = _destSlave.fetchTransferResponseFromIndex(destIndex);
            _destTransfer = _destSlave.getTransfer(ci.getTransferIndex());
        } catch (SlaveUnavailableException e) {
            logger.debug("SlaveUnavailableException received, throwing DestinationSlaveException");
            throw new DestinationSlaveException(e);
        } catch (RemoteIOException e) {
            logger.debug("RemoteIOException received, throwing DestinationSlaveException");
            throw new DestinationSlaveException(e);
        } catch (SSLUnavailableException e) {
            logger.debug("SSLUnavailableException received, throwing DestinationSlaveException");
            throw new DestinationSlaveException(e);
        }

        // Setup source Slave
        try {
            String srcIndex = basicIssuer.issueConnectToSlave(_srcSlave, _destTransfer
                    .getAddress().getAddress().getHostAddress(), _destTransfer
                    .getLocalPort(), _secureTransfer, true);
            ConnectInfo ci = _srcSlave.fetchTransferResponseFromIndex(srcIndex);
            _srcTransfer = _srcSlave.getTransfer(ci.getTransferIndex());
        } catch (SlaveUnavailableException e) {
            logger.debug("SlaveUnavailableException received, throwing SourceSlaveException");
            throw new SourceSlaveException(e);
        } catch (RemoteIOException e) {
            logger.debug("RemoteIOException received, throwing SourceSlaveException");
            throw new SourceSlaveException(e);
        } catch (SSLUnavailableException e) {
            logger.debug("SSLUnavailableException received, throwing SourceSlaveException");
            throw new SourceSlaveException(e);
        }

        // Start received on the destination
        try {
            _destTransfer.receiveFile(_file.getPath(), 'I', 0, "*@*", 0L, 0L);
        } catch (IOException e1) {
            logger.debug("IOException received, throwing DestinationSlaveException");
            throw new DestinationSlaveException(e1);
        } catch (SlaveUnavailableException e1) {
            logger.debug("SlaveUnavailableException received, throwing DestinationSlaveException");
            throw new DestinationSlaveException(e1);
        }

        // Start sending on the source
        try {
            _srcTransfer.sendFile(_file.getPath(), 'I', 0, "*@*", 0L, 0L);
        } catch (IOException e2) {
            logger.debug("IOException received, throwing SourceSlaveException");
            throw new SourceSlaveException(e2);
        } catch (SlaveUnavailableException e2) {
            logger.debug("SlaveUnavailableException received, throwing SourceSlaveException");
            throw new SourceSlaveException(e2);
        }

        boolean srcIsDone = false;
        boolean destIsDone = false;

        while (!(srcIsDone && destIsDone)) {
            try {
                if (_srcTransfer.getTransferStatus().isFinished()) {
                    srcIsDone = true;
                }
            } catch (TransferFailedException e7) {
                logger.debug("source slave {} had an Transfer failure", _srcSlave.getName());
                _destTransfer.abort("srcSlave had an error");
                throw new SourceSlaveException(e7.getCause());
            }

            try {
                if (_destTransfer.getTransferStatus().isFinished()) {
                    destIsDone = true;
                }
            } catch (TransferFailedException e6) {
                logger.debug("destination slave {} had an Transfer failure", _destSlave.getName());
                _srcTransfer.abort("destSlave had an error");
                throw new DestinationSlaveException(e6.getCause());
            }

            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) {
            }
        }
        // may as well set the checksum, we know this one is right
        if (_srcTransfer.getChecksum() != 0) {
            try {
                _file.setCheckSum(_srcTransfer.getChecksum());
            } catch (FileNotFoundException e) {
                logger.warn("Unable to set checksum in VFS as the file no longer exists on the source");
                // file was moved during transfer can't set the checksum
            }
        }

        return _srcTransfer.getChecksum() == _destTransfer.getChecksum()
                || _destTransfer.getChecksum() == 0
                || _srcTransfer.getChecksum() == 0;
    }

    /**
     * @return Returns the _destSlave.
     */
    public RemoteSlave getDestinationSlave() {
        return _destSlave;
    }

    /**
     * @return Returns the _srcSlave.
     */
    public RemoteSlave getSourceSlave() {
        return _srcSlave;
    }
}
