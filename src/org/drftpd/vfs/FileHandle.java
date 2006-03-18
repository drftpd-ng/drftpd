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
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import net.sf.drftpd.FileExistsException;
import net.sf.drftpd.NoAvailableSlaveException;
import net.sf.drftpd.ObjectNotFoundException;

import org.drftpd.master.RemoteSlave;

/**
 * @author zubov
 * @version $Id$
 */
public class FileHandle extends InodeHandle {

	public FileHandle(String path) {
		super(path);
	}

	protected VirtualFileSystemFile getInode() throws FileNotFoundException {
		VirtualFileSystemInode inode = super.getInode();
		if (inode instanceof VirtualFileSystemLink) {
			return (VirtualFileSystemFile) inode;
		}
		throw new ClassCastException("FileHandle object pointing to Inode:"
				+ inode);
	}

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

	public void setCheckSum(long checksum) throws FileNotFoundException {
		getInode().setChecksum(checksum);
	}

	public void removeSlave(RemoteSlave sourceSlave)
			throws FileNotFoundException {
		getInode().removeSlave(sourceSlave.getName());
	}

	public long getCheckSum() throws NoAvailableSlaveException,
			FileNotFoundException {
		return getInode().getChecksum();
	}

	public void addSlave(RemoteSlave destinationSlave)
			throws FileNotFoundException {
		getInode().addSlave(destinationSlave.getName());
	}

	public long getXfertime() throws FileNotFoundException {
		return getInode().getXfertime();
	}

	public boolean isAvailable() throws FileNotFoundException {
		try {
			return !getAvailableSlaves().isEmpty();
		} catch (NoAvailableSlaveException e) {
			return false;
		}
	}
}
