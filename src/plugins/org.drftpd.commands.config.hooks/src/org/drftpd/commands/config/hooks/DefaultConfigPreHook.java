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
package org.drftpd.commands.config.hooks;

import java.util.Iterator;
import java.util.List;

import org.apache.log4j.Logger;
import org.drftpd.GlobalContext;
import org.drftpd.commandmanager.CommandRequest;
import org.drftpd.commandmanager.CommandRequestInterface;
import org.drftpd.commandmanager.PreHookInterface;
import org.drftpd.commandmanager.StandardCommandManager;
import org.drftpd.commands.usermanagement.UserManagementHandler;
import org.drftpd.master.BaseFtpConnection;
import org.drftpd.master.ConnectionManager;
import org.drftpd.master.config.ConfigInterface;
import org.drftpd.vfs.perms.VFSPermissions;

/**
 * PreHooks that implements some of the functionalities "required" by the directives in perms.conf
 * @author fr0w
 * @version $Id$
 */
public class DefaultConfigPreHook implements PreHookInterface {	

	protected static final Logger logger = Logger.getLogger(DefaultConfigPreHook.class);
	
	public void initialize(StandardCommandManager manager) {
	}

	public CommandRequestInterface hideInWhoHook(CommandRequest request) {
		List<BaseFtpConnection> conns = ConnectionManager.getConnectionManager().getConnections();
		ConfigInterface cfg = GlobalContext.getConfig();
		
		for (Iterator<BaseFtpConnection> iter = conns.iterator(); iter.hasNext();) {
			BaseFtpConnection conn = iter.next();
			if (cfg.checkPathPermission("hideinwho", conn.getUserNull(), conn.getCurrentDirectory())) {
				iter.remove();
			}
		}
		
		request.getSession().setObject(UserManagementHandler.CONNECTIONS, conns);
		
		return request;
	}
	
	public CommandRequestInterface checkDownloadPermsHook(CommandRequest request) {
		VFSPermissions vfsPerms = GlobalContext.getConfig().getVFSPermissions();
		
		if (!vfsPerms.checkPathPermission("download", request.getSession().getUserNull(request.getUser()), request.getCurrentDirectory().getNonExistentFileHandle(request.getArgument()))) {
			request.setAllowed(false);
			request.setDeniedResponse(StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED"));
		}
		
		return request;
	}
}
