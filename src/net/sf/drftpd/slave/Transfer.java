package net.sf.drftpd.slave;

import java.io.IOException;
import java.net.InetAddress;
import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * @author <a href="mailto:drftpd@mog.se">Morgan Christiansson</a>
 */
public interface Transfer extends Remote {
	public static final char TRANSFER_RECEIVING_UPLOAD='R';
	public static final char TRANSFER_SENDING_DOWNLOAD='S';
	public static final char TRANSFER_THROUGHPUT='A';
	public static final char TRANSFER_UNKNOWN='U';
	
	public long getChecksum() throws RemoteException;
	public int getLocalPort() throws RemoteException;
	public long getTransfered() throws RemoteException;
	public int getTransferSpeed() throws RemoteException;
	
	public void uploadFile(String dirname, String filename, long offset) throws RemoteException, IOException;
	public void downloadFile(String path, char mode, long resumePosition) throws RemoteException, IOException;
	/**
	 * @deprecated use RemoteSlave.getAddress()
	 * @return
	 * @throws RemoteException
	 */
	public InetAddress getEndpoint() throws RemoteException;
	/**
	 * @return
	 */
	public long getTransferTime() throws RemoteException;
}
