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

package org.drftpd.irc;

import java.util.TimerTask;

import org.drftpd.irc.utils.Channel;

/**
 * Tasker is a like crontab that will make the SiteBot
 * reconnect or join channels that it was supposed to be
 * but it isnt (was kicked, for example).
 * @author fr0w
 */
public class Tasker extends TimerTask {

	private SiteBot _instance;
	
	public Tasker(SiteBot instance) {
		_instance = instance;
	}
	
	private SiteBot getSiteBot() {
		return _instance;
	}
	
	public void run() {
		//logger.debug("Running IRCBot Tasker!");
		// autoreconnect
		if (!getSiteBot().isConnected() && getSiteBot().autoReconnect()) {
			if (getSiteBot().getRetries() < getSiteBot().getMaxNumRetries()) {
				getSiteBot().setRetries(getSiteBot().getRetries() + 1);
				// we MUST create another IRCConnection object in order to re-connect.
				getSiteBot().setIRCConn(getSiteBot().newIRCConn());
				getSiteBot().connect();
			}
		}
		
		// autojoin
		for (Channel chan : getSiteBot().getChannels()) {
			if (!chan.isOn())
				getSiteBot().getIRCConnection().doJoin(chan.getName(), chan.getKey());
		}		
	}
}
