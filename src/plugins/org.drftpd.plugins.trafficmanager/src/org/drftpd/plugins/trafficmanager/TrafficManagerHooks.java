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
package org.drftpd.plugins.trafficmanager;

import org.drftpd.commandmanager.CommandRequest;
import org.drftpd.commandmanager.CommandRequestInterface;
import org.drftpd.commandmanager.PreHookInterface;
import org.drftpd.commandmanager.StandardCommandManager;
import org.drftpd.commands.dataconnection.DataConnectionHandler;
import org.drftpd.usermanager.NoSuchUserException;
import org.drftpd.usermanager.UserFileException;

/**
 * @author CyBeR
 * @version $Id: TrafficManagerHooks.java 1925 2009-06-15 21:46:05Z CyBeR $
 */

public class TrafficManagerHooks implements PreHookInterface {

	public void initialize(StandardCommandManager cManager) {

	}
	
    /*
	 * Prehook method for TrafficManager
	 */
	public CommandRequestInterface doTrafficManagerSTOR(CommandRequest request) {
		try {
			for (TrafficType trafficType : TrafficManager.getTrafficManager().getTrafficTypes()) {
				if (trafficType.getUpload()) {
					if ((trafficType.checkInclude(request.getCurrentDirectory().getPath())) && (!trafficType.checkExclude(request.getCurrentDirectory().getPath())) && (trafficType.getPerms().check(request.getUserObject()))) {
						request.setObject(DataConnectionHandler.MAX_XFER_SPEED, trafficType.getMaxSpeed());
						request.setObject(DataConnectionHandler.MIN_XFER_SPEED, trafficType.getMinSpeed());
						break;
					}
				}
			}
		} catch (RuntimeException e) {
			// No Traffic Manager Loaded - IGNORE
		} catch (NoSuchUserException e) {
			// User does not exist - shouldn't happen, but ignore
		} catch (UserFileException e) {
			// User problem - ignore
		}
			
		return request;
	}
	
	public CommandRequestInterface doTrafficManagerRETR(CommandRequest request) {
		try {
			for (TrafficType trafficType : TrafficManager.getTrafficManager().getTrafficTypes()) {
				if (trafficType.getDownload()) {
					if ((trafficType.checkInclude(request.getCurrentDirectory().getPath())) && (!trafficType.checkExclude(request.getCurrentDirectory().getPath())) && (trafficType.getPerms().check(request.getUserObject()))) {
						request.setObject(DataConnectionHandler.MAX_XFER_SPEED, trafficType.getMaxSpeed());
						request.setObject(DataConnectionHandler.MIN_XFER_SPEED, trafficType.getMinSpeed());
						break;
					}
				}
			}
		} catch (RuntimeException e) {
			// No Traffic Manager Loaded - IGNORE
		} catch (NoSuchUserException e) {
			// User does not exist - shouldn't happen, but ignore
		} catch (UserFileException e) {
			// User problem - ignore
		}
			
		return request;
	}
	
}