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
package net.sf.drftpd.event.irc;

import net.sf.drftpd.event.InviteEvent;
import net.sf.drftpd.master.ConnectionManager;
import net.sf.drftpd.master.usermanager.NoSuchUserException;
import net.sf.drftpd.master.usermanager.User;
import net.sf.drftpd.master.usermanager.UserFileException;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import f00f.net.irc.martyr.GenericCommandAutoService;
import f00f.net.irc.martyr.InCommand;
import f00f.net.irc.martyr.commands.MessageCommand;

/**
 * @author mog
 *
 * @version $Id: Invite.java,v 1.8 2004/03/21 06:20:54 zubov Exp $
 */
public class Invite extends GenericCommandAutoService implements IRCPluginInterface {
	private static final Logger logger = Logger.getLogger(Invite.class);

	public String getCommands() {
		return "!invite(msg)";
	}
	
	private ConnectionManager _cm;

	public Invite(IRCListener ircListener) {
		super(ircListener.getIRCConnection());
		_cm = ircListener.getConnectionManager();
	}

	protected void updateCommand(InCommand command) {
		if(command instanceof MessageCommand) {
			MessageCommand msgc = (MessageCommand)command;
			String msg = msgc.getMessage();
			if (msg.startsWith("!invite ")
				&& msgc.isPrivateToUs(this.getConnection().getClientState())) {
				String args[] = msg.split(" ");
				User user;
				try {
					user = _cm.getUserManager().getUserByName(args[1]);
				} catch (NoSuchUserException e) {
					logger.log(
						Level.WARN,
						args[1] + " " + e.getMessage(),
						e);
					return;
				} catch (UserFileException e) {
					logger.log(Level.WARN, "", e);
					return;
				}
				if (user.checkPassword(args[2])) {
					logger.info(
						"Invited \""
							+ msgc.getSourceString()
							+ "\" as user "
							+ user.getUsername());
					//_conn.sendCommand(
					//	new InviteCommand(msgc.getSource(), _channelName));
					getConnectionManager().dispatchFtpEvent(
						new InviteEvent(
							"INVITE",
							msgc.getSource().getNick()));
				} else {
					logger.log(
						Level.WARN,
						msgc.getSourceString()
							+ " attempted invite with bad password: "
							+ msgc);
				}
			}
		}
	}

	private ConnectionManager getConnectionManager() {
		return _cm;
	}
}
