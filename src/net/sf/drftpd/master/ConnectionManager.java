package net.sf.drftpd.master;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.rmi.AlreadyBoundException;
import java.rmi.RemoteException;
import java.rmi.StubNotFoundException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.drftpd.event.GlftpdLog;
import net.sf.drftpd.master.queues.NukeLog;
import net.sf.drftpd.master.usermanager.GlftpdUserManager;
import net.sf.drftpd.master.usermanager.User;
import net.sf.drftpd.master.usermanager.UserManager;
import net.sf.drftpd.remotefile.JDOMRemoteFile;
import net.sf.drftpd.remotefile.LinkedRemoteFile;
import net.sf.drftpd.slave.*;
import net.sf.drftpd.slave.Slave;
import net.sf.drftpd.slave.SlaveImpl;

import org.jdom.Document;
import org.jdom.Element;
import org.jdom.input.SAXBuilder;

import se.mog.io.File;

public class ConnectionManager {
	public static final int idleTimeout = 600;
	private Vector connections = new Vector();
	private UserManager usermanager;
	private SlaveManagerImpl slavemanager;
	private Timer timer;

	private static Logger logger =
		Logger.getLogger(ConnectionManager.class.getName());
	static {
		logger.setLevel(Level.FINEST);
	}

	public ConnectionManager(Properties cfg) {
		LinkedRemoteFile root = null;

		List rslaves = new ArrayList();
		try {
			Document doc = new SAXBuilder().build(new FileReader("slaves.xml"));
			List children = doc.getRootElement().getChildren("slave");
			for (Iterator i = children.iterator(); i.hasNext();) {
				List masks = new ArrayList();
				Element slaveElement = (Element) i.next();
				List maskElements = slaveElement.getChildren("mask");
				for (Iterator i2 = maskElements.iterator(); i2.hasNext();) {
					masks.add(((Element) i2.next()).getText());
				}
				rslaves.add(
					new RemoteSlave(slaveElement.getChildText("name"), masks));
			}
		} catch (Exception ex) {
			logger.log(Level.INFO, "Error reading masks from slaves.xml", ex);
		}
		/** END: load XML file database **/
		NukeLog nukelog;
		try {
			Document doc =
				new SAXBuilder().build(new FileReader("nukelog.xml"));
			List nukes = doc.getRootElement().getChildren("nuke");
		} catch (Exception ex) {
			logger.log(
				Level.INFO,
				"Error loading nukelog from nukelog.xml",
				ex);
		}

		/** load XML file database **/
		try {
			Document doc = new SAXBuilder().build(new FileReader("files.xml"));
			root =
				new LinkedRemoteFile(
					new JDOMRemoteFile(doc.getRootElement(), rslaves));
		} catch (FileNotFoundException ex) {
			logger.info("files.xml not found, new file will be created.");
			root = new LinkedRemoteFile();
		} catch (Exception ex) {
			logger.info("Error loading \"files.xml\"");
			ex.printStackTrace();
			root = new LinkedRemoteFile();
		}

		/** register slavemanager **/
		try {
			slavemanager =
				new SlaveManagerImpl(
					cfg.getProperty("slavemanager.url"),
					root,
					rslaves);
		} catch (StubNotFoundException ex) {
			throw new RuntimeException(
				"StubNotFoundException, try running rmic",
				ex);
		} catch (RemoteException ex) {
			logger.log(Level.SEVERE, "RemoteException", ex);
			return;
		} catch (AlreadyBoundException ex) {
			logger.log(Level.SEVERE, "AlreadyBoundException", ex);
			return;
		}

		String localslave = cfg.getProperty("master.localslave", "false");
		if (localslave.equalsIgnoreCase("true")) {
			Slave slave;
			try {
				slave = new SlaveImpl(cfg);
			} catch (RemoteException ex) {
				ex.printStackTrace();
				System.exit(0);
				return;
				//the compiler doesn't know that execution stops at System.exit(),
			}
			RemoteSlave remoteSlave =
				new RemoteSlave(cfg.getProperty("slave.name"), slave);

			try {
				LinkedRemoteFile slaveroot =
					SlaveImpl.getDefaultRoot(
						remoteSlave,
						cfg.getProperty("slave.roots"));
				slavemanager.addSlave(remoteSlave, slaveroot);
			} catch (RemoteException ex) {
				ex.printStackTrace();
				return;
			} catch (IOException ex) {
				ex.printStackTrace();
				System.exit(0);
				return;
			}
		}

		usermanager = new GlftpdUserManager(cfg);

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
				slavemanager.saveFilesXML();
			}
		};
		//run every 5 minutes
		timer.schedule(timerSave, 0, 600 * 1000);
	}

	public void timerLogoutIdle() {
		long currTime = System.currentTimeMillis();
		synchronized (connections) {
			//for(Iterator i = ((Vector)connections.clone()).iterator(); i.hasNext(); ) {
			for (Iterator i = connections.iterator(); i.hasNext();) {
				FtpConnection conn = (FtpConnection) i.next();

				int idle = (int) ((currTime - conn.getLastActive()) / 1000);
				if (conn.getUser() == null) {
					logger.finer(conn + " not logged in");
					continue;
				}
				int maxIdleTime = conn.getUser().getMaxIdleTime();
				if (maxIdleTime == 0)
					maxIdleTime = idleTimeout;
				User user = conn.getUser();
				//				logger.finest(
				//					"User has been idle for "
				//						+ idle
				//						+ "s, max "
				//						+ maxIdleTime
				//						+ "s");

				if (idle >= maxIdleTime) {
					// idle time expired, logout user.
					conn.stop(
						"Idle time expired: "
							+ conn.getUser().getMaxIdleTime()
							+ "s");
				}
			}
		}
	}
	//TODO Fix FtpListener so that it doesn't need to be init:ed for each conn
	public void start(Socket sock) throws IOException {
		FtpConnection conn =
			new FtpConnection(
				sock,
				usermanager,
				slavemanager,
				slavemanager.getRoot(),
				this);

		conn.addFtpListener(new GlftpdLog(new File("glftpd.log")));
		connections.add(conn);
		conn.start();
	}

	public void remove(BaseFtpConnection conn) {
		if (!connections.remove(conn)) {
			throw new RuntimeException("connections.remove() returned false.");
		}
	}

	/**
	 * returns a <code>Collection</code> of current connections
	 */
	public Collection getConnections() {
		return connections;
	}

	public static void main(String args[]) {
		System.out.println("drftpd alpha master server starting.");
		System.out.println("http://drftpd.sourceforge.net");
		try {
			Handler handlers[] = Logger.getLogger("").getHandlers();
			if (handlers.length == 1) {
				handlers[0].setLevel(Level.FINEST);
			} else {
				logger.warning(
					"handlers.length != 1, can't setLevel() on root element");
			}

			/** load config **/
			logger.info("loading drftpd.conf");
			Properties cfg = new Properties();
			try {
				cfg.load(new FileInputStream("drftpd.conf"));
			} catch (IOException e) {
				logger.severe("Error reading drftpd.conf: " + e.getMessage());
				return;
			}

			logger.info("Starting ConnectionManager");
			ConnectionManager mgr = new ConnectionManager(cfg);
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
			} catch (Exception e) {
				e.printStackTrace();
			}
		} catch (Throwable th) {
			logger.log(Level.SEVERE, "", th);
			System.exit(0);
			return;
		}
	}
}