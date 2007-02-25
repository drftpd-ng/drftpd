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
import org.drftpd.commandmanager.CommandResponse;
import org.drftpd.commandmanager.PreHookInterface;
import org.drftpd.commands.TransferStatistics;
import org.drftpd.master.config.FtpConfig;
import org.drftpd.permissions.Permission;
import org.drftpd.usermanager.NoSuchUserException;
import org.drftpd.usermanager.UserFileException;
/**
 * @author zubov
 * @version $Id$
 */
public class PermissionPreHook implements PreHookInterface {
	
	private static final Logger logger = Logger.getLogger(PermissionPreHook.class);
	
	private static final String PERMSTAG = "permsTag";
	
	private String _key = null;
	
	public void initialize() {
		
	}
	
	public CommandRequest doPermissionCheck(CommandRequest request) {
		try {
			if (_key == null) {
				logger.warn("Permission key " + PERMSTAG + " is not configured for command, must allow command");
			} else if (FtpConfig.getFtpConfig().checkPermission(_key, request.getUserObject())) {
				request.setDeniedResponse(new CommandResponse(500, "You do not have the proper permissions to " + _key));
			} else {
				// it worked, you passed the test
				return request;
			}
		} catch (NoSuchUserException e) {
			request.setDeniedResponse(new CommandResponse(500, "You do not exist"));
		} catch (UserFileException e) {
			request.setDeniedResponse(new CommandResponse(500, "Your userfile is corrupted"));
		}
		request.setAllowed(false);
		return request;
	}

	public void addExtensionParameter(String id, String key) {
		// only one I care about is permsTag
		if (id.equalsIgnoreCase(PERMSTAG)) {
			logger.debug("Setting " + PERMSTAG + " key for " + key);
			_key = key;
		}
	}

	public String getExtensionParameter(String key) {
		// we don't need this
		return null;
	}
}
