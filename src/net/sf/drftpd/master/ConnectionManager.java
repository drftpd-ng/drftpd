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
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Constructor;
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
import net.sf.drftpd.event.MessageEvent;
import net.sf.drftpd.event.listeners.RaceStatistics;
import net.sf.drftpd.master.command.CommandManagerFactory;
import net.sf.drftpd.master.config.FtpConfig;
import net.sf.drftpd.master.usermanager.NoSuchUserException;
import net.sf.drftpd.master.usermanager.User;
import net.sf.drftpd.master.usermanager.UserFileException;
import net.sf.drftpd.master.usermanager.UserManager;
import net.sf.drftpd.mirroring.JobManager;
import net.sf.drftpd.permission.GlobRMIServerSocketFactory;
import net.sf.drftpd.remotefile.LinkedRemoteFile;
import net.sf.drftpd.remotefile.MLSTSerialize;
import net.sf.drftpd.slave.SlaveImpl;
import net.sf.drftpd.util.SafeFileWriter;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.drftpd.sections.SectionManagerInterface;
import org.drftpd.slave.socket.SocketSlaveManager;

/**
 * @version $Id: ConnectionManager.java,v 1.110 2004/06/01 17:16:49 mog Exp $
 */
public class ConnectionManager {

	public static final int idleTimeout = 300;

	private static final Logger logger =
		Logger.getLogger(ConnectionManager.class.getName());

	public static LinkedRemoteFile loadMLSTFileDatabase(
		List rslaves,
		ConnectionManager cm)
		throws IOException {
		return MLSTSerialize.unserialize(
			cm != null ? cm.getConfig() : null,
			new FileReader("files.mlst"),
			rslaves);
	}

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
	private FtpConfig _config;
	private List _conns = Collections.synchronizedList(new ArrayList());

	private ArrayList _ftpListeners = new ArrayList();
	private JobManager _jm;

	protected LinkedRemoteFile _root;
	private SectionManagerInterface _sections;
	private String _shutdownMessage = null;
	//allow package classes for inner classes without use of synthetic methods
	private SlaveManagerImpl _slaveManager;
	private Timer _timer;
	private UserManager _usermanager;

	protected ConnectionManager() {
	}

	public ConnectionManager(
		Properties cfg,
		Properties slaveCfg,
		String cfgFileName,
		String slaveCfgFileName) {
		try {
			_config = new FtpConfig(cfg, cfgFileName, this);
		} catch (Throwable ex) {
			throw new FatalException(ex);
		}
		_timer = new Timer();

		loadSlaveManager(cfg, cfgFileName);

		loadRSlaves();

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

		loadUserManager(cfg, cfgFileName);

		_commandManagerFactory = new CommandManagerFactory(this);

		addFtpListener(new RaceStatistics());

		loadSectionManager(cfg);

		loadPlugins(cfg);

		try { // only need to reload for SlaveSelection using JobManager settings
			getSlaveManager().getSlaveSelectionManager().reload();
		} catch (IOException e1) {
			throw new FatalException(e1);
		}

		loadTimer();
		getSlaveManager().addShutdownHook();
	}

	private void loadRSlaves() {
		try {
			List rslaves1 = _slaveManager.getSlaveList();
			_root = ConnectionManager.loadMLSTFileDatabase(rslaves1, this);
		} catch (FileNotFoundException e) {
			logger.info("files.mlst not found, creating a new filelist", e);
			_root = new LinkedRemoteFile(getConfig());
			//saveFilelist();
		} catch (IOException e) {
			throw new FatalException(e);
		}
	}

	/**
	 * @param cfg
	 */
	private void loadSlaveManager(Properties cfg, String cfgFileName) {
		/** register slavemanager **/
		try {
			String smclass = null;
			try {
				smclass = FtpConfig.getProperty(cfg, "master.slavemanager");
			} catch (Exception ex) {
			}
			if (smclass == null)
				smclass = "net.sf.drftpd.master.SlaveManagerImpl";
			_slaveManager =
				(SlaveManagerImpl) Class.forName(smclass).newInstance();
			List rslaves = _slaveManager.loadSlaves();
			GlobRMIServerSocketFactory ssf =
				new GlobRMIServerSocketFactory(rslaves);
			_slaveManager.init(cfg, rslaves, ssf, this);
		} catch (Exception e) {
			logger.log(Level.WARN, "Exception instancing SlaveManager", e);
			throw new FatalException(
				"Cannot create instance of slavemanager, check master.slavemanager in "
					+ cfgFileName,
				e);
		}
	}

	/**
	 * Calls init(this) on the argument
	 */
	public void addFtpListener(FtpListener listener) {
		listener.init(this);
		_ftpListeners.add(listener);
	}

	public FtpReply canLogin(BaseFtpConnection baseconn, User user) {
		int count = getConfig().getMaxUsersTotal();
		//Math.max if the integer wraps
		if (user.isExempt())
			count = Math.max(count, count + getConfig().getMaxUsersExempt());
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
			&& getConfig().checkUserRejectInsecure(user)) {
			return new FtpReply(530, "USE SECURE CONNECTION");
		} else if (
			baseconn.isSecure() && getConfig().checkUserRejectSecure(user)) {
			return new FtpReply(530, "USE INSECURE CONNECTION");
		}
		return null; // everything passed
	}

	public void dispatchFtpEvent(Event event) {
		logger.debug("Dispatching " + event+" to "+getFtpListeners());
		for (Iterator iter = getFtpListeners().iterator(); iter.hasNext();) {
			try {
				FtpListener handler = (FtpListener) iter.next();
				handler.actionPerformed(event);
			} catch (RuntimeException e) {
				logger.warn("RuntimeException dispatching event", e);
			}
		}
	}

	public CommandManagerFactory getCommandManagerFactory() {
		return _commandManagerFactory;
	}

	public FtpConfig getConfig() {
		return _config;
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
		return _ftpListeners;
	}

	public JobManager getJobManager() {
		if (_jm == null)
			throw new IllegalStateException("JobManager not loaded");
		return _jm;
	}

	public LinkedRemoteFile getRoot() {
		return _root;
	}

	public SectionManagerInterface getSectionManager() {
		return _sections;
	}
	public String getShutdownMessage() {
		return _shutdownMessage;
	}

	public SlaveManagerImpl getSlaveManager() {
		return _slaveManager;
	}

	public Timer getTimer() {
		return _timer;
	}

	public UserManager getUserManager() {
		return _usermanager;
	}

	public boolean isJobManagerLoaded() {
		return (_jm != null);
	}
	public boolean isShutdown() {
		return _shutdownMessage != null;
	}

	public void loadJobManager() {
		if (_jm != null)
			return; // already loaded
		try {
			_jm = new JobManager(this);
			_jm.startJobs();
		} catch (IOException e) {
			throw new FatalException("Error loading JobManager", e);
		}
	}

	protected void loadPlugins(Properties cfg) {
		for (int i = 1;; i++) {
			String classname = cfg.getProperty("plugins." + i);
			if (classname == null)
				break;
			try {
				FtpListener ftpListener =
					(FtpListener) Class.forName(classname).newInstance();
				addFtpListener(ftpListener);
			} catch (Exception e) {
				throw new FatalException("Error loading plugins", e);
			}
		}
	}

	private void loadSectionManager(Properties cfg) {
		try {
			Class cl =
				Class.forName(
					cfg.getProperty(
						"sectionmanager",
						"org.drftpd.sections.def.SectionManager"));
			Constructor c =
				cl.getConstructor(new Class[] { ConnectionManager.class });
			_sections =
				(SectionManagerInterface) c.newInstance(new Object[] { this });
		} catch (Exception e) {
			throw new FatalException(e);
		}
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
				getSlaveManager().saveFilelist();
				try {
					getUserManager().saveAll();
				} catch (UserFileException e) {
					logger.log(Level.FATAL, "Error saving all users", e);
				}
			}
		};
		//run every hour 
		_timer.schedule(timerSave, 60 * 60 * 1000, 60 * 60 * 1000);
	}

	/**
	 * @param cfg
	 * @param cfgFileName Can be null, Used in error message
	 */
	protected void loadUserManager(Properties cfg, String cfgFileName) {
		try {
			_usermanager =
				(UserManager) Class
					.forName(FtpConfig.getProperty(cfg, "master.usermanager"))
					.newInstance();
			// if the below method is not run, JSXUserManager fails when trying to do a reset() on the user logging in
			_usermanager.init(this);
		} catch (Exception e) {
			throw new FatalException(
				"Cannot create instance of usermanager, check master.usermanager in "
					+ cfgFileName,
				e);
		}
	}

	public void reload() {
		//		String url = System.getProperty(LogManager.DEFAULT_CONFIGURATION_KEY);
		//		if(url != null) {
		//			LogManager.resetConfiguration();
		//			OptionConverter.selectAndConfigure(url, null, LogManager.getLoggerRepository());
		//		}
		getSectionManager().reload();
	}

	public void remove(BaseFtpConnection conn) {
		if (!_conns.remove(conn)) {
			throw new RuntimeException("connections.remove() returned false.");
		}
		if (isShutdown() && _conns.isEmpty()) {
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
				MLSTSerialize.serialize(getRoot(), out);
			} finally {
				out.close();
			}
		} catch (IOException e) {
			logger.warn("Error saving files.mlst", e);
		}
	}
	public void shutdown(String message) {
		_shutdownMessage = message;
		ArrayList conns = new ArrayList(getConnections());
		for (Iterator iter = conns.iterator(); iter.hasNext();) {
			((BaseFtpConnection) iter.next()).stop(message);
		}
		dispatchFtpEvent(new MessageEvent("SHUTDOWN", message));
	}
	public void start(Socket sock) throws IOException {
		if (isShutdown()) {
			new PrintWriter(sock.getOutputStream()).println(
				"421 " + getShutdownMessage());
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
}
