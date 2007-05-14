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

import org.apache.log4j.Logger;
import org.drftpd.commandmanager.CommandRequest;
import org.drftpd.commandmanager.CommandRequestInterface;
import org.drftpd.commandmanager.CommandResponse;
import org.drftpd.commandmanager.PreHookInterface;
import org.drftpd.commandmanager.StandardCommandManager;
import org.drftpd.permissions.Permission;
import org.drftpd.usermanager.NoSuchUserException;
import org.drftpd.usermanager.UserFileException;
/**
 * @author zubov
 * @version $Id$
 */
public class PermissionPreHook implements PreHookInterface {
	
	private static final Logger logger = Logger.getLogger(PermissionPreHook.class);
	
	public void initialize(StandardCommandManager cManager) {
		
	}
	
	public CommandRequestInterface doPermissionCheck(CommandRequest request) {
		
		try {
			Permission perm = request.getPermission();
			if (perm == null) {
				logger.warn("Permissions are not configured for command " + request.getCommand());
			} else if (perm.check(request.getUserObject())) {
				// it worked, you passed the test
				return request;
			}
			request.setDeniedResponse(new CommandResponse(500, "You do not have the proper permissions for command " + request.getCommand()));
		} catch (NoSuchUserException e) {
			request.setDeniedResponse(new CommandResponse(500, "You do not exist"));
		} catch (UserFileException e) {
			request.setDeniedResponse(new CommandResponse(500, "Your userfile is corrupted"));
		}
		request.setAllowed(false);
		return request;
	}
	
}
