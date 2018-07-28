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
package org.drftpd.permissions.fxp;

import org.drftpd.GlobalContext;
import org.drftpd.commandmanager.CommandRequest;
import org.drftpd.commandmanager.CommandRequestInterface;
import org.drftpd.commandmanager.PreHookInterface;
import org.drftpd.commandmanager.StandardCommandManager;
import org.drftpd.commands.dataconnection.DataConnectionHandler;
import org.drftpd.master.BaseFtpConnection;
import org.drftpd.master.config.ConfigInterface;
import org.drftpd.slave.Transfer;
import org.drftpd.vfs.DirectoryHandle;

import java.net.InetAddress;

/**
 * @author fr0w
 * @version $Id$
 */
public class FXPPermissionPreHook implements PreHookInterface {
	public void initialize(StandardCommandManager manager) {
	}

	public CommandRequestInterface checkDownloadFXPPerm(CommandRequest request) {		
		return checkFXPPerm(request, Transfer.TRANSFER_SENDING_DOWNLOAD);
	}

	public CommandRequestInterface checkUploadFXPPerm(CommandRequest request) {		
		return checkFXPPerm(request, Transfer.TRANSFER_RECEIVING_UPLOAD);
	}

	public CommandRequestInterface checkFXPPerm(CommandRequest request, char direction) {	
		DirectoryHandle fromDir = request.getCurrentDirectory();		
		ConfigInterface config = GlobalContext.getConfig();
		String directive = direction == Transfer.TRANSFER_RECEIVING_UPLOAD ? "deny_upfxp" : "deny_dnfxp";
		String mask = "*@*"; // default initialization

		if (config.checkPathPermission(directive, request.getSession().getUserNull(request.getUser()), fromDir)) {
			// denied to make fxp.
			// let's set the ip that is going to be sent to the slave.
			InetAddress inetAdd = request.getSession().getObject(BaseFtpConnection.ADDRESS, null);
			mask = "*@"+inetAdd.getHostAddress();						
		}
		
		request.getSession().setObject(DataConnectionHandler.INET_ADDRESS, mask);
		
		return request;
	}

}
