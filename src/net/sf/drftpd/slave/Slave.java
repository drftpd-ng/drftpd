package net.sf.drftpd.slave;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetAddress;
import java.rmi.Remote;
import java.rmi.RemoteException;

import net.sf.drftpd.ObjectExistsException;
import net.sf.drftpd.SFVFile;

/**
 * Slave interface, this interface is used to initate transfers to and from remote slaves.
 * @author Morgan Christiansson <mog@linux.nu>
 */
public interface Slave extends Remote {
	/**
	 * Connect to 'addr':'port'. and send 'file'.
	 * 
	 * Argument should be a StaticStaticRemoteFile so that the whole directory structure doesn't get serialized and sent.
	 */
	public Transfer doConnectSend(
		String path,
		char type,
		long offset,
		InetAddress addr,
		int port)
		throws RemoteException, IOException;

	/**
	 * Listen on any port and send 'file' when connection is receieved.
	 */
	public Transfer doListenSend(String path, char type, long offset)
		throws RemoteException, IOException;

	/**
	 * Connect to 'addr':'port' and receive file.
	 */
	public Transfer doConnectReceive(
		String dirname,
		String file,
		char type,
		long offset,
		InetAddress addr, int port)
		throws RemoteException, IOException;
		
	/**
	 * Listen on any port and receive 'file' when connection is received.
	 */
	public Transfer doListenReceive(
		String dirname,
		String file,
		char type, long offset)
		throws RemoteException, IOException;

	public long checkSum(
		String path)
		throws RemoteException, IOException;
		
	/**
	 * Get statistics for this slave, usefull when deciding which slave to use when transferring files.
	 */
	public SlaveStatus getSlaveStatus() throws RemoteException;

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
		throws RemoteException, FileNotFoundException, ObjectExistsException;
		
	/**
	 * Delete files.
	 */
	public void delete(String path) throws RemoteException, IOException;
}
