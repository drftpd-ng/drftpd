package net.sf.drftpd.slave;

import net.sf.drftpd.RemoteFile;
import net.sf.drftpd.PermissionDeniedException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.net.InetAddress;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.ConnectException;

/**
 * Slave interface, this interface is used to initate transfers to and from remote slaves.
 * @author Morgan Christiansson <mog@linux.nu>
 */
public interface Slave extends Remote {
	/**
	 * Connect to 'addr':'port'. and send 'file'.
	 * 
	 * Argument should be a StaticRemoteFile so that the whole directory structure doesn't get serialized and sent.
	 */
	public Transfer doConnectSend(
		RemoteFile file,
		long offset,
		InetAddress addr,
		int port)
		throws RemoteException, IOException;

	/**
	 * Listen on 'port' and send 'file' when connection is receieved.
	 * 
	 * If argument 'port' is 0, the argument to ServerSocket will also be 0 and Transfer.getLocalPort() may be used to retreive the listening port .
	 */
	public Transfer doListenSend(RemoteFile file, long offset, int port)
		throws RemoteException, IOException;

	/**
	 * Connect to 'addr':'port' and receive file.
	 */
	public Transfer doConnectReceive(
		RemoteFile file,
		long offset,
		InetAddress addr,
		int port)
		throws RemoteException, IOException;
	/**
	 * Listen on 'port' and receive 'file' when connection is received.
	 * 
	 * If argument 'port' is 0, the argument to ServerSocket will also be 0 and Transfer.getLocalPort() may be used to retreive the listening port .
	 */
	public Transfer doListenReceive(RemoteFile file, long offset, int port)
		throws RemoteException, IOException;

	/**
	 * Get statistics for this slave, usefull when deciding which slave to use when transferring files.
	 */
	public SlaveStatus getSlaveStatus() throws RemoteException;
	/**
	 * Attempt to create the directory 'path'.
	 */
	public void mkdir(String path)
		throws RemoteException, IOException;
}
