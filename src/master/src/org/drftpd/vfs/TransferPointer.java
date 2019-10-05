/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 *
 * DrFTPD is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * DrFTPD; if not, write to the Free Software Foundation, Inc., 59 Temple Place,
 * Suite 330, Boston, MA 02111-1307 USA
 */
package org.drftpd.vfs;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.drftpd.master.RemoteTransfer;
import org.drftpd.slave.Transfer;

import java.io.FileNotFoundException;

/**
 * This class's only purpose is to create a hard Reference to the VirtualFileSystemFile object that is being transferred
 * Each RemoteTransfer class should reference a TransferPointer to their appropriate file
 * @author zubov
 * @version $Id$
 */
public class TransferPointer {
	private VirtualFileSystemFile _vfsObject = null;
	
	protected static final Logger logger = LogManager.getLogger(InodeHandle.class);

	public TransferPointer(String path, RemoteTransfer transfer) throws FileNotFoundException {
		VirtualFileSystemInode vfsInode = VirtualFileSystem.getVirtualFileSystem().getInodeByPath(path);
		if (vfsInode.isFile()) {
			_vfsObject = (VirtualFileSystemFile) vfsInode;
			if (transfer.getTransferDirection() == Transfer.TRANSFER_RECEIVING_UPLOAD) {
				_vfsObject.addUpload(transfer);
			} else if (transfer.getTransferDirection() == Transfer.TRANSFER_SENDING_DOWNLOAD) {
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
			_vfsObject.removeUpload(transfer);
		} else if (transfer.getTransferDirection() == Transfer.TRANSFER_SENDING_DOWNLOAD) {
			_vfsObject.removeDownload(transfer);
		} else {
			throw new IllegalArgumentException("Transfer has to have a direction");
		}
		_vfsObject = null;
	}
}
