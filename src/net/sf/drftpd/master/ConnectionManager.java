package net.sf.drftpd.master;

import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.rmi.RemoteException;
import java.util.Collection;
import java.util.Iterator;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;

import org.jdom.Document;
import org.jdom.input.SAXBuilder;

import net.sf.drftpd.master.usermanager.GlftpdUserManager;
import net.sf.drftpd.master.usermanager.UserManager;
import net.sf.drftpd.remotefile.*;
import net.sf.drftpd.remotefile.LinkedRemoteFile;
import net.sf.drftpd.remotefile.RemoteFileTree;
import net.sf.drftpd.slave.*;
import net.sf.drftpd.slave.RemoteSlave;
import net.sf.drftpd.slave.SlaveImpl;

public class ConnectionManager {
	private Vector connections = new Vector();
	private UserManager usermanager;
	private SlaveManagerImpl slavemanager;
	private Timer timer;

	public ConnectionManager(Properties cfg) {

		/** register slavemanager **/
		try {
		Document doc = new SAXBuilder().build(new FileReader("files.xml"));
		
		LinkedRemoteFile root = new LinkedRemoteFile(new JDOMRemoteFileTree("", doc.getRootElement()));
		} catch(Exception ex) {
			System.err.println("Error loading \"files.xml\"");
			ex.printStackTrace();
		}
		try {
			slavemanager =
				new SlaveManagerImpl(cfg.getProperty("slavemanager.url"));
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
				//the compiler doesn't know that execution stops at System.exit() stops execution
			}
			
			try {
				LinkedRemoteFile slaveroot = SlaveImpl.getDefaultRoot(cfg.getProperty("slave.root"), slave);
				slavemanager.addSlave(slave, slaveroot);
			} catch (RemoteException ex) {
				ex.printStackTrace();
				return;
			} catch(IOException ex) {
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
		/** load config **/
		Properties cfg = new Properties();
		try {
			cfg.load(new FileInputStream("drftpd.conf"));
		} catch (Exception ex) {
			ex.printStackTrace();
		}

		ConnectionManager mgr = new ConnectionManager(cfg);
		System.setProperty("line.separator", "\r\n");
		/** listen for connections **/
		try {
			ServerSocket server =
				new ServerSocket(
					Integer.parseInt(cfg.getProperty("master.port")));
			System.out.println("Listening on port " + server.getLocalPort());
			while (true) {
				mgr.start(server.accept());
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}