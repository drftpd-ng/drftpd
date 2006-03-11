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
package org.drftpd.irc.listeners;

import org.apache.log4j.Logger;
import org.drftpd.irc.SiteBot;
import org.schwering.irc.lib.IRCEventListener;
import org.schwering.irc.lib.IRCUser;

/**
 * A simple listener to make the sitebot
 * able to connect to psyBNCs
 * @author fr0w
 */
public class psyBNC extends IRCListener implements IRCEventListener{

	private static final Logger logger = Logger.getLogger(psyBNC.class); 
	
	public psyBNC(SiteBot instance) {
		super(instance);
	}
	
	public void onNotice(String target, IRCUser user, String msg) {
		if (msg.indexOf("/QUOTE PASS".toLowerCase()) != -1 &&
				user.getNick().equalsIgnoreCase("-psyBNC")) {
			getIRCConnection().send("pass" + getSiteBot().getPsyBNC());
			logger.debug("Sent pass to psyBNC...");
		}
	}

}
