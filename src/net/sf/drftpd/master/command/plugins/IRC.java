package net.sf.drftpd.master.command.plugins;

import net.sf.drftpd.ObjectNotFoundException;
import net.sf.drftpd.event.irc.IRCListener;
import net.sf.drftpd.master.BaseFtpConnection;
import net.sf.drftpd.master.FtpReply;
import net.sf.drftpd.master.FtpRequest;
import net.sf.drftpd.master.command.CommandHandler;
import net.sf.drftpd.master.command.CommandManager;
import net.sf.drftpd.master.command.CommandManagerFactory;
import net.sf.drftpd.master.command.UnhandledCommandException;
import net.sf.drftpd.master.usermanager.NoSuchUserException;

import org.apache.log4j.Logger;

/**
 * @author mog
 * @version $Id: IRC.java,v 1.1 2004/02/03 20:03:14 mog Exp $
 */
public class IRC implements CommandHandler {

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
		IRCListener irc;
		try {
			irc =
				(IRCListener) conn.getConnectionManager().getFtpListener(
					IRCListener.class);
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
			irc.say(req2.getArgument());
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
