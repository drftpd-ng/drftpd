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

import org.drftpd.GlobalContext;
import org.drftpd.dynamicdata.Key;
import org.drftpd.exceptions.ObjectNotFoundException;
import org.drftpd.master.RemoteTransfer;
import org.drftpd.slave.TransferFailedException;
import org.drftpd.stats.StatsInterface;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Lowest representation of a File object.
 */
public class VirtualFileSystemFile extends VirtualFileSystemInode implements StatsInterface  {

	public static final Key<Long> CRC = new Key<>(VirtualFileSystemFile.class, "checksum");

	public static final Key<Long> MD5 = new Key<>(VirtualFileSystemFile.class, "md5");

	public static final Key<Long> XFERTIME = new Key<>(VirtualFileSystemFile.class, "xfertime");
	
	protected Set<String> _slaves;
	
	
	public static final Key<Integer> DOWNLOADEDTIMES = new Key<>(VirtualFileSystemFile.class,
            "dltimes");
	
	public static final Key<Long> DOWNLOADDURATION = new Key<>(VirtualFileSystemFile.class,
            "dlduration");

	private transient Queue<RemoteTransfer> _uploads = new ConcurrentLinkedQueue<>();

	private transient Queue<RemoteTransfer> _downloads = new ConcurrentLinkedQueue<>();

	private long _size;

	/**
	 * @return a set of which slaves have this file.
	 */
	public Set<String> getSlaves() {
		synchronized (_slaves) {
			return new HashSet<>(_slaves);
		}
	}
	
	@Override
	public String toString() {
		StringBuilder ret = new StringBuilder();
		ret.append("File").append(super.toString()).append("[slaves=");
		for (String slave : getSlaves()) {
			ret.append(slave).append(",");
		}
		ret.replace(ret.length() - 1, ret.length(), "]");
		ret.append("[xfertime=").append(getXfertime()).append("]");
		return ret.toString();
	}

	public void setSlaves(Set<String> slaves) {
		_slaves = slaves;
	}

	public VirtualFileSystemFile(String username, String group, long size,
			String initialSlave) {
		this(username, group, size, new HashSet<>(Arrays
                .asList(initialSlave)));
	}

	public VirtualFileSystemFile(String username, String group, long size,
			Set<String> slaves) {
		super(username, group);
		setSize(size);
		_slaves = slaves;
	}

	/**
	 * Add a slave to the list of slaves that contain this file.
	 * @param rslave
	 */
	public void addSlave(String rslave) {
		boolean added;
		synchronized (_slaves) {
			added = _slaves.add(rslave);
		}
		if (added) {
			getParent().incrementSlaveRefCount(rslave);
			commit();
		
			getVFS().notifySlavesChanged(this, _slaves);
		}
	}

	/**
	 * @return the CRC32 of the file.
	 */
	public long getChecksum() {
		return getKeyedMap().getObjectLong(CRC);
	}

	/**
	 * @return the xfertime of the file.
	 */
	public long getXfertime() {
		return getKeyedMap().getObjectLong(XFERTIME);
	}

	/**
	 * Remove the slave from slave list.
	 * @param rslave
	 */
	public void removeSlave(String rslave) {
		boolean isEmpty;
		boolean removed;
		synchronized (_slaves) {
			removed = _slaves.remove(rslave);
			isEmpty = _slaves.isEmpty();
		}
		if (removed) {
			getParent().decrementSlaveRefCount(rslave);
		}
		if (isEmpty) {
			delete();
		} else if (removed) {
			commit();
			
			getVFS().notifySlavesChanged(this, _slaves);
		}
	}

	/**
	 * Changes the CRC32.
	 * @param checksum
	 */
	public void setChecksum(long checksum) {
		getKeyedMap().setObject(CRC, checksum);
		commit();
	}

	/**
	 * Changes the xfertime of the File.
	 * @param xfertime
	 */
	public void setXfertime(long xfertime) {
		getKeyedMap().setObject(XFERTIME, xfertime);
		commit();
	}

	/**
	 * Modifies the size of the File.
	 * @param size
	 */
	@Override
	public synchronized void setSize(long size) {
		if (_size != size) {
			if (size < 0) {
				throw new IllegalArgumentException("File size cannot be < 0");
			}
			if (getParent() == null) {
				// we haven't been assigned a parent yet
				_size = size;
			} else {
				getParent().addSize(size-_size); // adjust parent by difference.
				_size = size;
			}
			if (isInodeLoaded()) {
				commit();
				getVFS().notifySizeChanged(this,_size);
			}
		}
	}

	public boolean isUploading() {
		return isTransferring(_uploads);
	}
	
	public boolean isTransferring() {
		return isUploading() || isDownloading();
	}
	
	public void addUpload(RemoteTransfer transfer) {
		_uploads.add(transfer);
	}
	
	public void addDownload(RemoteTransfer transfer) {
		_downloads.add(transfer);
	}
	
	public void removeUpload(RemoteTransfer transfer) {
		_uploads.remove(transfer);
	}
	
	public void removeDownload(RemoteTransfer transfer) {
		_downloads.remove(transfer);
	}
	
	protected void abortTransfers(String reason) {
		abortUploads(reason);
		abortDownloads(reason);
	}
	
	protected void abortUploads(String reason) {
		abortTransfers(_uploads, reason);
	}
	
	protected void abortDownloads(String reason) {
		abortTransfers(_downloads, reason);
	}

	private void abortTransfers(Queue<RemoteTransfer> transfers, String reason) {
		for (RemoteTransfer transfer :  transfers) {
			transfer.abort(reason);
			transfers.remove(transfer);
		}
	}

	private boolean isTransferring(Queue<RemoteTransfer> transfers) {
		for (RemoteTransfer transfer : transfers) {
			try {
				if (!transfer.getTransferStatus().isFinished()) {
					return true;
				}
				// transfer is done
			} catch (TransferFailedException e) {
				// this one failed but another might be transferring
			}
		}
		return false;
	}
	
	public boolean isDownloading() {
		return isTransferring(_downloads);
	}

	public boolean isAvailable() {
		synchronized (_slaves) {
			for (String slave : _slaves) {
				try {
					if (GlobalContext.getGlobalContext().getSlaveManager()
							.getRemoteSlave(slave).isAvailable()) {
						return true;
					}
				} catch (ObjectNotFoundException e) {
					removeSlave(slave);
				}
			}
		}		
		return false;
	}

	public long getDownloadedBytes() {
		return getKeyedMap().getObjectInteger(DOWNLOADEDTIMES) * getSize() ;
	}

	public int getDownloadedFiles() {
		return getKeyedMap().getObjectInteger(DOWNLOADEDTIMES);
	}

	public long getDownloadedTime() {
		return getKeyedMap().getObjectLong(DOWNLOADDURATION);
	}

	/*
	 * (non-Javadoc)
	 * @see org.drftpd.stats.StatsInterface#getUploadedBytes()
	 * Useless since it's equals to getSize().
	 */
	public long getUploadedBytes() {
		return getSize();
	}

	/*
	 * (non-Javadoc)
	 * @see org.drftpd.stats.StatsInterface#getUploadedFiles()
	 * Useless since a file cannot be uploaded more than once.
	 */
	public int getUploadedFiles() {
		return 1;
	}

	/*
	 * (non-Javadoc)
	 * @see org.drftpd.stats.StatsInterface#getUploadedTime()
	 * Useless since it's equals to getXfertime().
	 */
	public long getUploadedTime() {
		return getXfertime();
	}

	public void setDownloadedBytes(long bytes) {}

	public void setDownloadedFiles(int files) {
		getKeyedMap().incrementInt(DOWNLOADEDTIMES);
		commit();
	}

	public void setDownloadedTime(long millis) {
		getKeyedMap().incrementLong(DOWNLOADDURATION);
		commit();
	}

	/*
	 * (non-Javadoc)
	 * @see org.drftpd.stats.StatsInterface#setUploadedBytes(long)
	 * Equals to file size.
	 */
	public void setUploadedBytes(long bytes) {}

	/*
	 * (non-Javadoc)
	 * @see org.drftpd.stats.StatsInterface#setUploadedFiles(int)
	 * Useless since a file cannot be uploaded more than once.
	 */
	public void setUploadedFiles(int files) {}

	/*
	 * (non-Javadoc)
	 * @see org.drftpd.stats.StatsInterface#setUploadedTime(long)
	 * Equals to setXfertime().
	 */
	public void setUploadedTime(long millis) {}

	@Override
	public long getSize() {
		return _size;
	}

	protected Map<String,AtomicInteger> getSlaveRefCounts() {
		Map<String,AtomicInteger> slaveRefCounts = new TreeMap<>();
		synchronized(_slaves) {
			for (String slave : _slaves) {
				slaveRefCounts.put(slave, new AtomicInteger(1));
			}
		}
		return slaveRefCounts;
	}
}
