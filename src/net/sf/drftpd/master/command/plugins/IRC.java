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
package net.sf.drftpd.master.command.plugins;

import net.sf.drftpd.ObjectNotFoundException;
import net.sf.drftpd.master.BaseFtpConnection;
import net.sf.drftpd.master.FtpReply;
import net.sf.drftpd.master.FtpRequest;
import net.sf.drftpd.master.command.CommandManager;
import net.sf.drftpd.master.command.CommandManagerFactory;
import net.sf.drftpd.master.usermanager.NoSuchUserException;

import org.apache.log4j.Logger;
import org.drftpd.commands.CommandHandler;
import org.drftpd.commands.CommandHandlerFactory;
import org.drftpd.commands.UnhandledCommandException;
import org.drftpd.plugins.SiteBot;

/**
 * @author mog
 * @version $Id: IRC.java,v 1.5 2004/06/04 14:18:56 mog Exp $
 */
public class IRC implements CommandHandlerFactory, CommandHandler {

	private static final Logger logger = Logger.getLogger(IRC.class);

	public IRC() {
		super();
	}

	public FtpReply execute(BaseFtpConnection conn)
		throws UnhandledCommandException {
		try {
			if (!conn.getUser().isAdmin()) {
				return FtpReply.RESPONSE_530_ACCESS_DENIED;
			}
		} catch (NoSuchUserException e1) {
			throw new RuntimeException(e1);
		}
		SiteBot irc;
		try {
			irc =
				(SiteBot) conn.getConnectionManager().getFtpListener(
					SiteBot.class);
		} catch (ObjectNotFoundException e) {
			return new FtpReply(500, "IRCListener not loaded");
		}

		if (!conn.getRequest().hasArgument())
			return FtpReply.RESPONSE_501_SYNTAX_ERROR;
		FtpRequest req2 = new FtpRequest(conn.getRequest().getArgument());
		if (req2.getCommand().equals("RECONNECT")) {
			irc.reconnect();
			return new FtpReply(
				200,
				"Told bot to disconnect, auto-reconnect should handle the rest");
		} else if (req2.getCommand().equals("DISCONNECT")) {
			irc.disconnect();
			return new FtpReply(200, "Told bot to disconnect");
		} else if (req2.getCommand().equals("CONNECT")) {
			try {
				irc.connect();
				return new FtpReply(200, "Sitebot connected");
			} catch (Exception e) {
				logger.warn("", e);
				return new FtpReply(500, e.getMessage());
			}
		} else if (req2.getCommand().equals("SAY")) {
			irc.sayGlobal(req2.getArgument());
			return new FtpReply(200, "Said: " + req2.getArgument());
		}
		return new FtpReply(501, conn.jprintf(IRC.class, "irc.usage"));
	}

	public CommandHandler initialize(
		BaseFtpConnection conn,
		CommandManager initializer) {
		return this;
	}

	public String[] getFeatReplies() {
		return null;
	}

	public void load(CommandManagerFactory initializer) {
	}

	public void unload() {
	}

}
