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

import java.io.IOException;
import java.util.Iterator;

import net.sf.drftpd.FileExistsException;
import net.sf.drftpd.event.DirectoryFtpEvent;
import net.sf.drftpd.master.BaseFtpConnection;
import net.sf.drftpd.master.FtpReply;
import net.sf.drftpd.master.command.CommandManager;
import net.sf.drftpd.master.command.CommandManagerFactory;
import net.sf.drftpd.master.usermanager.NoSuchUserException;
import net.sf.drftpd.remotefile.LinkedRemoteFile;
import net.sf.drftpd.remotefile.LinkedRemoteFileInterface;

import org.apache.log4j.Logger;
import org.drftpd.commands.CommandHandler;
import org.drftpd.commands.CommandHandlerFactory;
import org.drftpd.commands.UnhandledCommandException;

/**
 * @author mog
 * @version $Id: Request.java,v 1.18 2004/07/12 20:37:26 mog Exp $
 */
public class Request implements CommandHandlerFactory, CommandHandler {
	private static final String FILLEDPREFIX = "FILLED-for.";

	private static final Logger logger = Logger.getLogger(Request.class);

	private static final String REQPREFIX = "REQUEST-by.";

	private FtpReply doSITE_REQFILLED(BaseFtpConnection conn) {
		if (!conn.getRequest().hasArgument())
			return FtpReply.RESPONSE_501_SYNTAX_ERROR;

		LinkedRemoteFileInterface currdir = conn.getCurrentDirectory();
		String reqname = conn.getRequest().getArgument();

		for (Iterator iter = currdir.getFiles().iterator(); iter.hasNext();) {
			LinkedRemoteFile file = (LinkedRemoteFile) iter.next();

			if (!file.getName().startsWith(REQPREFIX))
				continue;
			String username = file.getName().substring(REQPREFIX.length());
			String myreqname = username.substring(username.indexOf('-') + 1);
			username = username.substring(0, username.indexOf('-'));
			if (myreqname.equals(reqname)) {
				String filledname = FILLEDPREFIX + username + "-" + myreqname;
				LinkedRemoteFile filledfile;
				try {
					filledfile =
						file.renameTo(
							file.getParentFile().getPath(),
							filledname);
				} catch (IOException e) {
					logger.warn("", e);
					return new FtpReply(200, e.getMessage());
				}
				//if (conn.getConfig().checkDirLog(conn.getUserNull(), file)) {
				conn.getConnectionManager().dispatchFtpEvent(
					new DirectoryFtpEvent(
						conn,
						"REQFILLED",
						filledfile));
				//}
				try {
					conn.getUser().addRequestsFilled();
				} catch (NoSuchUserException e) {
					e.printStackTrace();
				}
				return new FtpReply(
					200,
					"OK, renamed " + myreqname + " to " + filledname);
			}
		}
		return new FtpReply(200, "Couldn't find a request named " + reqname);
	}
	private FtpReply doSITE_REQUEST(BaseFtpConnection conn) {

		if (!conn.getConnectionManager().getGlobalContext().getConfig()
			.checkPathPermission(
				"request",
				conn.getUserNull(),
				conn.getCurrentDirectory())) {
			return FtpReply.RESPONSE_530_ACCESS_DENIED;
		}

		if (!conn.getRequest().hasArgument()) {
			return FtpReply.RESPONSE_501_SYNTAX_ERROR;
		}

		String createdDirName =
			REQPREFIX
				+ conn.getUserNull().getUsername()
				+ "-"
				+ conn.getRequest().getArgument();
		try {
			LinkedRemoteFile createdDir =
				conn.getCurrentDirectory().createDirectory(
					conn.getUserNull().getUsername(),
					conn.getUserNull().getGroupName(),
					createdDirName);

			//if (conn.getConfig().checkDirLog(conn.getUserNull(), createdDir)) {
			conn.getConnectionManager().dispatchFtpEvent(
				new DirectoryFtpEvent(
					conn,
					"REQUEST",
					createdDir));
			//}
			try {
				conn.getUser().addRequests();
			} catch (NoSuchUserException e) {
				e.printStackTrace();
			}
			return new FtpReply(
				257,
				"\"" + createdDir.getPath() + "\" created.");
		} catch (FileExistsException ex) {
			return new FtpReply(
				550,
				"directory " + createdDirName + " already exists");
		}
	}

	public FtpReply execute(BaseFtpConnection conn)
		throws UnhandledCommandException {
		String cmd = conn.getRequest().getCommand();
		if ("SITE REQUEST".equals(cmd))
			return doSITE_REQUEST(conn);
		if ("SITE REQFILLED".equals(cmd))
			return doSITE_REQFILLED(conn);
		throw UnhandledCommandException.create(
			Request.class,
			conn.getRequest());
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
