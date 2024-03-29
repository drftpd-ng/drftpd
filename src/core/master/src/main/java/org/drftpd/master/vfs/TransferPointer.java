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
package org.drftpd.master.vfs;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.drftpd.master.network.RemoteTransfer;
import org.drftpd.slave.network.Transfer;

import java.io.FileNotFoundException;

/**
 * This class's only purpose is to create a hard Reference to the VirtualFileSystemFile object that is being transferred
 * Each RemoteTransfer class should reference a TransferPointer to their appropriate file
 *
 * @author zubov
 * @version $Id$
 */
public class TransferPointer {
    protected static final Logger logger = LogManager.getLogger(TransferPointer.class);
    private VirtualFileSystemFile _vfsObject = null;

    public TransferPointer(String path, RemoteTransfer transfer) throws FileNotFoundException {
        VirtualFileSystemInode vfsInode = VirtualFileSystem.getVirtualFileSystem().getInodeByPath(path);
        if (vfsInode.isFile()) {
            _vfsObject = (VirtualFileSystemFile) vfsInode;
            if (transfer.getTransferDirection() == Transfer.TRANSFER_RECEIVING_UPLOAD) {
                logger.debug("TransferPointer() - Adding upload for {}", transfer);
                _vfsObject.addUpload(transfer);
            } else if (transfer.getTransferDirection() == Transfer.TRANSFER_SENDING_DOWNLOAD) {
                logger.debug("TransferPointer() - Adding download for {}", transfer);
                _vfsObject.addDownload(transfer);
            } else {
                throw new IllegalArgumentException("Transfer has to have a direction");
            }
        } else {
            logger.error("This is a bug, report me! -- inconsistent file system", new ObjectNotValidException(path));
        }
    }

    public void unlinkPointer(RemoteTransfer transfer) {
        if (transfer.getTransferDirection() == Transfer.TRANSFER_RECEIVING_UPLOAD) {
            logger.debug("unlinkPointer() - Removing upload for {}", transfer);
            _vfsObject.removeUpload(transfer);
        } else if (transfer.getTransferDirection() == Transfer.TRANSFER_SENDING_DOWNLOAD) {
            logger.debug("unlinkPointer() - Removing download for {}", transfer);
            _vfsObject.removeDownload(transfer);
        } else {
            throw new IllegalArgumentException("Transfer has to have a direction");
        }
        _vfsObject = null;
    }
}
