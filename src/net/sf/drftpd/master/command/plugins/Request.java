package net.sf.drftpd.master.command.plugins;

import java.io.IOException;
import java.util.Iterator;

import net.sf.drftpd.ObjectExistsException;
import net.sf.drftpd.event.DirectoryFtpEvent;
import net.sf.drftpd.master.BaseFtpConnection;
import net.sf.drftpd.master.FtpReply;
import net.sf.drftpd.master.command.CommandHandler;
import net.sf.drftpd.master.command.CommandManager;
import net.sf.drftpd.master.command.CommandManagerFactory;
import net.sf.drftpd.master.command.UnhandledCommandException;
import net.sf.drftpd.master.usermanager.NoSuchUserException;
import net.sf.drftpd.remotefile.LinkedRemoteFile;

import org.apache.log4j.Logger;

/**
 * @author mog
 *
 * @version $Id: Request.java,v 1.5 2003/12/23 13:38:20 mog Exp $
 */
public class Request implements CommandHandler {
	public void unload() {}
	public void load(CommandManagerFactory initializer) {}

	private static Logger logger = Logger.getLogger(Request.class);
	
	private static final String REQPREFIX = "REQUEST-by.";
	private static final String FILLEDPREFIX = "FILLED-by.";
	private FtpReply doSITE_REQUEST(BaseFtpConnection conn) {
		conn.resetState();

		if (!conn.getConfig().checkRequest(conn.getUserNull(), conn.getCurrentDirectory())) {
			return FtpReply.RESPONSE_530_ACCESS_DENIED;
		}

		if (!conn.getRequest().hasArgument()) {
			return FtpReply.RESPONSE_501_SYNTAX_ERROR;
		}

		String createdDirName =
			REQPREFIX + conn.getUserNull().getUsername() + "-" + conn.getRequest().getArgument();
		try {
			LinkedRemoteFile createdDir =
				conn.getCurrentDirectory().createDirectory(
					conn.getUserNull().getUsername(),
					conn.getUserNull().getGroupName(),
					createdDirName);

			if (conn.getConfig().checkDirLog(conn.getUserNull(), createdDir)) {
				conn.getConnectionManager().dispatchFtpEvent(
					new DirectoryFtpEvent(conn.getUserNull(), "REQUEST", createdDir));
			}
			try {
				conn.getUser().addRequests();
			} catch (NoSuchUserException e) {
				e.printStackTrace();
			}
			return new FtpReply(257, "\"" + createdDir.getPath() + "\" created.");
		} catch (ObjectExistsException ex) {
			return new FtpReply(550, "directory " + createdDirName + " already exists");
		}
	}

	public FtpReply execute(BaseFtpConnection conn) throws UnhandledCommandException {
		String cmd = conn.getRequest().getCommand();
		if("SITE REQUEST".equals(cmd)) return doSITE_REQUEST(conn);
		if("SITE REQFILLED".equals(cmd)) return doSITE_REQFILLED(conn);
		throw UnhandledCommandException.create(Request.class, conn.getRequest());
	}

	/**
	 * @param conn
	 * @return
	 */
	private FtpReply doSITE_REQFILLED(BaseFtpConnection conn) {
		if(!conn.getRequest().hasArgument()) return FtpReply.RESPONSE_501_SYNTAX_ERROR;
		
		LinkedRemoteFile currdir = conn.getCurrentDirectory();
		String reqname = conn.getRequest().getArgument();
		
		for (Iterator iter = currdir.getFiles().iterator(); iter.hasNext();) {
			LinkedRemoteFile file = (LinkedRemoteFile) iter.next();
			
			if(!file.getName().startsWith(REQPREFIX)) continue;
			String username = file.getName().substring(REQPREFIX.length());
			String myreqname = username.substring(username.indexOf('-')+1);
			username = username.substring(0, username.indexOf('-'));
			if(myreqname.equals(reqname)) {
				String filledname = FILLEDPREFIX+username+"-"+myreqname;
				try {
					file.renameTo(file.getParentFile().getPath(), filledname);
				} catch (IOException e) {
					logger.warn("", e);
					return new FtpReply(200, e.getMessage());
				}
				if (conn.getConfig().checkDirLog(conn.getUserNull(), file)) {
					conn.getConnectionManager().dispatchFtpEvent(
						new DirectoryFtpEvent(conn.getUserNull(), "REQFILLED", file));
				}
				try {
					conn.getUser().addRequestsFilled();
				} catch (NoSuchUserException e) {
					e.printStackTrace();
				}
				return new FtpReply(200, "OK, renamed "+ myreqname+ " to "+filledname);
			}
		}
		return new FtpReply(200, "Couldn't find a request named "+reqname);
	}

	public CommandHandler initialize(BaseFtpConnection conn, CommandManager initializer) {
		return this;
	}

	public String[] getFeatReplies() {
		return null;
	}
}
