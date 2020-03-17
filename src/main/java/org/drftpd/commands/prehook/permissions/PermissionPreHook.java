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

import org.drftpd.common.CommandHook;
import org.drftpd.common.HookType;
import org.drftpd.master.commandmanager.CommandRequestInterface;
import org.drftpd.master.permissions.Permission;
import org.drftpd.master.usermanager.NoSuchUserException;
import org.drftpd.master.usermanager.User;
import org.drftpd.master.usermanager.UserFileException;
import org.drftpd.plugins.commandmanager.CommandRequest;
import org.drftpd.plugins.commandmanager.CommandResponse;

/**
 * @author zubov
 * @version $Id$
 */
public class PermissionPreHook  {

	@CommandHook(commands = {"doSITE_REQUEST", "doSITE_REQFILLED", "doSITE_REQDELETE", "doSITE_REQUESTS",
			"doSITE_SECTIONS", "doSITE_SPEEDTEST", "doTEXT_OUTPUT", "doSITE_TV", "doSITE_CREATETV", "doSITE_REMOVETV",
	"doSITE_TVQUEUE", "doSITE_RESCAN", "doRETR", "doSITE_UNDUPE", "doSITE_FIXLINKS", "doSITE_UNMIRROR",
	"doSITE_INVITE", "doSITE_BLOWFISH", "doSITE_SETBLOWFISH", "doSITE_IRC", "doTOP", "doCUT", "doPASSED"},
			priority = 2, type = HookType.PRE)
	public CommandRequestInterface doPermissionCheck(CommandRequest request) {
		
		try {
			Permission perm = request.getPermission();
			if (perm == null) {
				request.setDeniedResponse(new CommandResponse(500, "Permissions are not configured for command " + request.getCommand()));
				request.setAllowed(false);
				return request;
			}
			User user = null;
			try {
				user = request.getUserObject();
			} catch (NoSuchUserException e) {
				request.setDeniedResponse(new CommandResponse(500, "You are not authenticated"));
			}
			if (perm.check(user)) {
				// it worked, you passed the test
				return request;
			}
		} catch (UserFileException e) {
			request.setDeniedResponse(new CommandResponse(500, "Your userfile is corrupted"));
		}
		request.setAllowed(false);
		return request;
	}
}
