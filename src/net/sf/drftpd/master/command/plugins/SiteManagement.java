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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

import net.sf.drftpd.event.ConnectionEvent;
import net.sf.drftpd.event.FtpListener;
import net.sf.drftpd.master.BaseFtpConnection;
import net.sf.drftpd.master.command.CommandManager;
import net.sf.drftpd.master.command.CommandManagerFactory;

import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.helpers.OptionConverter;
import org.drftpd.PropertyHelper;
import org.drftpd.commands.CommandHandler;
import org.drftpd.commands.CommandHandlerFactory;
import org.drftpd.commands.Reply;
import org.drftpd.commands.ReplyException;
import org.drftpd.commands.UnhandledCommandException;
import org.drftpd.remotefile.LinkedRemoteFileInterface;

/**
 * @author mog
 * @author zubov
 * @version $Id$
 */
public class SiteManagement extends CommandHandler implements CommandHandlerFactory {
	private static final Logger logger = Logger.getLogger(SiteManagement.class);

	private Reply doSITE_LIST(BaseFtpConnection conn) {
		if (!conn.getUserNull().isAdmin()) {
			return Reply.RESPONSE_530_ACCESS_DENIED;
		}

		Reply response = (Reply) Reply.RESPONSE_200_COMMAND_OK.clone();

		// .getMap().values() to get the .isDeleted files as well.
		LinkedRemoteFileInterface dir = conn.getCurrentDirectory();

		if (conn.getRequest().hasArgument()) {
			try {
				dir = dir.lookupFile(conn.getRequest().getArgument());
			} catch (FileNotFoundException e) {
				logger.debug("", e);

				return new Reply(200, e.getMessage());
			}
		}

		List files;
		if (dir.isFile()) {
			files = Collections.singletonList(dir);
		} else {
			files = new ArrayList(dir.getMap().values());
		}
		Collections.sort(files);

		for (Iterator iter = files.iterator(); iter.hasNext();) {
			LinkedRemoteFileInterface file = (LinkedRemoteFileInterface) iter
					.next();

			// if (!key.equals(file.getName()))
			// response.addComment(
			// "WARN: " + key + " not equals to " + file.getName());
			// response.addComment(key);
			response.addComment(file.toString());
		}

		return response;
	}

	private Reply doSITE_LOADPLUGIN(BaseFtpConnection conn) {
		if (!conn.getUserNull().isAdmin()) {
			return Reply.RESPONSE_530_ACCESS_DENIED;
		}

		if (!conn.getRequest().hasArgument()) {
			return new Reply(500, "Usage: site load className");
		}

		FtpListener ftpListener = getFtpListener(conn.getRequest()
				.getArgument());

		if (ftpListener == null) {
			return new Reply(500,
					"Was not able to find the class you are trying to load");
		}

		conn.getGlobalContext().addFtpListener(ftpListener);

		return new Reply(200, "Successfully loaded your plugin");
	}

	private Reply doSITE_PLUGINS(BaseFtpConnection conn) {
		if (!conn.getUserNull().isAdmin()) {
			return Reply.RESPONSE_530_ACCESS_DENIED;
		}

		Reply ftpReply = new Reply(200, "Command ok");
		ftpReply.addComment("Plugins loaded:");

		for (Iterator iter = conn.getGlobalContext().getFtpListeners()
				.iterator(); iter.hasNext();) {
			ftpReply.addComment(iter.next().getClass().getName());
		}

		return ftpReply;
	}

	private Reply doSITE_RELOAD(BaseFtpConnection conn) throws ReplyException {
		if (!conn.getUserNull().isAdmin()) {
			return Reply.RESPONSE_530_ACCESS_DENIED;
		}

		try {
			conn.getGlobalContext().getSectionManager().reload();
			conn.getGlobalContext().reloadFtpConfig();
			conn.getGlobalContext().getSlaveSelectionManager().reload();

			try {
				conn.getGlobalContext().getJobManager()
						.reload();
			} catch (IllegalStateException e1) {
				// not loaded, don't reload
			}

			conn.getGlobalContext().getConnectionManager()
					.getCommandManagerFactory().reload();

		} catch (IOException e) {
			logger.log(Level.FATAL, "Error reloading config", e);

			return new Reply(200, e.getMessage());
		}

		conn.getGlobalContext().dispatchFtpEvent(
				new ConnectionEvent(conn, "RELOAD"));

		// ugly hack to clear resourcebundle cache
		// see
		// http://developer.java.sun.com/developer/bugParade/bugs/4212439.html
		try {
			Field cacheList = ResourceBundle.class
					.getDeclaredField("cacheList");
			cacheList.setAccessible(true);
			((Map) cacheList.get(ResourceBundle.class)).clear();
			cacheList.setAccessible(false);
		} catch (Exception e) {
			logger.error("", e);
		}

		try {
			OptionConverter.selectAndConfigure(
					new URL(PropertyHelper.getProperty(System.getProperties(),
							"log4j.configuration")), null, LogManager
							.getLoggerRepository());
		} catch (MalformedURLException e) {
			throw new ReplyException(e);
		} finally {
		}
		return Reply.RESPONSE_200_COMMAND_OK;
	}

	private Reply doSITE_SHUTDOWN(BaseFtpConnection conn) {
		if (!conn.getUserNull().isAdmin()) {
			return Reply.RESPONSE_530_ACCESS_DENIED;
		}

		String message;

		if (!conn.getRequest().hasArgument()) {
			message = "Service shutdown issued by "
					+ conn.getUserNull().getName();
		} else {
			message = conn.getRequest().getArgument();
		}

		conn.getGlobalContext().shutdown(message);

		return Reply.RESPONSE_200_COMMAND_OK;
	}

	private Reply doSITE_UNLOADPLUGIN(BaseFtpConnection conn) {
		if (!conn.getUserNull().isAdmin()) {
			return Reply.RESPONSE_530_ACCESS_DENIED;
		}

		if (!conn.getRequest().hasArgument()) {
			return new Reply(500, "Usage: site unload className");
		}

		for (Iterator iter = conn.getGlobalContext().getFtpListeners()
				.iterator(); iter.hasNext();) {
			FtpListener ftpListener = (FtpListener) iter.next();

			if (ftpListener.getClass().getName().equals(
					"org.drftpd.plugins."
							+ conn.getRequest().getArgument())
					|| ftpListener.getClass().getName().equals(
							conn.getRequest().getArgument())) {
				try {
					ftpListener.unload();
				} catch (RuntimeException e) {
					return new Reply(200,
							"Exception unloading plugin, plugin removed");
				}

				iter.remove();

				return new Reply(200, "Successfully unloaded your plugin");
			}
		}

		return new Reply(500, "Could not find your plugin on the site");
	}

	public Reply execute(BaseFtpConnection conn) throws ReplyException {
		String cmd = conn.getRequest().getCommand();

		if ("SITE RELOAD".equals(cmd)) {
			return doSITE_RELOAD(conn);
		}

		if ("SITE SHUTDOWN".equals(cmd)) {
			return doSITE_SHUTDOWN(conn);
		}

		if ("SITE LIST".equals(cmd)) {
			return doSITE_LIST(conn);
		}

		if ("SITE LOADPLUGIN".equals(cmd)) {
			return doSITE_LOADPLUGIN(conn);
		}

		if ("SITE UNLOADPLUGIN".equals(cmd)) {
			return doSITE_UNLOADPLUGIN(conn);
		}

		if ("SITE PLUGINS".equals(cmd)) {
			return doSITE_PLUGINS(conn);
		}

		throw UnhandledCommandException.create(SiteManagement.class, conn
				.getRequest());
	}

    public String getHelp(String cmd) {
        ResourceBundle bundle = ResourceBundle.getBundle(SiteManagement.class.getName());
        if ("".equals(cmd))
            return bundle.getString("help.general")+"\n";
        else if("list".equals(cmd) || "plugins".equals(cmd) || "loadplugin".equals(cmd) ||
                "unloadplugin".equals(cmd) || "shutdown".equals(cmd) || "reload".equals(cmd))
            return bundle.getString("help."+cmd)+"\n";
        else
            return "";
    }
    
    public String[] getFeatReplies() {
		return null;
	}

	private FtpListener getFtpListener(String arg) {
		FtpListener ftpListener = null;

		try {
			ftpListener = (FtpListener) Class.forName(
					"org.drftpd.plugins." + arg).newInstance();
		} catch (InstantiationException e) {
			logger
					.error(
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
				logger
						.error(
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

	public CommandHandler initialize(BaseFtpConnection conn,
			CommandManager initializer) {
		return this;
	}

	public void load(CommandManagerFactory initializer) {
	}

	public void unload() {
	}
}
