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
	public static final char TRANSFER_UNKNOWN='U';
	
	public long getChecksum() throws RemoteException;
	/**
	 * For a passive connection, returns the port the serversocket is listening on.
	 */
	public int getLocalPort() throws RemoteException;

	/**
	 * Returns the number of bytes transfered.
	 */
	public long getTransfered() throws RemoteException;
	/**
	 * Returns how fast the transfer is going in bytes per second.
	 */
	public int getXferSpeed() throws RemoteException;
	
	public void receiveFile(String dirname, String filename, long offset) throws RemoteException, IOException;
	public void sendFile(String path, char mode, long resumePosition, boolean checksum) throws RemoteException, IOException;
	public long getElapsed() throws RemoteException;
	
	public void abort() throws RemoteException;
}
