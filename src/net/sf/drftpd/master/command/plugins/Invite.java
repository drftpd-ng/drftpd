package net.sf.drftpd.master.command.plugins;

import net.sf.drftpd.event.InviteEvent;
import net.sf.drftpd.master.BaseFtpConnection;
import net.sf.drftpd.master.FtpReply;
import net.sf.drftpd.master.command.CommandHandler;
import net.sf.drftpd.master.command.CommandManager;
import net.sf.drftpd.master.command.CommandManagerFactory;
import net.sf.drftpd.master.command.UnhandledCommandException;

/**
 * @author mog
 *
 * @version $Id: Invite.java,v 1.6 2004/01/13 20:30:54 mog Exp $
 */
public class Invite implements CommandHandler {
	public Invite() {
	}

	public FtpReply doSITE_INVITE(BaseFtpConnection conn) {
		String user = conn.getRequest().getArgument();
		InviteEvent invite = new InviteEvent(conn.getRequest().getCommand(),user);
		conn.getConnectionManager().dispatchFtpEvent(invite);
		return new FtpReply(200, "Inviting " + user);
	}

	public FtpReply execute(BaseFtpConnection conn)
		throws UnhandledCommandException {
		String cmd = conn.getRequest().getCommand();
		if ("SITE INVITE".equals(cmd))
			return doSITE_INVITE(conn);
		throw UnhandledCommandException.create(Invite.class, conn.getRequest());
	}

	public String[] getFeatReplies() {
		return null;
	}
	public CommandHandler initialize(
		BaseFtpConnection conn,
		CommandManager initializer) {
		return this;
	}
	public void load(CommandManagerFactory initializer) {
	}
	public void unload() {
	}

}
