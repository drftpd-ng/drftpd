package net.sf.drftpd.slave;

import java.io.IOException;
import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * @author mog
 */
public interface Transfer extends Remote {
	public static final char TRANSFER_RECEIVING='R';// TRANSFER_UPLOAD='R';
	public static final char TRANSFER_SENDING='S';// TRANSFER_DOWNLOAD='S';

	public int getLocalPort() throws RemoteException;
	public long getTransfered() throws RemoteException;
	public void transfer() throws RemoteException, IOException;
}
