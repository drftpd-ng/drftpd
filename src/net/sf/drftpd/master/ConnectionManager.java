package net.sf.drftpd.master;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.awt.image.SampleModel;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.rmi.AlreadyBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.rmi.server.RMISocketFactory;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;

import org.jdom.Document;
import org.jdom.Element;
import org.jdom.input.SAXBuilder;
import sun.security.action.GetLongAction;

import net.sf.drftpd.master.usermanager.GlftpdUserManager;
import net.sf.drftpd.master.usermanager.UserManager;
import net.sf.drftpd.permission.GlobRMISocketFactory;
import net.sf.drftpd.remotefile.*;
import net.sf.drftpd.remotefile.LinkedRemoteFile;
import net.sf.drftpd.remotefile.RemoteFile;
import net.sf.drftpd.slave.*;
import net.sf.drftpd.slave.RemoteSlave;
import net.sf.drftpd.slave.SlaveImpl;

public class ConnectionManager {
	private Vector connections = new Vector();
	private UserManager usermanager;
	private SlaveManagerImpl slavemanager;
	private Timer timer;

	private static Logger logger =
		Logger.getLogger("net.sf.drftpd.master.ConnectionManager");
	static {
		logger.setLevel(Level.ALL);
	}
	public ConnectionManager(Properties cfg) {
		logger.setLevel(Level.ALL);
		LinkedRemoteFile root = null;

		/** load XML file database **/
		try {
			logger.log(Level.FINEST, "Loading files.xml:1");
			Document doc = new SAXBuilder().build(new FileReader("files.xml"));

			logger.log(Level.FINEST, "Loading files.xml:2");
				root = new LinkedRemoteFile(null, // slaves = null
		null, // parent = null
		new JDOMRemoteFile("", doc.getRootElement()) // entry
	);
		} catch (FileNotFoundException ex) {
			logger.finest(
				"files.xml not found, new file will be created.");
			root = new LinkedRemoteFile();
		} catch (Exception ex) {
			logger.finest("Error loading \"files.xml\"");
			ex.printStackTrace();
			root = new LinkedRemoteFile();
		}

		List masks = new ArrayList();
		try {
			Document doc = new SAXBuilder().build(new FileReader("slaves.xml"));
			List children = doc.getRootElement().getChildren("slave");
			for (Iterator i = children.iterator(); i.hasNext();) {
				Element slaveElement = (Element) i.next();
				List maskElements = slaveElement.getChildren("mask");
				for (Iterator i2 = maskElements.iterator(); i2.hasNext();) {
					Element maskElement = (Element) i2.next();
					masks.add(maskElement.getText());
				}
			}
		} catch (Exception ex) {
			logger.info("Error reading masks from slaves.xml");
			ex.printStackTrace();
		}
		/** END: load XML file database **/

		/** register slavemanager **/
		try {
			slavemanager =
				new SlaveManagerImpl(
					cfg.getProperty("slavemanager.url"),
					root,
					masks);
		} catch (RemoteException ex) {
			ex.printStackTrace();
			return;
		}

		String localslave = cfg.getProperty("master.localslave");
		if (localslave != null && localslave.equalsIgnoreCase("true")) {
			RemoteSlave slave;
			try {
				slave = new RemoteSlave(new SlaveImpl(cfg));
			} catch (RemoteException ex) {
				ex.printStackTrace();
				System.exit(0);
				return;
				//the compiler doesn't know that execution stops at System.exit(),
				// use "return" to supress warning
			}

			try {
				LinkedRemoteFile slaveroot =
					SlaveImpl.getDefaultRoot(
						cfg.getProperty("slave.root"),
						slave);
				slavemanager.addSlave(slave, slaveroot);
			} catch (RemoteException ex) {
				ex.printStackTrace();
				return;
			} catch (IOException ex) {
				ex.printStackTrace();
				System.exit(-1);
				return;
			}
		}

		usermanager = new GlftpdUserManager(cfg);

		timer = new Timer();
		TimerTask timerTask = new TimerTask() {
			public void run() {
				timerTask();
			}
		};
		timer.schedule(timerTask, 0, 10000);
	}

	public void timerTask() {
		long currTime = System.currentTimeMillis();
		synchronized (connections) {
			//		for(Iterator i = ((Vector)connections.clone()).iterator(); i.hasNext(); ) {
			for (Iterator i = connections.iterator(); i.hasNext();) {
				FtpConnection conn = (FtpConnection) i.next();

				int idle = (int) ((currTime - conn.getLastActive()) / 1000);
				if (conn.getUser() == null) {
					System.out.println(conn + " not logged in");
					continue;
				}
				System.out.println(
					"User has been idle for "
						+ idle
						+ "s, max "
						+ conn.getUser().getMaxIdleTime()
						+ "s");
				if (idle >= conn.getUser().getMaxIdleTime()) {
					// idle time expired, logout user.
					conn.stop(
						"Idle time expired: "
							+ conn.getUser().getMaxIdleTime()
							+ "s");
				}
			}
		}
	}

	public void start(Socket sock) {
		FtpConnection conn =
			new FtpConnection(
				sock,
				usermanager,
				slavemanager,
				slavemanager.getRoot(),
				this);
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
		logger.info("drftpd-alpha. master server starting.");
		/** load config **/
		logger.finest("loading drftpd.conf");
		Properties cfg = new Properties();
		try {
			cfg.load(new FileInputStream("drftpd.conf"));
		} catch (Exception ex) {
			logger.log(Level.SEVERE, "Error loading drftpd.conf", ex);
		}

		logger.finest("Starting ConnectionManager");
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
	}
}