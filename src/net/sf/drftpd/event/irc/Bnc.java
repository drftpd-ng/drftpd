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

import org.apache.log4j.Logger;

import net.sf.drftpd.master.ConnectionManager;
import net.sf.drftpd.master.command.plugins.Textoutput;
import f00f.net.irc.martyr.GenericCommandAutoService;
import f00f.net.irc.martyr.InCommand;
import f00f.net.irc.martyr.commands.MessageCommand;

/**
 * @author zubov
 */
public class Bnc extends GenericCommandAutoService implements IRCPluginInterface {

	private ConnectionManager _cm;
	private static final Logger logger = Logger.getLogger(Bnc.class);

	public Bnc(IRCListener ircListener) {
		super(ircListener.getIRCConnection());
		_cm = ircListener.getConnectionManager();
	}

	protected void updateCommand(InCommand command) {
		try {
		if (command instanceof MessageCommand) {
			MessageCommand msgc = (MessageCommand) command;
			String msg = msgc.getMessage();
			try {
				if (msg.startsWith("!bnc")) {
					if (msgc.isPrivateToUs(getConnection().getClientState())) {
						Textoutput.sendTextToIRC(
							getConnection(),
							msgc.getSource().getNick(),
							"bnc");
					} else
						Textoutput.sendTextToIRC(
							getConnection(),
							msgc.getDest(),
							"bnc");
				}
			} catch (Exception e) {
				logger.debug("", e);
			}
		}
	}catch (Exception e) {
		logger.debug("",e);
	}
	}
	public String getCommands() {
		return "!bnc";
	}
}
