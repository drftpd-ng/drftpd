package net.sf.drftpd.master;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.net.BindException;
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
	private Vector connections = new Vector();
	private UserManager usermanager;
	private NukeLog nukelog;
	//allow package classes for inner classes without use of synthetic methods
	private SlaveManagerImpl slavemanager;
	private String shutdownMessage = null;
	private Timer timer;

	private FtpConfig config;

	private static Logger logger =
		Logger.getLogger(ConnectionManager.class.getName());
	static {
		logger.setLevel(Level.ALL);
	}
	private Writer commandDebug;
	protected void dispatchFtpEvent(Event event) {
		System.out.println("Dispatching "+event.getCommand()+" to "+ftpListeners);
		for (Iterator iter = ftpListeners.iterator(); iter.hasNext();) {
			try {
				FtpListener handler = (FtpListener) iter.next();
				handler.actionPerformed(event);
			} catch (Throwable t) {
				logger.log(Level.WARN, "Exception dispatching event", t);
			}
		}
	}
	private Properties propertiesConfig;
	public ConnectionManager(Properties cfg, String cfgFileName) {
		this.propertiesConfig = cfg;
		try {
			this.config = new FtpConfig(cfg, cfgFileName, this);
		} catch (Throwable ex) {
			throw new FatalException(ex);
		}
		new File("ftp-data/logs").mkdirs();

		try {
			this.commandDebug = new FileWriter("ftp-data/logs/debug.log");
		} catch (IOException e1) {
			throw new FatalException(e1);
		}
		/** END: load XML file database **/

		nukelog = new NukeLog();

		try {
			Document doc =
				new SAXBuilder().build(new FileReader("nukelog.xml"));
			List nukes = doc.getRootElement().getChildren("nukes");
			for (Iterator iter = nukes.iterator(); iter.hasNext();) {
				Element nukeElement = (Element) iter.next();

				User user =
					usermanager.getUserByName(nukeElement.getChildText("user"));
				String command = nukeElement.getChildText("command");
				String directory = nukeElement.getChildText("directory");
				long time = Long.parseLong(nukeElement.getChildText("time"));
				int multiplier =
					Integer.parseInt(nukeElement.getChildText("multiplier"));
				String reason = nukeElement.getChildText("reason");

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

				nukelog.add(
					new NukeEvent(
						user,
						command,
						directory,
						time,
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
		List rslaves = SlaveManagerImpl.loadRSlaves();
		GlobRMIServerSocketFactory ssf =
			new GlobRMIServerSocketFactory(rslaves);
		/** register slavemanager **/
		try {
			slavemanager = new SlaveManagerImpl(cfg, rslaves, ssf, this);
		} catch (RemoteException e) {
			throw new FatalException(e);
		}

		if (cfg
			.getProperty("master.localslave", "false")
			.equalsIgnoreCase("true")) {
			try {
				new SlaveImpl(cfg);
			} catch (RemoteException ex) {
				ex.printStackTrace();
				System.exit(0);
				return;
				//the compiler doesn't know that execution stops at System.exit(),
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

		timer = new Timer();
		TimerTask timerLogoutIdle = new TimerTask() {
			public void run() {
				timerLogoutIdle();
			}
		};
		//run every 10 seconds
		timer.schedule(timerLogoutIdle, 0, 10 * 1000);

		TimerTask timerSave = new TimerTask() {
			public void run() {
				getSlavemanager().saveFilesXML();
				try {
					getUsermanager().saveAll();
				} catch (UserFileException e) {
					logger.log(Level.FATAL, "Error saving all users", e);
				}
			}
		};
		//run every 5 minutes
		timer.schedule(timerSave, 0, 600 * 1000);

		if (cfg.getProperty("irc.enabled", "false").equals("true")) {
			try {
				addFtpListener(
					new IRCListener(this, getConfig(), new String[0]));
			} catch (Exception e2) {
				logger.log(Level.WARN, "Error starting IRC bot", e2);
			}
		}
		addFtpListener(new XferLogListener());
	}

	public void timerLogoutIdle() {
		long currTime = System.currentTimeMillis();
		synchronized (connections) {
			//for(Iterator i = ((Vector)connections.clone()).iterator(); i.hasNext(); ) {
			for (Iterator i = connections.iterator(); i.hasNext();) {
				FtpConnection conn = (FtpConnection) i.next();

				int idle = (int) ((currTime - conn.getLastActive()) / 1000);
				int maxIdleTime;
					try {
						maxIdleTime = conn.getUser().getMaxIdleTime();
						if(maxIdleTime == 0) maxIdleTime = idleTimeout;
					} catch (NoSuchUserException e) {
						maxIdleTime = idleTimeout;
					}

				if (!conn.isExecuting() && idle >= maxIdleTime) {
					// idle time expired, logout user.
					conn.stop(
						"Idle time expired: "
							+ maxIdleTime
							+ "s");
				}
			}
		}
	}

	public void start(Socket sock) throws IOException {
		FtpConnection conn =
			new FtpConnection(
				sock,
				usermanager,
				slavemanager,
				slavemanager.getRoot(),
				this,
				this.nukelog,
				this.commandDebug);
		conn.ftpListeners = this.ftpListeners;
		connections.add(conn);
		conn.start();
	}
	public void shutdown(String message) {
		this.shutdownMessage = message;
		for (Iterator iter = getConnections().iterator(); iter.hasNext();) {
			((FtpConnection) iter.next()).stop(message);
		}
		dispatchFtpEvent(new MessageEvent("SHUTDOWN", message));
	}

	private ArrayList ftpListeners = new ArrayList();
	public void addFtpListener(FtpListener listener) {
		ftpListeners.add(listener);
	}

	public void remove(BaseFtpConnection conn) {
		if (!connections.remove(conn)) {
			throw new RuntimeException("connections.remove() returned false.");
		}
		if (isShutdown() && connections.isEmpty()) {
			slavemanager.saveFilesXML();
			try {
				getUsermanager().saveAll();
			} catch (UserFileException e) {
				logger.log(Level.WARN, "Failed to save all userfiles", e);
			}
			System.exit(0);
		}
	}

	/**
	 * returns a <code>Collection</code> of current connections
	 */
	public Collection getConnections() {
		return this.connections;
	}
	public boolean isShutdown() {
		return this.shutdownMessage != null;
	}
	public String getShutdownMessage() {
		return this.shutdownMessage;
	}

	public static final String VERSION = "drftpd v0.7.0";
	public static void main(String args[]) {
		BasicConfigurator.configure();
		System.out.println(VERSION + " master server starting.");
		System.out.println("http://drftpd.sourceforge.net");

		try {
//			Handler handlers[] = Logger.getLogger("").getHandlers();
//			if (handlers.length == 1) {
//				handlers[0].setLevel(Level.ALL);
//			} else {
//				logger.WARN(
//					"handlers.length != 1, can't setLevel() on root element");
//			}

			String cfgFileName;
			if (args.length >= 1) {
				cfgFileName = args[0];
			} else {
				cfgFileName = "drftpd-0.7.conf";
			}
			if (new File(cfgFileName).exists()) {
				System.out.println(cfgFileName + " does not exist.");
			}
			/** load config **/
			Properties cfg = new Properties();
			try {
				cfg.load(new FileInputStream(cfgFileName));
			} catch (IOException e) {
				logger.fatal(
					"Error reading " + cfgFileName + ": " + e.getMessage());
				return;
			}

			logger.info("Starting ConnectionManager");
			ConnectionManager mgr = new ConnectionManager(cfg, cfgFileName);
			System.setProperty("line.separator", "\r\n");
			/** listen for connections **/
			try {
				ServerSocket server =
					new ServerSocket(
						Integer.parseInt(cfg.getProperty("master.port")));
				logger.info("Listening on port " + server.getLocalPort());
				while (true) {
					mgr.start(server.accept());
				}
			} catch (BindException e) {
				throw new FatalException(
					"Couldn't bind on port " + cfg.getProperty("master.port"),
					e);
			} catch (Exception e) {
				logger.log(Level.FATAL, "", e);
			}
		} catch (Throwable th) {
			logger.log(Level.FATAL, "", th);
			System.exit(0);
			return;
		}
	}

	/**
	 * @return
	 */
	public SlaveManagerImpl getSlavemanager() {
		return slavemanager;
	}

	/**
	 * @return
	 */
	public UserManager getUsermanager() {
		return usermanager;
	}
	/**
	 * @return
	 */
	public FtpConfig getConfig() {
		return config;
	}
	/**
	 * @return
	 */
	public Properties getPropertiesConfig() {
		return propertiesConfig;
	}

}