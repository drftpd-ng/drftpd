/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 * 
 * DrFTPD is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 * 
 * DrFTPD is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with DrFTPD; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package net.sf.drftpd.slave;

import java.io.IOException;
import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * @author mog
 * @version $Id: Transfer.java,v 1.22 2004/02/10 00:03:31 mog Exp $
 */
public interface Transfer extends Remote {
	public static final char TRANSFER_RECEIVING_UPLOAD='R';
	public static final char TRANSFER_SENDING_DOWNLOAD='S';
	public static final char TRANSFER_THROUGHPUT='A';
	public static final char TRANSFER_UNKNOWN='U';
	
	public void abort() throws RemoteException;
	public long getChecksum() throws RemoteException;

	/**
	 * Returns how long this transfer has been running in milliseconds.
	 */
	public long getElapsed() throws RemoteException;
	
	/**
	 * For a passive connection, returns the port the serversocket is listening on.
	 */
	public int getLocalPort() throws RemoteException;
	public TransferStatus getStatus() throws RemoteException;

	/**
	 * Returns the number of bytes transfered.
	 */
	public long getTransfered() throws RemoteException;
	
	/**
	 * Returns how fast the transfer is going in bytes per second.
	 */
	public int getXferSpeed() throws RemoteException;
	public TransferStatus receiveFile(String dirname, char mode, String filename, long offset) throws RemoteException, IOException;
	public TransferStatus sendFile(String path, char mode, long resumePosition, boolean checksum) throws RemoteException, IOException;
}
