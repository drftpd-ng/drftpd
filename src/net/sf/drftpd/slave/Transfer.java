package net.sf.drftpd.slave;

import java.io.IOException;
import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * @author <a href="mailto:drftpd@mog.se">Morgan Christiansson</a>
 */
public interface Transfer extends Remote {
	public static final char TRANSFER_RECEIVING_UPLOAD='R';
	public static final char TRANSFER_SENDING_DOWNLOAD='S';
	public static final char TRANSFER_THROUGHPUT='A';
	
	public long getChecksum() throws RemoteException;
	public int getLocalPort() throws RemoteException;
	public long getTransfered() throws RemoteException;
	public void transfer() throws RemoteException, IOException;
}
