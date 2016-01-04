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
package org.drftpd.plugins.trafficmanager.types.ban;

import java.util.Date;
import java.util.Properties;

import org.drftpd.GlobalContext;
import org.drftpd.commands.UserManagement;
import org.drftpd.master.BaseFtpConnection;
import org.drftpd.master.FtpReply;
import org.drftpd.plugins.trafficmanager.TrafficType;
import org.drftpd.usermanager.User;
import org.drftpd.vfs.FileHandle;

/**
 * @author CyBeR
 * @version $Id: TrafficBan.java 1925 2009-06-15 21:46:05Z CyBeR $
 */

public class TrafficBan extends TrafficType {
	
	private String _reason;
	private long _bantime;
	private boolean _kickall;
	
	public TrafficBan(Properties p, int confnum, String type) {
		super(p, confnum, type);
		
		_reason = p.getProperty(confnum + ".reason","Trasnfering Too Slow").trim();
		
		try {
			_bantime = Integer.parseInt(p.getProperty(confnum + ".bantime","300").trim()) * 1000;
		} catch (NumberFormatException e) {
    		throw new RuntimeException("Invalid BanTime for " + confnum + ".bantime - Skipping Config");
		}
		
		_kickall = p.getProperty(confnum + ".kickall","true").trim().equalsIgnoreCase("true");
	}

	@Override
	public void doAction(User user, FileHandle file, boolean isStor, long minspeed, long speed, long transfered, BaseFtpConnection conn,String slavename) {
		user.getKeyedMap().setObject(UserManagement.BAN_TIME,new Date(System.currentTimeMillis() + _bantime));
		user.getKeyedMap().setObject(UserManagement.BAN_REASON, _reason);
		user.commit();					
		
		if (_kickall) {
			for (BaseFtpConnection connection : GlobalContext.getConnectionManager().getConnections()) {
				if (connection.getUsername().equals(user.getName())) {
					connection.printOutput(new FtpReply(426, _reason));
					if (isStor) {
						connection.abortCommand();
					}
					if (doDelete(file)) {
						try {
							wait(1000);
						} catch (InterruptedException e) {

						}
					}
				connection.stop();
				}
			}
		} else {
			conn.printOutput(new FtpReply(426, _reason));
			if (isStor) {
				conn.abortCommand();
			}
			if (doDelete(file)) {
				try {
					wait(1000);
				} catch (InterruptedException e) {

				}
			}
			conn.stop();
		}
		GlobalContext.getEventService().publishAsync(new TrafficTypeBanEvent(getType(),user,file,isStor,minspeed,speed,transfered,slavename,_bantime));
	}

}
