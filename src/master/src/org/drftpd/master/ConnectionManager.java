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

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.bushe.swing.event.annotation.EventSubscriber;
import org.drftpd.GlobalContext;
import org.drftpd.PropertyHelper;
import org.drftpd.commandmanager.CommandManagerInterface;
import org.drftpd.commands.UserManagement;
import org.drftpd.event.ReloadEvent;
import org.drftpd.usermanager.NoSuchUserException;
import org.drftpd.usermanager.User;
import org.tanukisoftware.wrapper.WrapperManager;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.*;

/**
 * @version $Id$
 */
public class ConnectionManager {
	private static final Logger logger = LogManager.getLogger(ConnectionManager.class.getName());

	private static final String cmdConf = "conf/ftpcommands.conf";

	private static final String themeDir = "conf/themes/ftp";

	private static ConnectionManager _connectionManager = null;

	private HashMap<String,Properties> _cmds;

	private CommandManagerInterface _commandManager = null;

	private List<BaseFtpConnection> _conns = new Vector<>();

	private ThreadPoolExecutor _pool;
	
	private static String _bindIP;

	/**
	 * If you're creating a ConnectionManager object and it's not part of a TestCase
	 * you're not doing it correctly, ConnectionManager is a Singleton
	 *
	 */
	protected ConnectionManager() {
		getGlobalContext().getSlaveManager().addShutdownHook();
		// Subscribe to events
		AnnotationProcessor.process(this);
    }

	public static ConnectionManager getConnectionManager() {
		if (_connectionManager == null) {
			_connectionManager = new ConnectionManager();
		}
		return _connectionManager;
	}
	
	public static String getBindIP() {
		return _bindIP;
	}

	public static void boot() {
		System.out.println(GlobalContext.VERSION + " Master starting.");
		System.out.println("https://github.com/drftpd-ng/drftpd3");
		System.out.println("Further logging will be done using (mostly) log4j, check logs/");
		// Set current thread name to make it clear in logfiles what is coming from the main master process 
		// instead of being named after the wrapper
		Thread.currentThread().setName("Master Main Thread");

		try {
			logger.info("Starting ConnectionManager");

			GlobalContext.getGlobalContext().init();

			getConnectionManager().loadCommands();
			Properties cfg = GlobalContext.getConfig().getMainProperties();
			/** initialise command manager before accepting connections **/
			getConnectionManager().initCommandManager();

			/** listen for connections * */
			String bindip = null;
			ServerSocket server = null;
			boolean useIP;

			try {
				bindip = PropertyHelper.getProperty(cfg, "master.ip");
                useIP = !bindip.equals("");
			} catch (NullPointerException e) {
				useIP = false;
			}

			if (useIP) {
				server = new ServerSocket();
				server.bind(new InetSocketAddress(bindip, Integer
						.parseInt(PropertyHelper
								.getProperty(cfg, "master.port"))));
				_bindIP = bindip;
                logger.info("Listening on {}:{}", server.getInetAddress(), server.getLocalPort());
			} else {
				server = new ServerSocket(Integer.parseInt(PropertyHelper
						.getProperty(cfg, "master.port")));
                logger.info("Listening on port {}", server.getLocalPort());
			}

			getConnectionManager().createThreadPool();

			while (true) {		
				getConnectionManager().start(server.accept());
			}

			// catches subclasses of Error and Exception
		} catch (Throwable th) {
			th.printStackTrace();
			logger.error("", th);
			WrapperManager.stop(0);

		}
	}

	public void createThreadPool() {
		int maxUserConnected = GlobalContext.getConfig().getMaxUsersTotal();
		int maxAliveThreads = maxUserConnected + GlobalContext.getConfig().getMaxUsersExempt();
		int minAliveThreads = (int) Math.round(maxAliveThreads * 0.25);

		_pool = new ThreadPoolExecutor(minAliveThreads, maxAliveThreads, 3*60, TimeUnit.SECONDS,
                new SynchronousQueue<>(), new ConnectionThreadFactory(), new ThreadPoolExecutor.AbortPolicy());
		_pool.allowCoreThreadTimeOut(false);
		_pool.prestartAllCoreThreads();
	}

	public void dumpThreadPool() {
        logger.debug("Active threads: {} / Completed Tasks: {}", _pool.getActiveCount(), _pool.getCompletedTaskCount());
        logger.debug("Pool information - Min # of threads: {} / Max: {}", _pool.getCorePoolSize(), _pool.getMaximumPoolSize());
        logger.debug("Current # of threads: {}", _pool.getPoolSize());
	}

	public FtpReply canLogin(BaseFtpConnection baseconn, User user) {
		int count = GlobalContext.getConfig().getMaxUsersTotal();

		// Math.max if the integer wraps
	        if(GlobalContext.getConfig().isLoginExempt(user)) {
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

		int maxLogins = user.getKeyedMap().getObjectInteger(UserManagement.MAXLOGINS);
		if (maxLogins > 0) {
			if (maxLogins <= userCount) {
				return new FtpReply(530, "Sorry, your account is restricted to "
						+ maxLogins + " simultaneous logins.");
			}
		}
		
		int maxLoginsIP = user.getKeyedMap().getObjectInteger(UserManagement.MAXLOGINSIP); 
		if (maxLoginsIP > 0) {
			if (maxLoginsIP <= ipCount) {
				return new FtpReply(530, "Sorry, your maximum number of connections from this IP ("
						+ maxLoginsIP	+ ") has been reached.");
			}
		}

		Date banTime = user.getKeyedMap().getObject(UserManagement.BAN_TIME, new Date());
		if (banTime.getTime() > System.currentTimeMillis()) {
			return new FtpReply(530, "Sorry you are banned until "
					+ banTime + "! ("+ user.getKeyedMap().getObjectString(UserManagement.BAN_REASON) + ")");
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

	private void initCommandManager() {
		if (_commandManager == null) {
			_commandManager = getGlobalContext().getCommandManager();
			if (_commandManager != null) {
				_commandManager.initialize(getCommands(), themeDir);
			}
		}
	}

	public CommandManagerInterface getCommandManager() {
		return _commandManager;
	}

	/**
	 * returns a <code>Collection</code> of current connections
	 */
	public List<BaseFtpConnection> getConnections() {
		return new ArrayList<>(_conns);
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
		for (BaseFtpConnection conn : getConnections()) {
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
		try {
			_pool.execute(conn);
		} catch (RejectedExecutionException e) {
			conn.printOutput(new FtpReply(421, "Connection closing"));
			conn.shutdownSocket();
			_conns.remove(conn);
		}
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

	@EventSubscriber
	public void onReloadEvent(ReloadEvent event) {
        logger.info("Reloading " + cmdConf + ", origin {}", event.getOrigin());
		loadCommands();
		_commandManager.initialize(getCommands(), themeDir);
		for (BaseFtpConnection conn : getConnections()) {
			conn.setCommands(getCommands());
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
