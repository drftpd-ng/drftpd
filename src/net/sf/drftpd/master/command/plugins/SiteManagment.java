/*
 * Created on 2003-okt-16
 *
 * To change the template for this generated file go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
package net.sf.drftpd.master.command.plugins;

import java.io.IOException;

import net.sf.drftpd.event.UserEvent;
import net.sf.drftpd.master.BaseFtpConnection;
import net.sf.drftpd.master.FtpReply;
import net.sf.drftpd.master.command.CommandHandler;
import net.sf.drftpd.master.command.CommandManager;
import net.sf.drftpd.master.command.CommandManagerFactory;
import net.sf.drftpd.master.command.UnhandledCommandException;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

/**
 * @author mog
 *
 * To change the template for this generated type comment go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
public class SiteManagment implements CommandHandler {
	public void unload() {}
	public void load(CommandManagerFactory initializer) {}

	private Logger logger = Logger.getLogger(SiteManagment.class);

	private FtpReply doSITE_SHUTDOWN(BaseFtpConnection conn) {
		conn.resetState();
		if (!conn.getUserNull().isAdmin()) {
			return FtpReply.RESPONSE_530_ACCESS_DENIED;
		}
		String message;
		if (!conn.getRequest().hasArgument()) {
			message = "Service shutdown issued by " + conn.getUserNull().getUsername();
		} else {
			message = conn.getRequest().getArgument();
		}
		conn.getConnectionManager().shutdown(message);
		return FtpReply.RESPONSE_200_COMMAND_OK;
	}

	private FtpReply doSITE_RELOAD(BaseFtpConnection conn) {
		conn.resetState();
		if (!conn.getUserNull().isAdmin()) {
			return FtpReply.RESPONSE_530_ACCESS_DENIED;
		}

		try {
			conn.getConnectionManager().getConfig().reloadConfig();
			conn.getSlaveManager().reloadRSlaves();
			conn.getConnectionManager().getCommandManagerFactory().reload();
			//slaveManager.saveFilesXML();
		} catch (IOException e) {
			logger.log(Level.FATAL, "Error reloading config", e);
			return new FtpReply(200, e.getMessage());
		}
		conn.getConnectionManager().dispatchFtpEvent(new UserEvent(conn.getUserNull(), "RELOAD"));
		return FtpReply.RESPONSE_200_COMMAND_OK;
	}

	public FtpReply execute(BaseFtpConnection conn) throws UnhandledCommandException {
		String cmd = conn.getRequest().getCommand();
		if("SITE RELOAD".equals(cmd)) return doSITE_RELOAD(conn);
		if("SITE SHUTDOWN".equals(cmd)) return doSITE_SHUTDOWN(conn);
		throw UnhandledCommandException.create(SiteManagment.class, conn.getRequest());
	}
	public CommandHandler initialize(BaseFtpConnection conn, CommandManager initializer) {
		return this;
	}

	public String[] getFeatReplies() {
		return null;
	}

}
