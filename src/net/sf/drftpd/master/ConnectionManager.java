package net.sf.drftpd.master;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;

import net.sf.drftpd.FatalException;
import net.sf.drftpd.event.Event;
import net.sf.drftpd.event.FtpListener;
import net.sf.drftpd.event.MessageEvent;
import net.sf.drftpd.event.NukeEvent;
import net.sf.drftpd.event.XferLogListener;
import net.sf.drftpd.event.irc.IRCListener;
import net.sf.drftpd.master.command.CommandManagerFactory;
import net.sf.drftpd.master.config.FtpConfig;
import net.sf.drftpd.master.queues.NukeLog;
import net.sf.drftpd.master.usermanager.NoSuchUserException;
import net.sf.drftpd.master.usermanager.User;
import net.sf.drftpd.master.usermanager.UserFileException;
import net.sf.drftpd.master.usermanager.UserManager;
import net.sf.drftpd.permission.GlobRMIServerSocketFactory;
import net.sf.drftpd.slave.SlaveImpl;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.input.SAXBuilder;

public class ConnectionManager {
	public static final int idleTimeout = 300;

	private static Logger logger =
		Logger.getLogger(ConnectionManager.class.getName());

	public static void main(String args[]) {
		BasicConfigurator.configure();
//		Logger root = Logger.getRootLogger();
//		new File("ftp-data/logs").mkdirs();
//		try {
//			root.addAppender(
//				new FileAppender(
//					new PatternLayout(PatternLayout.TTCC_CONVERSION_PATTERN),
//					"ftp-data/logs/drftpd.log"));
//		} catch (IOException e1) {
//			throw new FatalException(e1);
//		}
		System.out.println(SlaveImpl.VERSION + " master server starting.");
		System.out.println("http://drftpd.sourceforge.net");

		System.setProperty("line.separator", "\r\n");
		try {
			String cfgFileName;
			if (args.length >= 1) {
				cfgFileName = args[0];
			} else {
				cfgFileName = "drftpd.conf";
			}

			/** load config **/
			Properties cfg = new Properties();
			cfg.load(new FileInputStream(cfgFileName));

			logger.info("Starting ConnectionManager");
			ConnectionManager mgr = new ConnectionManager(cfg, cfgFileName);
			/** listen for connections **/
			ServerSocket server =
				new ServerSocket(
					Integer.parseInt(cfg.getProperty("master.port")));
			logger.info("Listening on port " + server.getLocalPort());
			while (true) {
				mgr.start(server.accept());
			}
		} catch (Exception th) {
			logger.error("", th);
			System.exit(0);
			return;
		}
	}
	private FtpConfig _config;

	private Vector _conns = new Vector();

	private ArrayList _ftpListeners = new ArrayList();
	private NukeLog _nukelog;
	private String shutdownMessage = null;
	//allow package classes for inner classes without use of synthetic methods
	private SlaveManagerImpl slaveManager;
	private Timer timer;
	private UserManager usermanager;
	public ConnectionManager(Properties cfg, String cfgFileName) {
		try {
			this._config = new FtpConfig(cfg, cfgFileName, this);
		} catch (Throwable ex) {
			throw new FatalException(ex);
		}

		/** END: load XML file database **/

		List rslaves = SlaveManagerImpl.loadRSlaves();
		GlobRMIServerSocketFactory ssf =
			new GlobRMIServerSocketFactory(rslaves);
		/** register slavemanager **/
		try {
			slaveManager = new SlaveManagerImpl(cfg, rslaves, ssf, this);
		} catch (RemoteException e) {
			throw new FatalException(e);
		}

		if (cfg
			.getProperty("master.localslave", "false")
			.equalsIgnoreCase("true")) {
			try {
				new SlaveImpl(cfg);
			} catch (RemoteException ex) {
				throw new FatalException(ex);
			}
		}

		try {
			usermanager =
				(UserManager) Class
					.forName(cfg.getProperty("master.usermanager"))
					.newInstance();
		} catch (Exception e) {
			throw new FatalException(
				"Cannot create instance of usermanager, check master.usermanager in drftpd-0.7.conf",
				e);
		}

		_nukelog = new NukeLog();
		try {
			Document doc =
				new SAXBuilder().build(new FileReader("nukelog.xml"));
			List nukes = doc.getRootElement().getChildren();
			for (Iterator iter = nukes.iterator(); iter.hasNext();) {
				Element nukeElement = (Element) iter.next();

				User user =
					usermanager.getUserByName(nukeElement.getChildText("user"));
				String directory = nukeElement.getChildText("path");
				long time = Long.parseLong(nukeElement.getChildText("time"));
				int multiplier =
					Integer.parseInt(nukeElement.getChildText("multiplier"));
				String reason = nukeElement.getChildText("reason");

				long size = Long.parseLong(nukeElement.getChildText("size"));
				long nukedAmount =
					Long.parseLong(nukeElement.getChildText("nukedAmount"));

				Map nukees = new Hashtable();
				List nukeesElement =
					nukeElement.getChild("nukees").getChildren("nukee");
				for (Iterator iterator = nukeesElement.iterator();
					iterator.hasNext();
					) {
					Element nukeeElement = (Element) iterator.next();
					String nukeeUsername =
						nukeeElement.getChildText("username");
					Long nukeeAmount =
						new Long(nukeeElement.getChildText("amount"));

					nukees.put(nukeeUsername, nukeeAmount);
				}
				_nukelog.add(
					new NukeEvent(
						user,
						"NUKE",
						directory,
						time,
						size,
						nukedAmount,
						multiplier,
						reason,
						nukees));
			}
		} catch (FileNotFoundException ex) {
			logger.log(
				Level.DEBUG,
				"nukelog.xml not found, will create it after first nuke.");
		} catch (Exception ex) {
			logger.log(
				Level.INFO,
				"Error loading nukelog from nukelog.xml",
				ex);
		}

		if (cfg.getProperty("irc.enabled", "false").equals("true")) {
			try {
				addFtpListener(
					new IRCListener(this, getConfig(), new String[0]));
			} catch (Exception e2) {
				throw new FatalException(e2);
			}
		}

		addFtpListener(new XferLogListener());

		timer = new Timer();
		TimerTask timerLogoutIdle = new TimerTask() {
			public void run() {
				timerLogoutIdle();
			}
		};
		//run every 10 seconds
		timer.schedule(timerLogoutIdle, 10 * 1000, 10 * 1000);

		TimerTask timerSave = new TimerTask() {
			public void run() {
				getSlaveManager().saveFilesXML();
				try {
					getUsermanager().saveAll();
				} catch (UserFileException e) {
					logger.log(Level.FATAL, "Error saving all users", e);
				}
			}
		};
		//run every hour 
		timer.schedule(timerSave, 60 * 60 * 1000, 60 * 60 * 1000);

	}
	public void addFtpListener(FtpListener listener) {
		_ftpListeners.add(listener);
	}
	public void dispatchFtpEvent(Event event) {
		for (Iterator iter = _ftpListeners.iterator(); iter.hasNext();) {
			try {
				FtpListener handler = (FtpListener) iter.next();
				handler.actionPerformed(event);
			} catch (RuntimeException t) {
				logger.log(Level.WARN, "Exception dispatching event", t);
			}
		}
	}

	public FtpConfig getConfig() {
		return _config;
	}

	/**
	 * returns a <code>Collection</code> of current connections
	 */
	public Collection getConnections() {
		return _conns;
	}
	/**
	 * @return
	 */
	public List getFtpListeners() {
		return _ftpListeners;
	}
	/**
	 * @return
	 */
	public NukeLog getNukeLog() {
		return _nukelog;
	}
	public String getShutdownMessage() {
		return this.shutdownMessage;
	}

	/**
	 * @return
	 */
	public SlaveManagerImpl getSlaveManager() {
		return slaveManager;
	}

	/**
	 * @return
	 */
	public UserManager getUsermanager() {
		return usermanager;
	}
	public boolean isShutdown() {
		return this.shutdownMessage != null;
	}

	public void remove(BaseFtpConnection conn) {
		if (!_conns.remove(conn)) {
			throw new RuntimeException("connections.remove() returned false.");
		}
		if (isShutdown() && _conns.isEmpty()) {
			slaveManager.saveFilesXML();
			try {
				getUsermanager().saveAll();
			} catch (UserFileException e) {
				logger.log(Level.WARN, "Failed to save all userfiles", e);
			}
			System.exit(0);
		}
	}
	public void shutdown(String message) {
		this.shutdownMessage = message;
		Collection conns = getConnections();
		synchronized (conns) {
			for (Iterator iter = getConnections().iterator();
				iter.hasNext();
				) {
				((BaseFtpConnection) iter.next()).stop(message);
			}
		}
		dispatchFtpEvent(new MessageEvent("SHUTDOWN", message));
	}
	private CommandManagerFactory commandManagerFactory = new CommandManagerFactory();
	public void start(Socket sock) throws IOException {
		BaseFtpConnection conn = new BaseFtpConnection(this, sock);
		_conns.add(conn);
		conn.start();
	}

	public void timerLogoutIdle() {
		long currTime = System.currentTimeMillis();
		synchronized (_conns) {
			for (Iterator i = _conns.iterator(); i.hasNext();) {
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

	/**
	 * @return
	 */
	public CommandManagerFactory getCommandManagerFactory() {
		return commandManagerFactory;
	}

}
