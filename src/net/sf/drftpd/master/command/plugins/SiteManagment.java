/*
 * Created on 2003-okt-16
 *
 * To change the template for this generated file go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
package net.sf.drftpd.master.command.plugins;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;

import net.sf.drftpd.event.FtpListener;
import net.sf.drftpd.event.UserEvent;
import net.sf.drftpd.master.BaseFtpConnection;
import net.sf.drftpd.master.FtpReply;
import net.sf.drftpd.master.command.CommandHandler;
import net.sf.drftpd.master.command.CommandManager;
import net.sf.drftpd.master.command.CommandManagerFactory;
import net.sf.drftpd.master.command.UnhandledCommandException;
import net.sf.drftpd.remotefile.LinkedRemoteFile;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

/**
 * @author mog
 * @author zubov
 * @version $Id: SiteManagment.java,v 1.10 2004/02/03 01:04:06 mog Exp $
 */
public class SiteManagment implements CommandHandler {

	private static final Logger logger = Logger.getLogger(SiteManagment.class);

	private FtpReply doSITE_LIST(BaseFtpConnection conn) {
		if (!conn.getUserNull().isAdmin())
			return FtpReply.RESPONSE_530_ACCESS_DENIED;
		conn.resetState();
		FtpReply response = (FtpReply) FtpReply.RESPONSE_200_COMMAND_OK.clone();
		//.getMap().values() to get the .isDeleted files as well.
		LinkedRemoteFile dir = conn.getCurrentDirectory();
		if (conn.getRequest().hasArgument()) {
			try {
				dir = dir.lookupFile(conn.getRequest().getArgument(), true);
			} catch (FileNotFoundException e) {
				logger.debug("", e);
				return new FtpReply(200, e.getMessage());
			}
		}
		ArrayList files =
			new ArrayList(dir.getMap().values());
		Collections.sort(files);
		for (Iterator iter = files.iterator(); iter.hasNext();) {
			LinkedRemoteFile file = (LinkedRemoteFile) iter.next();
			//if (!key.equals(file.getName()))
			//	response.addComment(
			//		"WARN: " + key + " not equals to " + file.getName());
			//response.addComment(key);
			response.addComment(file.toString());
		}
		return response;
	}
	private FtpReply doSITE_LOADPLUGIN(BaseFtpConnection conn) {
		if (!conn.getUserNull().isAdmin()) {
			return FtpReply.RESPONSE_530_ACCESS_DENIED;
		}
		if (!conn.getRequest().hasArgument()) {
			return new FtpReply(500, "Usage: site load className");
		}
		FtpListener ftpListener =
			getFtpListener(conn.getRequest().getArgument());
		if (ftpListener == null)
			return new FtpReply(
				500,
				"Was not able to find the class you are trying to load");
		conn.getConnectionManager().addFtpListener(ftpListener);
		return new FtpReply(200, "Successfully loaded your plugin");
	}

	private FtpReply doSITE_PLUGINS(BaseFtpConnection conn) {
		if (!conn.getUserNull().isAdmin()) {
			return FtpReply.RESPONSE_530_ACCESS_DENIED;
		}
		FtpReply ftpReply = new FtpReply(200, "Command ok");
		ftpReply.addComment("Plugins loaded:");
		for (Iterator iter =
			conn.getConnectionManager().getFtpListeners().iterator();
			iter.hasNext();
			) {
			ftpReply.addComment(iter.next().getClass().getName());
		}
		return ftpReply;
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
		conn.getConnectionManager().dispatchFtpEvent(
			new UserEvent(conn.getUserNull(), "RELOAD"));
		return FtpReply.RESPONSE_200_COMMAND_OK;
	}

	private FtpReply doSITE_SHUTDOWN(BaseFtpConnection conn) {
		conn.resetState();
		if (!conn.getUserNull().isAdmin()) {
			return FtpReply.RESPONSE_530_ACCESS_DENIED;
		}
		String message;
		if (!conn.getRequest().hasArgument()) {
			message =
				"Service shutdown issued by "
					+ conn.getUserNull().getUsername();
		} else {
			message = conn.getRequest().getArgument();
		}
		conn.getConnectionManager().shutdown(message);
		return FtpReply.RESPONSE_200_COMMAND_OK;
	}
	private FtpReply doSITE_UNLOADPLUGIN(BaseFtpConnection conn) {
		if (!conn.getUserNull().isAdmin()) {
			return FtpReply.RESPONSE_530_ACCESS_DENIED;
		}
		if (!conn.getRequest().hasArgument()) {
			return new FtpReply(500, "Usage: site unload className");
		}
		for (Iterator iter =
			conn.getConnectionManager().getFtpListeners().iterator();
			iter.hasNext();
			) {
			FtpListener ftpListener = (FtpListener) iter.next();
			if (ftpListener
				.getClass()
				.getName()
				.equals(
					"net.sf.drftpd.event.listeners."
						+ conn.getRequest().getArgument())
				|| ftpListener.getClass().getName().equals(
					conn.getRequest().getArgument())) {
				ftpListener.unload();
				iter.remove();
				return new FtpReply(200, "Successfully unloaded your plugin");
			}
		}
		return new FtpReply(500, "Could not find your plugin on the site");
	}

	public FtpReply execute(BaseFtpConnection conn)
		throws UnhandledCommandException {
		String cmd = conn.getRequest().getCommand();
		if ("SITE RELOAD".equals(cmd))
			return doSITE_RELOAD(conn);
		if ("SITE SHUTDOWN".equals(cmd))
			return doSITE_SHUTDOWN(conn);
		if ("SITE LIST".equals(cmd))
			return doSITE_LIST(conn);
		if ("SITE LOADPLUGIN".equals(cmd))
			return doSITE_LOADPLUGIN(conn);
		if ("SITE UNLOADPLUGIN".equals(cmd))
			return doSITE_UNLOADPLUGIN(conn);
		if ("SITE PLUGINS".equals(cmd))
			return doSITE_PLUGINS(conn);
		throw UnhandledCommandException.create(
			SiteManagment.class,
			conn.getRequest());
	}

	public String[] getFeatReplies() {
		return null;
	}
	private FtpListener getFtpListener(String arg) {
		FtpListener ftpListener = null;
		try {
			ftpListener =
				(FtpListener) Class
					.forName("net.sf.drftpd.event.listeners." + arg)
					.newInstance();
		} catch (InstantiationException e) {
			logger.error(
				"Was not able to create an instance of the class, did not load",
				e);
			return null;
		} catch (IllegalAccessException e) {
			logger.error("This will not happen, I do not exist", e);
			return null;
		} catch (ClassNotFoundException e) {
		}
		if (ftpListener == null) {
			try {
				ftpListener = (FtpListener) Class.forName(arg).newInstance();
			} catch (InstantiationException e) {
				logger.error(
					"Was not able to create an instance of the class, did not load",
					e);
				return null;
			} catch (IllegalAccessException e) {
				logger.error("This will not happen, I do not exist", e);
				return null;
			} catch (ClassNotFoundException e) {
				return null;
			}
		}
		return ftpListener;
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
