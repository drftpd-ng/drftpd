package net.sf.drftpd.master.command.plugins;

import java.util.Collection;
import java.util.Iterator;

import net.sf.drftpd.ObjectNotFoundException;
import net.sf.drftpd.master.BaseFtpConnection;
import net.sf.drftpd.master.FtpReply;
import net.sf.drftpd.master.RemoteSlave;
import net.sf.drftpd.master.command.CommandHandler;
import net.sf.drftpd.master.command.CommandManager;
import net.sf.drftpd.master.command.CommandManagerFactory;
import net.sf.drftpd.master.command.UnhandledCommandException;

/**
 * @author mog
 *
 * @version $Id: SlaveManagment.java,v 1.3 2003/12/23 13:38:20 mog Exp $
 */
public class SlaveManagment implements CommandHandler {
	public void unload() {}
	public void load(CommandManagerFactory initializer) {}

	private FtpReply doSITE_CHECKSLAVES(BaseFtpConnection conn) {
		conn.resetState();
		return new FtpReply(
			200,
			"Ok, "
				+ conn.getSlaveManager().verifySlaves()
				+ " stale slaves removed");
	}

	private FtpReply doSITE_KICKSLAVE(BaseFtpConnection conn) {
		conn.reset();
		if (!conn.getUserNull().isAdmin()) {
			return FtpReply.RESPONSE_530_ACCESS_DENIED;
		}
		if (!conn.getRequest().hasArgument()) {
			return FtpReply.RESPONSE_501_SYNTAX_ERROR;
		}
		RemoteSlave rslave;
		try {
			rslave =
				conn.getConnectionManager().getSlaveManager().getSlave(
					conn.getRequest().getArgument());
		} catch (ObjectNotFoundException e) {
			return new FtpReply(200, "No such slave");
		}
		if (!rslave.isAvailable()) {
			return new FtpReply(200, "Slave is already offline");
		}
		rslave.setOffline(
			"Slave kicked by " + conn.getUserNull().getUsername());
		return FtpReply.RESPONSE_200_COMMAND_OK;
	}

	/** Lists all slaves used by the master
	 * USAGE: SITE SLAVES
	 * 
	 */
	private FtpReply doSITE_SLAVES(BaseFtpConnection conn) {
		if (!conn.getUserNull().isAdmin()) {
			return FtpReply.RESPONSE_530_ACCESS_DENIED;
		}
		Collection slaves = conn.getSlaveManager().getSlaves();
		FtpReply response =
			new FtpReply(200, "OK, " + slaves.size() + " slaves listed.");

		for (Iterator iter = conn.getSlaveManager().getSlaves().iterator();
			iter.hasNext();
			) {
			RemoteSlave rslave = (RemoteSlave) iter.next();
			response.addComment(rslave.toString());
		}
		return response;
	}

	public FtpReply execute(BaseFtpConnection conn)
		throws UnhandledCommandException {
		String cmd = conn.getRequest().getCommand();
		if ("SITE CHECKSLAVES".equals(cmd))
			return doSITE_CHECKSLAVES(conn);
		if ("SITE KICKSLAVE".equals(cmd))
			return doSITE_KICKSLAVE(conn);
		if ("SITE SLAVES".equals(cmd))
			return doSITE_SLAVES(conn);
		throw UnhandledCommandException.create(
			SlaveManagment.class,
			conn.getRequest());
	}
	/* (non-Javadoc)
	 * @see net.sf.drftpd.master.command.CommandHandler#initialize(net.sf.drftpd.master.BaseFtpConnection)
	 */
	public CommandHandler initialize(BaseFtpConnection conn, CommandManager initializer) {
		return this;
	}

	public String[] getFeatReplies() {
		return null;
	}
}
