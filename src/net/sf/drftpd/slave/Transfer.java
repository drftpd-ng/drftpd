package net.sf.drftpd.slave;

import java.io.IOException;
import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * @author mog
 */
public interface Transfer extends Remote {
	public int getLocalPort() throws RemoteException;
	public void transfer() throws RemoteException, IOException;
}
