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
import net.sf.drftpd.slave.SlaveStatus;
import net.sf.drftpd.util.ReplacerUtils;

import org.apache.log4j.Logger;

import org.tanesha.replacer.FormatterException;
import org.tanesha.replacer.ReplacerEnvironment;

import f00f.net.irc.martyr.GenericCommandAutoService;
import f00f.net.irc.martyr.InCommand;
import f00f.net.irc.martyr.commands.MessageCommand;

/**
 * @author zubov
 * @version $Id
 */

public class Diskfree
	extends GenericCommandAutoService
	implements IRCPluginInterface {

	private IRCListener _listener;

	private static final Logger logger = Logger.getLogger(Diskfree.class);

	private ConnectionManager getConnectionManager() {
		return _listener.getConnectionManager();
	}

	private void say(String string) {
		_listener.say(string);
	}

	private void fillEnvSpace(ReplacerEnvironment env, SlaveStatus status) {
		_listener.fillEnvSpace(env, status);
	}

	public Diskfree(IRCListener listener) {
		super(listener.getIRCConnection());
		_listener = listener;
	}

	private void updateDF(MessageCommand msgc) throws FormatterException {
		SlaveStatus status =
			getConnectionManager().getSlaveManager().getAllStatus();
		ReplacerEnvironment env =
			new ReplacerEnvironment(IRCListener.GLOBAL_ENV);

		fillEnvSpace(env, status);
		say(ReplacerUtils.jprintf("diskfree", env, IRCListener.class));
	}

	public String getCommands() {
		return "!df";
	}

	protected void updateCommand(InCommand command) {
		try {
			if (command instanceof MessageCommand) {
				MessageCommand msgc = (MessageCommand) command;
				String msg = msgc.getMessage();
				//only accept messages from _channelName
				if (msg.equals("!df")) {
					try {
						updateDF(msgc);
					} catch (FormatterException e) {
						say("[df] FormatterException: " + e.getMessage());
					}
				}
			}
		} catch (Exception e) {
			logger.debug("", e);
		}

	}
}