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
package org.drftpd.commands.nuke;

import org.drftpd.commandmanager.CommandRequest;
import org.drftpd.commandmanager.CommandRequestInterface;
import org.drftpd.commandmanager.CommandResponse;
import org.drftpd.commandmanager.PreHookInterface;
import org.drftpd.commandmanager.StandardCommandManager;
import org.drftpd.commands.nuke.common.NukeData;
import org.drftpd.exceptions.ObjectNotFoundException;
import org.drftpd.vfs.DirectoryHandle;
import org.drftpd.vfs.VirtualFileSystem;

/**
 * Nuke PreHook. 
 * @author fr0w
 * @version $Id$
 */
public class NukePreHook implements PreHookInterface {
	public void initialize(StandardCommandManager cManager) {
	}
	
	public CommandRequestInterface doNukeCheck(CommandRequest request) {
		String targetDir = VirtualFileSystem.fixPath(request.getArgument());
		DirectoryHandle currentDir = request.getCurrentDirectory();
		
		String fullPath = "";
		if (currentDir.getPath().equals(VirtualFileSystem.separator)) // no need append the '/'
			fullPath = VirtualFileSystem.separator + targetDir;
		else
			fullPath = currentDir.getPath() + VirtualFileSystem.separator + targetDir;

		try {
			NukeData nd = NukeBeans.getNukeBeans().get(fullPath);
			// nuke found.
			request.setAllowed(false);
			request.setDeniedResponse(new CommandResponse(530, "Access denied - " +
					"Directory already nuked for '"+ nd.getReason() + "'"));
		} catch (ObjectNotFoundException e) {
			// do nothing, allowed.
		}
		return request;		
	}
}
