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
package org.drftpd.master;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.Security;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;


import org.apache.log4j.Logger;
import org.drftpd.GlobalContext;
import org.drftpd.PropertyHelper;
import org.drftpd.commandmanager.CommandManagerInterface;
import org.drftpd.commands.UserManagement;
import org.drftpd.event.Event;
import org.drftpd.exceptions.FatalException;
import org.drftpd.master.FtpReply;
import org.drftpd.slave.Slave;
import org.drftpd.usermanager.NoSuchUserException;
import org.drftpd.usermanager.User;
import org.java.plugin.PluginManager;
import org.java.plugin.registry.Extension;
import org.java.plugin.registry.ExtensionPoint;

/**
 * @version $Id$
 */
public class ConnectionManager {
	private static final Logger logger = Logger
			.getLogger(ConnectionManager.class.getName());

	private static final String cmdConf = "conf/ftpcommands.conf";

	private static ConnectionManager _connectionManager = null;

	private HashMap<String,Properties> _cmds;

	private CommandManagerInterface _commandManager = null;

	private List<BaseFtpConnection> _conns = Collections
			.synchronizedList(new ArrayList<BaseFtpConnection>());

	/**
	 * If you're creating a ConnectionManager object and it's not part of a TestCase
	 * you're not doing it correctly, ConnectionManager is a Singleton
	 *
	 */
	protected ConnectionManager() {

		// getGlobalContext().addFtpListener(new RaceStatistics());
		// getGlobalContext().addFtpListener(new Statistics());

		// loadTimer();
		getGlobalContext().getSlaveManager().addShutdownHook();
	}

	public static ConnectionManager getConnectionManager() {
		if (_connectionManager == null) {
			_connectionManager = new ConnectionManager();
		}
		return _connectionManager;
	}

	public static void boot() {
		System.out.println(Slave.VERSION + " master server starting.");
		System.out.println("http://drftpd.org/");
		System.out
				.println("Further logging will be done using (mostly) log4j, check logs/");

		try {
			logger.info("Starting ConnectionManager");

			// try using the JuiCE if available

			Class<?> juiceJCE = null;
			Class<?> bcJCE = null;

			try {
				juiceJCE = Class.forName("org.apache.security.juice.provider.JuiCEProviderOpenSSL");
			} catch (ClassNotFoundException e) {
				logger.info("JuiCE JCE not installed, using java native JSSE");
			}

			// Only try installing JuiCE provider if the class was found
			
			if (juiceJCE != null) {
				int provider1Pos = Security.insertProviderAt((java.security.Provider) juiceJCE
					.newInstance(), 2);
				if (provider1Pos == -1) {
					logger.info("Loading of JuiCE JCE failed");
				}
				else {
					logger.debug("Juice JCE Provider successfully inserted at position: "+provider1Pos);
				}
				try {
					bcJCE = Class.forName("org.bouncycastle.jce.provider.BouncyCastleProvider");
				} catch (ClassNotFoundException e) {
					logger.fatal("JuiCE JCE found but Bouncy Castle JCE not installed, please check installation");
				}
				if (bcJCE != null) {
					int provider2Pos = Security.insertProviderAt((java.security.Provider) bcJCE
						.newInstance(), 3);
					if (provider2Pos == -1) {
						logger.info("Loading of Bouncy Castle JCE failed");
					}
					else {
						logger.debug("Bouncy Castle JCE Provider successfully inserted at position: "+provider2Pos);
					}
				}
			}
			
			GlobalContext.getGlobalContext().init();

			getConnectionManager().loadCommands();
			Properties cfg = getGlobalContext().getConfig().getProperties();

			/** listen for connections * */
			String bindip = null;
			ServerSocket server = null;
			boolean useIP;

			try {
				bindip = PropertyHelper.getProperty(cfg, "master.ip");
				if (bindip.equals(""))
					useIP = false;
				else
					useIP = true;
			} catch (NullPointerException e) {
				useIP = false;
			}

			if (useIP) {
				server = new ServerSocket();
				server.bind(new InetSocketAddress(bindip, Integer
						.parseInt(PropertyHelper
								.getProperty(cfg, "master.port"))));
				logger.info("Listening on " + server.getInetAddress() + ":"
						+ server.getLocalPort());
			} else {
				server = new ServerSocket(Integer.parseInt(PropertyHelper
						.getProperty(cfg, "master.port")));
				logger.info("Listening on port " + server.getLocalPort());
			}

			while (true) {
				getConnectionManager().start(server.accept());
			}

			// catches subclasses of Error and Exception
		} catch (Throwable th) {
			th.printStackTrace();
			logger.error("", th);
			System.exit(0);

			return;
		}
	}

	public FtpReply canLogin(BaseFtpConnection baseconn, User user) {
		int count = getGlobalContext().getConfig().getMaxUsersTotal();

		// Math.max if the integer wraps
		if (user.isExempt()) {
			count = Math.max(count, count
					+ getGlobalContext().getConfig().getMaxUsersExempt());
		}

		// not >= because baseconn is already included
		if (_conns.size() > count) {
			return new FtpReply(550, "The site is full, try again later.");
		}

		int userCount = 0;
		int ipCount = 0;

		synchronized (_conns) {
			for (Iterator iter = _conns.iterator(); iter.hasNext();) {
				BaseFtpConnection tempConnection = (BaseFtpConnection) iter
						.next();

				try {
					User tempUser = tempConnection.getUser();

					if (tempUser.getName().equals(user.getName())) {
						userCount++;

						if (tempConnection.getClientAddress().equals(
								baseconn.getClientAddress())) {
							ipCount++;
						}
					}
				} catch (NoSuchUserException ex) {
					// do nothing, we found our current connection, baseconn =
					// tempConnection
				}
			}
		}

		if (user.getKeyedMap().getObjectInt(UserManagement.MAXLOGINS) > 0) {
			if (user.getKeyedMap().getObjectInt(UserManagement.MAXLOGINS) <= userCount) {
				return new FtpReply(530, "Sorry, your account is restricted to "
						+ user.getKeyedMap().getObjectInt(
								UserManagement.MAXLOGINS)
						+ " simultaneous logins.");
			}
		}
		if (user.getKeyedMap().getObjectInt(UserManagement.MAXLOGINSIP) > 0) {
			if (user.getKeyedMap().getObjectInt(UserManagement.MAXLOGINSIP) <= ipCount) {
				return new FtpReply(530,
						"Sorry, your maximum number of connections from this IP ("
								+ user.getKeyedMap().getObjectInt(
										UserManagement.MAXLOGINSIP)
								+ ") has been reached.");
			}
		}

		if (user.getKeyedMap().getObjectDate(UserManagement.BAN_TIME).getTime() > System
				.currentTimeMillis()) {
			return new FtpReply(530, "Sorry you are banned until "
					+ user.getKeyedMap().getObjectDate(UserManagement.BAN_TIME)
					+ "! ("
					+ user.getKeyedMap().getObjectString(
							UserManagement.BAN_REASON) + ")");
		}

		if (!baseconn.isSecure()
				&& getGlobalContext().getConfig().checkPermission(
						"userrejectinsecure", user)) {
			return new FtpReply(530, "USE SECURE CONNECTION");
		} else if (baseconn.isSecure()
				&& getGlobalContext().getConfig().checkPermission(
						"userrejectsecure", user)) {
			return new FtpReply(530, "USE INSECURE CONNECTION");
		}

		return null; // everything passed
	}

	public void dispatchFtpEvent(Event event) {
		getGlobalContext().dispatchFtpEvent(event);
	}

	public CommandManagerInterface getCommandManager() {
		if (_commandManager == null) {
			PluginManager manager = PluginManager.lookup(this);
			ExtensionPoint cmExtPoint = 
				manager.getRegistry().getExtensionPoint( 
						"master", "CommandManager");
			
			/*	Iterate over all extensions that have been connected to the
				CommandManager extension point and return the desired one */
	
			Properties cfg = getGlobalContext().getConfig().getProperties();
	
			Class<?> cmCls = null;
	
			String desiredCm = PropertyHelper.getProperty(cfg, "master.commandmanager");

			for (Extension cm : cmExtPoint.getConnectedExtensions()) {
				try {
					if (cm.getDeclaringPluginDescriptor().getId().equals(desiredCm)) {
						// If plugin isn't already activated then activate it
						if (!manager.isPluginActivated(cm.getDeclaringPluginDescriptor())) {
							manager.activatePlugin(cm.getDeclaringPluginDescriptor().getId());
						}
						ClassLoader cmLoader = manager.getPluginClassLoader( 
								cm.getDeclaringPluginDescriptor());
						cmCls = cmLoader.loadClass( 
								cm.getParameter("class").valueAsString());
						_commandManager = (CommandManagerInterface) cmCls.newInstance();
						_commandManager.initialize(getCommands());
						return _commandManager;
					}
				}
				catch (Exception e) {
					throw new FatalException(
							"Cannot create instance of commandmanager, check master.commandmanager in config file",
							e);
				}
			}
		}
		return _commandManager;
	}

	/**
	 * returns a <code>Collection</code> of current connections
	 */
	public List<BaseFtpConnection> getConnections() {
		return _conns;
	}

	public static GlobalContext getGlobalContext() {
		return GlobalContext.getGlobalContext();
	}

	private void loadTimer() {
		/*
		 * TimerTask timerLogoutIdle = new TimerTask() { public void run() { try {
		 * timerLogoutIdle(); } catch (Throwable t) { logger.error("Error in
		 * timerLogoutIdle TimerTask", t); } } };
		 * 
		 * //run every 10 seconds
		 * getGlobalContext().getTimer().schedule(timerLogoutIdle, 10 * 1000, 10 *
		 * 1000);
		 */

		/*
		 * TimerTask timerSave = new TimerTask() { public void run() { try {
		 * getGlobalContext().getSlaveManager().saveFilelist();
		 * 
		 * try { getGlobalContext().getUserManager().saveAll(); } catch
		 * (UserFileException e) { logger.log(Level.FATAL, "Error saving all
		 * users", e); } } catch (Throwable t) { logger.error("Error in
		 * timerSave TimerTask", t); } } };
		 */
		/*
		 * TimerTask timerGarbageCollect = new TimerTask() { public void run() {
		 * logger.debug("Memory free before GC :" +
		 * Bytes.formatBytes(Runtime.getRuntime().freeMemory()) + "/" +
		 * Bytes.formatBytes(Runtime.getRuntime().totalMemory())); System.gc();
		 * logger.debug("Memory free after GC :" +
		 * Bytes.formatBytes(Runtime.getRuntime().freeMemory()) + "/" +
		 * Bytes.formatBytes(Runtime.getRuntime().totalMemory())); } };
		 */

		// run every hour
		// / getGlobalContext().getTimer().schedule(timerSave, 60 * 60 * 1000,
		// 60 * 60 * 1000);
		// run every minute
		// getGlobalContext().getTimer().schedule(timerGarbageCollect, 60 *
		// 1000, 60 * 1000);
	}

	public void remove(BaseFtpConnection conn) {
		if (!_conns.remove(conn)) {
			throw new RuntimeException("connections.remove() returned false.");
		}
	}

	public void shutdownPrivate(String message) {
		for (BaseFtpConnection conn : new ArrayList<BaseFtpConnection>(
				getConnections())) {
			conn.stop(message);
		}
	}

	public void start(Socket sock) throws IOException {
		if (getGlobalContext().isShutdown()) {
			new PrintWriter(sock.getOutputStream()).println("421 "
					+ getGlobalContext().getShutdownMessage());
			sock.close();
			return;
		}

		/*
		 * Reserved for Implicit SSL, TODO if(sock instanceof SSLSocket) {
		 * SSLSocket sslsock = (SSLSocket) sock;
		 * sslsock.setUseClientMode(false); sslsock.startHandshake(); sock =
		 * sslsock; }
		 */

		BaseFtpConnection conn = new BaseFtpConnection(sock);

		_conns.add(conn);
		conn.start();
	}

	/**
	 * Handles the load of the FTP Commands.
	 * Firstly, it checks if <code>conf/ftpcommands.conf</code> exists, if not it halts the daemon.
	 * After that it read the file and create a list of the existing commands.
	 */
	private void loadCommands() {
		_cmds = GlobalContext.loadCommandConfig(cmdConf);
	}

	/**
	 * The HashMap should look like this:<br><code>
	 * Key -> Value<br>
	 * "AUTH" -> Properties Object for AUTH<br>
	 * "LIST" -> Properties Object for LIST</code>
	 */
	public HashMap<String,Properties> getCommands() {
		return _cmds;
	}
}
