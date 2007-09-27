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
package org.drftpd.commands.prehook.permissions;

import java.io.FileNotFoundException;

import org.apache.log4j.Logger;
import org.drftpd.commandmanager.CommandRequest;
import org.drftpd.commandmanager.CommandRequestInterface;
import org.drftpd.commandmanager.CommandResponse;
import org.drftpd.commandmanager.PreHookInterface;
import org.drftpd.commandmanager.StandardCommandManager;
import org.drftpd.vfs.FileHandle;
import org.drftpd.vfs.InodeHandle;
import org.drftpd.vfs.LinkHandle;
import org.drftpd.vfs.ObjectNotValidException;

/**
 * @author zubov
 * @version $Id$
 * This class will deny the download of a file that is currently being transferred
 */
public class DenyDownload implements PreHookInterface {

	public void initialize(StandardCommandManager cManager) {

	}

	public CommandRequestInterface doPermissionCheck(CommandRequest request) {

		try {
			InodeHandle inode = request.getCurrentDirectory().getInodeHandle(
					request.getArgument());

			FileHandle file = null;
			if (inode.isLink()) {
				file = ((LinkHandle) inode).getTargetFile();
			} else if (inode.isDirectory()) {
				return request;
				// RETR will figure out they're requesting to download a
				// directory
			} else { // inode.isFile
				file = (FileHandle) inode;
			}
			if (file.isUploading()) {
				request.setDeniedResponse(new CommandResponse(400,
						"The file is currently being uploaded."));
				request.setAllowed(false);
			}
			return request;
		} catch (FileNotFoundException e) {
			// file to transfer isn't found
			// we'll ignore this happened so RETR can give them the standard
			// response
			return request;
		} catch (ObjectNotValidException e) {
			// they're trying to download a directory :)
			return request;
		}
	}

}
