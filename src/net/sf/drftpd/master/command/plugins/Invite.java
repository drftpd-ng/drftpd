package net.sf.drftpd.master.command.plugins;

import net.sf.drftpd.event.Event;
import net.sf.drftpd.master.BaseFtpConnection;
import net.sf.drftpd.master.FtpReply;
import net.sf.drftpd.master.command.CommandHandler;
import net.sf.drftpd.master.command.CommandManager;
import net.sf.drftpd.master.command.CommandManagerFactory;
import net.sf.drftpd.master.command.UnhandledCommandException;

import org.apache.log4j.Logger;

/**
 * @author mog
 *
 * To change the template for this generated type comment go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
public class Invite implements CommandHandler {
	public Invite() {
	}
	public void unload() {
	}
	public void load(CommandManagerFactory initializer) {
	}

	private Logger logger = Logger.getLogger(Invite.class);

	public FtpReply execute(BaseFtpConnection conn)
		throws UnhandledCommandException {
		String cmd = conn.getRequest().getCommand();
		if ("SITE INVITE".equals(cmd))
			return doSITE_INVITE(conn);
		throw UnhandledCommandException.create(Invite.class, conn.getRequest());
	}

	public FtpReply doSITE_INVITE(BaseFtpConnection conn) {
		String user = conn.getRequest().getArgument();
		Event invite = new Event("INVITE " + user);
		conn.getConnectionManager().dispatchFtpEvent(invite);
		return new FtpReply(200, "Inviting " + user);
	}
	public CommandHandler initialize(
		BaseFtpConnection conn,
		CommandManager initializer) {
		return this;
	}

	public String[] getFeatReplies() {
		return null;
	}

}
