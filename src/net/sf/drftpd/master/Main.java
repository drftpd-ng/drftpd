package net.sf.drftpd.master;

import java.net.ServerSocket;
import java.io.FileInputStream;
import java.util.Properties;
import java.util.Hashtable;
import java.rmi.RemoteException;

public class Main {

    public static void main(String args[]) {
	/** load config **/
	Properties cfg= new Properties();
	try {
	    cfg.load(new FileInputStream("drftpd.conf"));
	} catch(Exception ex) {
	    ex.printStackTrace();
	}

	/** register slavemanager **/
	SlaveManager slavemanager=null;
	try {
	    slavemanager = new SlaveManagerImpl(cfg.getProperty("slavemanager.url"));
	} catch(RemoteException ex) {
	    ex.printStackTrace();
	}
	//System.out.println("SlaveManager: "+slavemanager);

	/** listen for connections **/
	try {
	    ServerSocket server = new ServerSocket(Integer.parseInt(cfg.getProperty("master.port")));
            System.out.println("Listening on port "+server.getLocalPort());
	    while(true) {
		FtpUser user = new FtpUser(new VirtualDirectory(slavemanager.getRoot()));
		FtpConnection conn = new FtpConnection(server.accept(), cfg, user);
		conn.setSlaveManager(slavemanager);
		new Thread(conn).start();
	    }
	} catch(Exception e) {
	    e.printStackTrace();
	}
    }

}
