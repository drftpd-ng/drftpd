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
package net.sf.drftpd.master;

import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;

import net.sf.drftpd.FatalException;
import net.sf.drftpd.ObjectNotFoundException;
import net.sf.drftpd.event.Event;
import net.sf.drftpd.event.FtpListener;
import net.sf.drftpd.event.listeners.RaceStatistics;
import net.sf.drftpd.master.command.CommandManagerFactory;
import net.sf.drftpd.master.config.FtpConfig;
import net.sf.drftpd.master.usermanager.NoSuchUserException;
import net.sf.drftpd.master.usermanager.User;
import net.sf.drftpd.master.usermanager.UserFileException;
import net.sf.drftpd.mirroring.JobManager;
import net.sf.drftpd.remotefile.LinkedRemoteFile;
import net.sf.drftpd.remotefile.MLSTSerialize;
import net.sf.drftpd.slave.SlaveImpl;
import net.sf.drftpd.util.SafeFileWriter;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.drftpd.GlobalContext;
import org.drftpd.slave.socket.SocketSlaveManager;

/**
 * @version $Id: ConnectionManager.java,v 1.113 2004/07/12 20:37:25 mog Exp $
 */
public class ConnectionManager {

	public static final int idleTimeout = 300;

	private static final Logger logger =
		Logger.getLogger(ConnectionManager.class.getName());

 	public static void main(String args[]) {
		System.out.println(SlaveImpl.VERSION + " master server starting.");
		System.out.println("http://drftpd.org/");
		try {
			String cfgFileName;
			if (args.length >= 1) {
				cfgFileName = args[0];
			} else {
				cfgFileName = "drftpd.conf";
			}
			String slaveCfgFileName;
			if (args.length >= 2) {
				slaveCfgFileName = args[1];
			} else {
				slaveCfgFileName = "slave.conf";
			}

			/** load master config **/
			Properties cfg = new Properties();
			cfg.load(new FileInputStream(cfgFileName));

			/** load slave config **/
			Properties slaveCfg; //used as a flag for if localslave=true
			if (cfg
				.getProperty("master.localslave", "false")
				.equalsIgnoreCase("true")) {
				slaveCfg = new Properties();
				slaveCfg.load(new FileInputStream(slaveCfgFileName));
			} else {
				slaveCfg = null;
			}

			logger.info("Starting ConnectionManager");
			ConnectionManager mgr =
				new ConnectionManager(
					cfg,
					slaveCfg,
					cfgFileName,
					slaveCfgFileName);
			/** listen for connections **/
			ServerSocket server =
				new ServerSocket(
					Integer.parseInt(
						FtpConfig.getProperty(cfg, "master.port")));
			logger.info("Listening on port " + server.getLocalPort());
			while (true) {
				mgr.start(server.accept());
			}
			//catches subclasses of Error and Exception
		} catch (Throwable th) {
			logger.error("", th);
			System.exit(0);
			return;
		}
	}

	private static String[] scrubArgs(String[] args) {
		String ret[] = new String[args.length - 1];
		System.arraycopy(args, 1, ret, 0, ret.length);
		return ret;
	}
	private CommandManagerFactory _commandManagerFactory;
	private List _conns = Collections.synchronizedList(new ArrayList());

	protected GlobalContext _gctx;

	private Timer _timer;

	protected ConnectionManager() {
	}

	public ConnectionManager(
		Properties cfg,
		Properties slaveCfg,
		String cfgFileName,
		String slaveCfgFileName) {
		_gctx =
			new GlobalContext(
				cfg,
				slaveCfg,
				cfgFileName,
				slaveCfgFileName,
				this);
		_timer = new Timer();

		// start socket slave manager
		if (!cfg.getProperty("master.socketport", "").equals("")) {
			new SocketSlaveManager(this, cfg);
		}

		if (slaveCfg != null) {
			try {
				new SlaveImpl(slaveCfg);
			} catch (IOException ex) { // RemoteException extends IOException
				throw new FatalException(ex);
			}
		}

		_commandManagerFactory = new CommandManagerFactory(this);

		getGlobalContext().addFtpListener(new RaceStatistics());

		loadTimer();
		getGlobalContext().getSlaveManager().addShutdownHook();
	}

	public FtpReply canLogin(BaseFtpConnection baseconn, User user) {
		int count = getGlobalContext().getConfig().getMaxUsersTotal();
		//Math.max if the integer wraps
		if (user.isExempt())
			count = Math.max(count, count + getGlobalContext().getConfig().getMaxUsersExempt());
		int userCount = 0;
		int ipCount = 0;

		// not >= because baseconn is already included
		if (_conns.size() > count)
			return new FtpReply(550, "The site is full, try again later.");
		synchronized (_conns) {
			for (Iterator iter = _conns.iterator(); iter.hasNext();) {
				BaseFtpConnection tempConnection =
					(BaseFtpConnection) iter.next();
				try {
					User tempUser = tempConnection.getUser();
					if (tempUser.getUsername().equals(user.getUsername())) {
						userCount++;
						if (tempConnection
							.getClientAddress()
							.equals(baseconn.getClientAddress())) {
							ipCount++;
						}
					}
				} catch (NoSuchUserException ex) {
					// do nothing, we found our current connection, baseconn = tempConnection
				}
			}
		}
		if (user.getMaxLoginsPerIP() > 0 && ipCount > user.getMaxLoginsPerIP())
			return new FtpReply(
				530,
				"Sorry, your maximum number of connections from this IP ("
					+ user.getMaxLoginsPerIP()
					+ ") has been reached.");
		if (user.getMaxLogins() > 0 && userCount > user.getMaxLogins())
			return new FtpReply(
				530,
				"Sorry, your account is restricted to "
					+ user.getMaxLogins()
					+ " simultaneous logins.");
		if (!baseconn.isSecure()
			&& getGlobalContext().getConfig().checkUserRejectInsecure(user)) {
			return new FtpReply(530, "USE SECURE CONNECTION");
		} else if (
			baseconn.isSecure() && getGlobalContext().getConfig().checkUserRejectSecure(user)) {
			return new FtpReply(530, "USE INSECURE CONNECTION");
		}
		return null; // everything passed
	}

	public void dispatchFtpEvent(Event event) {
		getGlobalContext().dispatchFtpEvent(event);
	}

	public CommandManagerFactory getCommandManagerFactory() {
		return _commandManagerFactory;
	}

	/**
	 * returns a <code>Collection</code> of current connections
	 */
	public List getConnections() {
		return _conns;
	}
	public FtpListener getFtpListener(Class clazz)
		throws ObjectNotFoundException {
		for (Iterator iter = getFtpListeners().iterator(); iter.hasNext();) {
			FtpListener listener = (FtpListener) iter.next();
			if (clazz.isInstance(listener))
				return listener;
		}
		throw new ObjectNotFoundException();
	}

	public List getFtpListeners() {
		return getGlobalContext().getFtpListeners();
	}

	public GlobalContext getGlobalContext() {
		if(_gctx == null) throw new NullPointerException();
		return _gctx;
	}

	public JobManager getJobManager() {
		return getGlobalContext().getJobManager();
	}

	public Timer getTimer() {
		return _timer;
	}

	private void loadTimer() {
		TimerTask timerLogoutIdle = new TimerTask() {
			public void run() {
				timerLogoutIdle();
			}
		};
		//run every 10 seconds
		_timer.schedule(timerLogoutIdle, 10 * 1000, 10 * 1000);

		TimerTask timerSave = new TimerTask() {
			public void run() {
				getGlobalContext().getSlaveManager().saveFilelist();
				try {
					getGlobalContext().getUserManager().saveAll();
				} catch (UserFileException e) {
					logger.log(Level.FATAL, "Error saving all users", e);
				}
			}
		};
		//run every hour 
		_timer.schedule(timerSave, 60 * 60 * 1000, 60 * 60 * 1000);
	}

	public void reload() {
		//		String url = System.getProperty(LogManager.DEFAULT_CONFIGURATION_KEY);
		//		if(url != null) {
		//			LogManager.resetConfiguration();
		//			OptionConverter.selectAndConfigure(url, null, LogManager.getLoggerRepository());
		//		}
		getGlobalContext().getSectionManager().reload();
	}

	public void remove(BaseFtpConnection conn) {
		if (!_conns.remove(conn)) {
			throw new RuntimeException("connections.remove() returned false.");
		}
		if (getGlobalContext().isShutdown() && _conns.isEmpty()) {
			//			_slaveManager.saveFilelist();
			//			try {
			//				getUserManager().saveAll();
			//			} catch (UserFileException e) {
			//				logger.log(Level.WARN, "Failed to save all userfiles", e);
			//			}
			logger.info("Shutdown complete, exiting");
			System.exit(0);
		}
	}
	public void saveFilelist() {
		try {
			SafeFileWriter out = new SafeFileWriter("files.mlst");
			try {
				MLSTSerialize.serialize(getGlobalContext().getRoot(), out);
			} finally {
				out.close();
			}
		} catch (IOException e) {
			logger.warn("Error saving files.mlst", e);
		}
	}

	/**
	 * TODO move closing connections to GlobalContext.
	 * 
	 * @see org.drftpd.GlobalContext#shutdown(String)
	 */
	public void shutdown(String message) {
		getGlobalContext().shutdown(message);
		ArrayList conns = new ArrayList(getConnections());
		for (Iterator iter = conns.iterator(); iter.hasNext();) {
			((BaseFtpConnection) iter.next()).stop(message);
		}
	}
	public void start(Socket sock) throws IOException {
		if (getGlobalContext().isShutdown()) {
			new PrintWriter(sock.getOutputStream()).println(
				"421 " + getGlobalContext().getShutdownMessage());
			sock.close();
			return;
		}
		BaseFtpConnection conn = new BaseFtpConnection(this, sock);
		_conns.add(conn);
		conn.start();
	}

	public void timerLogoutIdle() {
		long currTime = System.currentTimeMillis();
		ArrayList conns = new ArrayList(_conns);
		for (Iterator i = conns.iterator(); i.hasNext();) {
			BaseFtpConnection conn = (BaseFtpConnection) i.next();

			int idle = (int) ((currTime - conn.getLastActive()) / 1000);
			int maxIdleTime;
			try {
				maxIdleTime = conn.getUser().getIdleTime();
				if (maxIdleTime == 0)
					maxIdleTime = idleTimeout;
			} catch (NoSuchUserException e) {
				maxIdleTime = idleTimeout;
			}

			if (!conn.isExecuting() && idle >= maxIdleTime) {
				// idle time expired, logout user.
				conn.stop("Idle time expired: " + maxIdleTime + "s");
			}
		}
	}

	public void setGlobalContext(GlobalContext gctx) {
		_gctx = gctx;
	}
}
