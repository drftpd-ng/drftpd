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

import org.drftpd.common.CommandHook;
import org.drftpd.common.HookType;
import org.drftpd.master.GlobalContext;
import org.drftpd.master.commandmanager.*;
import org.drftpd.master.master.config.ConfigInterface;
import org.drftpd.master.usermanager.User;
import org.drftpd.master.vfs.FileHandle;
import org.drftpd.master.vfs.InodeHandle;
import org.drftpd.master.vfs.LinkHandle;
import org.drftpd.master.vfs.ObjectNotValidException;
import org.drftpd.commands.CommandRequest;
import org.drftpd.commands.CommandResponse;

import java.io.FileNotFoundException;

/**
 * @author CyBeR
 * @version $Id: DenyDownload.java 1925 2009-06-15 21:46:05Z CyBeR $
 */

public class DenyDownload {

	@CommandHook(commands = "doRETR", type = HookType.PRE)
	public CommandRequestInterface doPermissionCheck(CommandRequest request) {
		try {
			User user = request.getSession().getUserNull(request.getUser());
			InodeHandle inode = request.getCurrentDirectory().getInodeHandle(request.getArgument(), user);
			
			FileHandle file;
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
		} catch (FileNotFoundException | ObjectNotValidException e) {
			// File not found, let RETR handle it
			return request;
		} // Can't Download a Directory
	}
}
