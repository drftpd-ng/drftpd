package net.sf.drftpd.slave;

import net.sf.drftpd.PermissionDeniedException;
import net.sf.drftpd.SFVFile;
import net.sf.drftpd.master.usermanager.User;
import net.sf.drftpd.remotefile.RemoteFile;

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
		char mode,
		long offset,
		InetAddress addr,
		int port)
		throws RemoteException, IOException;

	/**
	 * Listen on any port and send 'file' when connection is receieved.
	 */
	public Transfer doListenSend(RemoteFile file, char mode, long offset)
		throws RemoteException, IOException;

	/**
	 * Connect to 'addr':'port' and save stream to 'path'.
	 */
//	public Transfer doConnectReceive(String path, InetAddress addr, int port) throws RemoteException, IOException;

	/**
	 * Connect to 'addr':'port' and receive file.
	 */
	public Transfer doConnectReceive(
		RemoteFile dir,
		String file,
		User owner,
		long offset,
		InetAddress addr,
		int port)
		throws RemoteException, IOException;
		
	/**
	 * Listen on any port and receive 'file' when connection is received.
	 */
	public Transfer doListenReceive(
		RemoteFile dir,
		String file,
		User owner,
		long offset)
		throws RemoteException, IOException;

	public long checkSum(
		String path)
		throws RemoteException, IOException;
		
	/**
	 * Get statistics for this slave, usefull when deciding which slave to use when transferring files.
	 */
	public SlaveStatus getSlaveStatus() throws RemoteException;
	/**
	 * Attempt to create the directory 'path'.
	 */
	public void mkdir(User user, String path)
		throws RemoteException, IOException;

	/**
	 * Check to see if slave is still up.
	 */
	public void ping() throws RemoteException;
	
	public SFVFile getSFVFile(String path)
		throws RemoteException, IOException;
	
	/**
	 * Rename files.
	 */
	public void rename(String from, String to)
		throws RemoteException, IOException;
		
	/**
	 * Delete files.
	 */
	public void delete(String path) throws RemoteException, IOException;
}
