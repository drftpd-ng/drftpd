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
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import java.util.Vector;
import java.util.concurrent.Executors;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;
import org.bushe.swing.event.EventSubscriber;
import org.drftpd.GlobalContext;
import org.drftpd.PropertyHelper;
import org.drftpd.commandmanager.CommandManagerInterface;
import org.drftpd.commands.UserManagement;
import org.drftpd.event.ReloadEvent;
import org.drftpd.slave.Slave;
import org.drftpd.usermanager.NoSuchUserException;
import org.drftpd.usermanager.User;

/**
 * @version $Id$
 */
public class ConnectionManager implements EventSubscriber {
	private static final Logger logger = Logger
			.getLogger(ConnectionManager.class.getName());

	private static final String cmdConf = "conf/ftpcommands.conf";

	private static final String themeDir = "conf/themes/ftp";

	private static ConnectionManager _connectionManager = null;

	private HashMap<String,Properties> _cmds;

	private CommandManagerInterface _commandManager = null;

	private List<BaseFtpConnection> _conns = new Vector<BaseFtpConnection>();
	
	private ThreadPoolExecutor _pool;

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
		GlobalContext.getEventService().subscribe(ReloadEvent.class, this);
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
			Properties cfg = GlobalContext.getConfig().getMainProperties();

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
			
			getConnectionManager().createThreadPool();
			
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
	
	public void createThreadPool() {
		int maxUserConnected = GlobalContext.getConfig().getMaxUsersTotal();
		int maxAliveThreads = maxUserConnected + GlobalContext.getConfig().getMaxUsersExempt();
		int minAliveThreads = (int) Math.round(maxAliveThreads * 0.25);
		
		_pool = new ThreadPoolExecutor(minAliveThreads, maxAliveThreads, 3*60, TimeUnit.SECONDS, 
				new SynchronousQueue<Runnable>(), new ConnectionThreadFactory(), new ThreadPoolExecutor.DiscardPolicy());
		
		// that's java1.6, can't be used for now.
		// _pool.allowCoreThreadTimeOut(false);
		
		// _pool.prestartAllCoreThreads();
	}
	
	public void dumpThreadPool() {
		logger.debug("Active threads: "+_pool.getActiveCount()+" / Completed Tasks: "+ _pool.getCompletedTaskCount());
		logger.debug("Pool information - Min # of threads: "+_pool.getCorePoolSize()+" / Max: "+ _pool.getMaximumPoolSize());
		logger.debug("Current # of threads: " + _pool.getPoolSize());
	}

	public FtpReply canLogin(BaseFtpConnection baseconn, User user) {
		int count = GlobalContext.getConfig().getMaxUsersTotal();

		// Math.max if the integer wraps
		if (user.isExempt()) {
			count = Math.max(count, count + GlobalContext.getConfig().getMaxUsersExempt());
		}

		// not >= because baseconn is already included
		if (_conns.size() > count) {
			return new FtpReply(550, "The site is full, try again later.");
		}

		int userCount = 0;
		int ipCount = 0;

		synchronized (_conns) {
			for (BaseFtpConnection tempConnection : _conns) {
				try {
					User tempUser = tempConnection.getUser();

					if (tempUser.getName().equals(user.getName())) {
						userCount++;
						if (tempConnection.getClientAddress().equals(baseconn.getClientAddress())) {
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
				&& GlobalContext.getConfig().checkPermission(
						"userrejectinsecure", user)) {
			return new FtpReply(530, "USE SECURE CONNECTION");
		} else if (baseconn.isSecure()
				&& GlobalContext.getConfig().checkPermission(
						"userrejectsecure", user)) {
			return new FtpReply(530, "USE INSECURE CONNECTION");
		}

		return null; // everything passed
	}

	public CommandManagerInterface getCommandManager() {
		if (_commandManager == null) {
			_commandManager = getGlobalContext().getCommandManager();
			if (_commandManager != null) {
				_commandManager.initialize(getCommands(), themeDir);
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
		_pool.execute(conn);
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

	public void onEvent(Object event) {
		if (event instanceof ReloadEvent) {
			logger.info("Reloading "+ cmdConf +", origin "+((ReloadEvent)event).getOrigin());
			loadCommands();
			_commandManager.initialize(getCommands(), themeDir);
			for (BaseFtpConnection conn : new ArrayList<BaseFtpConnection>(getConnections())) {
				conn.setCommands(getCommands());
			}
		}
	}
}

class ConnectionThreadFactory implements ThreadFactory {
	public static String getIdleThreadName(long threadId) {
		return "FtpConnection Handler-"+ threadId + " - Waiting for connections";
	}
	
	public Thread newThread(Runnable r) {
		Thread t = Executors.defaultThreadFactory().newThread(r);
		t.setName(getIdleThreadName(t.getId()));
		return t;
	}	
}
