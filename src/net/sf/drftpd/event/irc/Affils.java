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

import net.sf.drftpd.master.ConnectionManager;
import net.sf.drftpd.master.command.plugins.Textoutput;

import org.drftpd.plugins.SiteBot;

import f00f.net.irc.martyr.GenericCommandAutoService;
import f00f.net.irc.martyr.InCommand;
import f00f.net.irc.martyr.commands.MessageCommand;

/**
 * @author zubov
 * @version $Id: Affils.java,v 1.5 2004/05/12 00:45:04 mog Exp $
 */
public class Affils extends GenericCommandAutoService implements IRCPluginInterface {

	private ConnectionManager _cm;
	public Affils(SiteBot ircListener) {
		super(ircListener.getIRCConnection());
		_cm = ircListener.getConnectionManager();
	}

	protected void updateCommand(InCommand command) {
		if (command instanceof MessageCommand) {
			MessageCommand msgc = (MessageCommand) command;
			String msg = msgc.getMessage();
			if (msg.startsWith("!affils")) {
				if (msgc.isPrivateToUs(getConnection().getClientState())) {
//					Textoutput.sendTextToIRC(
//						getConnection(),
//						msgc.getSource().getNick(),
//						"affils");
				} else
					Textoutput.sendTextToIRC(
						getConnection(),
						msgc.getDest(),
						"affils");
			}
		}
	}

	public String getCommands() {
		return "!affils";
	}

}
