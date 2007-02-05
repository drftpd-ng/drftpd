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
package org.drftpd.vfs;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import net.sf.drftpd.NoAvailableSlaveException;
import net.sf.drftpd.ObjectNotFoundException;
import net.sf.drftpd.SlaveUnavailableException;

import org.drftpd.master.RemoteSlave;

/**
 * @author zubov
 * @version $Id$
 */
public class FileHandle extends InodeHandle implements FileHandleInterface {

	public FileHandle(String path) {
		super(path);
	}

	@Override
	/**
	 * @see org.drftpd.vfs.InodleHandle#getInode()
	 */
	protected VirtualFileSystemFile getInode() throws FileNotFoundException {
		VirtualFileSystemInode inode = super.getInode();
		if (inode instanceof VirtualFileSystemFile) {
			return (VirtualFileSystemFile) inode;
		}
		throw new ClassCastException("FileHandle object pointing to Inode:"
				+ inode);
	}

	/**
	 * Sets the xfertime of this file
	 * @throws FileNotFoundException if there's no such file.
	 */
	public void setXfertime(long x) throws FileNotFoundException {
		getInode().setXfertime(x);
	}

	/**
	 * @return a Set containing the slaves that contain the File.
	 * @throws FileNotFoundException
	 */
	public Set<RemoteSlave> getSlaves() throws FileNotFoundException {
		HashSet<RemoteSlave> slaves = new HashSet<RemoteSlave>();
		for (String slave : getInode().getSlaves()) {
			try {
				slaves.add(getGlobalContext().getSlaveManager().getRemoteSlave(
						slave));
			} catch (ObjectNotFoundException e) {
				getInode().removeSlave(slave);
			}
		}
		return slaves;
	}

	/**
	 * @return a filtered Collection containing only the avaiable slaves of
	 * the Inode.
	 * @throws NoAvailableSlaveException
	 * @throws FileNotFoundException
	 */
	public Collection<RemoteSlave> getAvailableSlaves()
			throws NoAvailableSlaveException, FileNotFoundException {
		HashSet<RemoteSlave> rslaves = new HashSet<RemoteSlave>();
		for (RemoteSlave rslave : getSlaves()) {
			if (rslave.isAvailable()) {
				rslaves.add(rslave);
			}
		}
		if (rslaves.isEmpty()) {
			throw new NoAvailableSlaveException();
		}
		return rslaves;
	}

	/**
	 * Changes the CRC32 of the File.
	 * @param checksum
	 * @throws FileNotFoundException
	 */
	public void setCheckSum(long checksum) throws FileNotFoundException {
		getInode().setChecksum(checksum);
	}

	/**
	 * Removes a slave from the slavelist.
	 */
	public void removeSlave(RemoteSlave sourceSlave)
			throws FileNotFoundException {
		getInode().removeSlave(sourceSlave.getName());
	}

	/**
	 * @return the first slave of the slave list.
	 * @throws FileNotFoundException if there's no such file.
	 * @throws NoAvailableSlaveException if there's no avaiable slave.
	 */
	protected RemoteSlave getASlaveForFunction() throws FileNotFoundException,
	NoAvailableSlaveException {
		for (RemoteSlave rslave : getAvailableSlaves()) {
			return rslave;
		}
		throw new NoAvailableSlaveException("No slaves are online for file "
				+ this);
	}

	/**
	 * @return the CRC32 of the file.
	 * @throws FileNotFoundException if there's no such file.
	 * @throws NoAvailableSlaveException if there's no avaiable slave.
	 */
	public long getCheckSum() throws NoAvailableSlaveException,
			FileNotFoundException {
		long checksum = getInode().getChecksum();
		if (checksum == 0L) {
			while (true) {
				RemoteSlave rslave = getASlaveForFunction();
				try {
					checksum = rslave.getCheckSumForPath(getPath());
					getInode().setChecksum(checksum);
					return checksum;
				} catch (IOException e) {
					rslave.setOffline(e);
				} catch (SlaveUnavailableException e) {
					continue;
				}
			}
		}
		return checksum;
	}

	/**
	 * Add a slave to the slave list.
	 * @param destinationSlave
	 * @throws FileNotFoundException if there's no such file.
	 */
	public void addSlave(RemoteSlave destinationSlave)
			throws FileNotFoundException {
		getInode().addSlave(destinationSlave.getName());
	}

	/**
	 * @return the xfertime.
	 * @throws FileNotFoundException if there's no such file.
	 */
	public long getXfertime() throws FileNotFoundException {
		return getInode().getXfertime();
	}
	
	public boolean isUploading() throws FileNotFoundException {
		return getInode().isUploading();
	}
	
	public boolean isDownloading() throws FileNotFoundException {
		return getInode().isDownloading();
	}
	
	public boolean isTransferring() throws FileNotFoundException {
		return getInode().isTransferring();
	}
	
	public void abortTransfers(String reason) throws FileNotFoundException {
		getInode().abortTransfers(reason);
	}
	
	public void abortUploads(String reason) throws FileNotFoundException {
		getInode().abortUploads(reason);
	}
	
	public void abortDownloads(String reason) throws FileNotFoundException {
		getInode().abortDownloads(reason);
	}

	/**
	 * @return true if there's an avaiable slave for the file or
	 * false if there isn't.
	 */
	public boolean isAvailable() throws FileNotFoundException {
		try {
			return !getAvailableSlaves().isEmpty();
		} catch (NoAvailableSlaveException e) {
			return false;
		}
	}

	/**
	 * Changes the size of the file.
	 * @param size
	 * @throws FileNotFoundException
	 */
	public void setSize(long size) throws FileNotFoundException {
		getInode().setSize(size);
	}

	@Override
	public boolean isDirectory() {
		return false;
	}

	@Override
	public boolean isFile() {
		return true;
	}

	@Override
	public boolean isLink() {
		return false;
	}

	@Override
	public void delete() throws FileNotFoundException {
		abortTransfers("File " + getPath() + " is being deleted");
		for (RemoteSlave rslave : getSlaves()) {
			rslave.simpleDelete(getPath());
		}
		super.delete();
	}
}
