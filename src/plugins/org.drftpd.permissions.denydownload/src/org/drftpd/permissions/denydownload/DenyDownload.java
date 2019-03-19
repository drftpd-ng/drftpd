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
package org.drftpd.permissions.denydownload;

import org.drftpd.GlobalContext;
import org.drftpd.commandmanager.*;
import org.drftpd.master.config.ConfigInterface;
import org.drftpd.usermanager.User;
import org.drftpd.vfs.FileHandle;
import org.drftpd.vfs.InodeHandle;
import org.drftpd.vfs.LinkHandle;
import org.drftpd.vfs.ObjectNotValidException;

import java.io.FileNotFoundException;

/**
 * @author CyBeR
 * @version $Id: DenyDownload.java 1925 2009-06-15 21:46:05Z CyBeR $
 */

public class DenyDownload implements PreHookInterface {
	
	public void initialize(StandardCommandManager cManager) {

	}
	
	/*
	 * Checks to see if file is uploading first.
	 * Then checks to see if use has permission to Download file 
	 */
	public CommandRequestInterface doPermissionCheck(CommandRequest request) {
		try {
			User user = request.getSession().getUserNull(request.getUser());
			InodeHandle inode = request.getCurrentDirectory().getInodeHandle(request.getArgument(), user);
			
			FileHandle file = null;
			if (inode.isLink()) {
				file = ((LinkHandle) inode).getTargetFileUnchecked();
			} else if (inode.isDirectory()) {
				// Is a directory, let RETR handle it
				return request;
			} else { // Is a file
				file = (FileHandle) inode;
			}

			if (file.isUploading()) {
		        ConfigInterface config = GlobalContext.getConfig();
				if (config.checkPathPermission("denydownload", user, request.getCurrentDirectory())) {
					request.setDeniedResponse(new CommandResponse(400,"No Permission To Download A File Currently Being Uploaded."));
					request.setAllowed(false);
				}
			}
			
			return request;
		} catch (FileNotFoundException e) {
			// File not found, let RETR handle it
			return request;
		} catch (ObjectNotValidException e) {
			// Can't Download a Directory
			return request;
		}
	}

}
