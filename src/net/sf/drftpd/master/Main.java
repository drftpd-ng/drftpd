package net.sf.drftpd.master;

import java.net.ServerSocket;
import java.io.FileInputStream;
import java.util.Properties;
import java.util.Hashtable;
import java.rmi.RemoteException;

public class Main {

	public static void main(String args[]) {
		/** load config **/
		Properties cfg = new Properties();
		try {
			cfg.load(new FileInputStream("drftpd.conf"));
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		
		System.setProperty("line.separator", "\r\n");
		
		/** register slavemanager **/
		SlaveManager slavemanager;
		try {
			slavemanager =
				new SlaveManagerImpl(cfg.getProperty("slavemanager.url"));
		} catch (RemoteException ex) {
			ex.printStackTrace();
			return;
		}
		
		UserManager usermanager = new GlftpdUserManager(cfg);
		/** listen for connections **/
		try {
			ServerSocket server =
				new ServerSocket(
					Integer.parseInt(cfg.getProperty("master.port")));
			System.out.println("Listening on port " + server.getLocalPort());
			while (true) {
				FtpConnection conn =
					new FtpConnection(server.accept(), cfg, usermanager, slavemanager, slavemanager.getRoot());
				//conn.setSlaveManager(slavemanager);
				new Thread(conn).start();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
