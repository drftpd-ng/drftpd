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

import java.beans.DefaultPersistenceDelegate;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.beans.XMLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.drftpd.GlobalContext;
import org.drftpd.dynamicdata.Key;
import org.drftpd.exceptions.ObjectNotFoundException;
import org.drftpd.master.RemoteTransfer;
import org.drftpd.slave.TransferFailedException;
import org.drftpd.stats.StatsInterface;

/**
 * Lowest representation of a File object.
 */
public class VirtualFileSystemFile extends VirtualFileSystemInode implements StatsInterface  {

	protected static final Collection<String> transientListFile = Arrays
			.asList(new String[] { "name", "parent", "xfertime", "checksum",
					"downloadedBytes", "downloadedFiles", "downloadedTime",
					"uploadedBytes", "uploadedFiles", "uploadedTime"});
	// the following fields AREN'T transient, they're simply stored in the
	// KeyedMap instead of as their own independent javabeans element
	//
	// xfertime, checksum, downloadedBytes, downloadedFiles, downloadedTime,
	// uploadedBytes, uploadedFiles, uploadedTime

	public static final Key<Long> CRC = new Key<Long>(VirtualFileSystemFile.class, "checksum");

	public static final Key<Long> MD5 = new Key<Long>(VirtualFileSystemFile.class, "md5");

	public static final Key<Long> XFERTIME = new Key<Long>(VirtualFileSystemFile.class,	"xfertime");
	
	protected Set<String> _slaves;
	
	
	public static final Key<Integer> DOWNLOADEDTIMES = new Key<Integer>(VirtualFileSystemFile.class, 
			"dltimes");
	
	public static final Key<Long> DOWNLOADDURATION = new Key<Long>(VirtualFileSystemFile.class, 
			"dlduration");
	
	private transient ArrayList<RemoteTransfer> _uploads = new ArrayList<RemoteTransfer>(1);
	
	private transient ArrayList<RemoteTransfer> _downloads = new ArrayList<RemoteTransfer>(1);

	private long _size;

	/**
	 * @return a set of which slaves have this file.
	 */
	public Set<String> getSlaves() {
		synchronized (_slaves) {
			return new HashSet<String>(_slaves);
		}
	}
	
	@Override
	public String toString() {
		StringBuffer ret = new StringBuffer();
		ret.append("File" + super.toString() + "[slaves=");
		for (String slave : getSlaves()) {
			ret.append(slave + ",");
		}
		ret.replace(ret.length() - 1, ret.length(), "]");
		ret.append("[xfertime="+getXfertime()+"]");
		return ret.toString();
	}

	public void setSlaves(Set<String> slaves) {
		_slaves = slaves;
	}

	public VirtualFileSystemFile(String username, String group, long size,
			String initialSlave) {
		this(username, group, size, new HashSet<String>(Arrays
				.asList(new String[] { initialSlave })));
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
	 * Configure the serialization of the File.
	 */
	@Override
	protected void setupXML(XMLEncoder enc) {
		super.setupXML(enc);
		
		PropertyDescriptor[] pdArr;
		try {
			pdArr = Introspector.getBeanInfo(VirtualFileSystemFile.class)
					.getPropertyDescriptors();
		} catch (IntrospectionException e) {
			logger.error("I don't know what to do here", e);
			throw new RuntimeException(e);
		}
		for (int x = 0; x < pdArr.length; x++) {
			// logger.debug("PropertyDescriptor - VirtualFileSystemFile - "
			// + pdArr[x].getDisplayName());
			if (transientListFile.contains(pdArr[x].getName())) {
				pdArr[x].setValue("transient", Boolean.TRUE);
			}
		}
		// needed to create a VFSFile object during unserialization
		enc.setPersistenceDelegate(VirtualFileSystemFile.class,
				new DefaultPersistenceDelegate(new String[] { "username",
						"group", "size", "slaves" }));
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
		if (size < 0) {
			throw new IllegalArgumentException("File size cannot be < 0");
		}
		if (getParent() == null) {
			// we haven't been assigned a parent yet
			_size = size;
		} else {
			getParent().addSize(-_size); // removing old size from parent.
			_size = size;
			getParent().addSize(_size);
		}
	}

	public boolean isUploading() {
		return isTransferring(_uploads);
	}
	
	public boolean isTransferring() {
		return isUploading() || isDownloading();
	}
	
	public void addUpload(RemoteTransfer transfer) {
		synchronized (_uploads) {
			_uploads.add(transfer);
		}
	}
	
	public void addDownload(RemoteTransfer transfer) {
		synchronized (_downloads) {
			_downloads.add(transfer);
		}
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
	
	private void abortTransfers(ArrayList<RemoteTransfer> transfers, String reason) {
		synchronized (transfers) {
			for (Iterator<RemoteTransfer> iter = transfers.iterator(); iter.hasNext();) {
				RemoteTransfer transfer = iter.next();
				transfer.abort(reason);
				iter.remove();
			}
		}
	}
	
	private boolean isTransferring(ArrayList<RemoteTransfer> transfers) {
		synchronized (transfers) {
			for (Iterator<RemoteTransfer> iter = transfers.iterator(); iter.hasNext();) {
				RemoteTransfer transfer = iter.next();
				try {
					if (!transfer.getTransferStatus().isFinished()) {
						return true;
					}
					// transfer is done
				} catch (TransferFailedException e) {
					// this one failed but another might be transferring
					
				}
				// transfer is done or failed, let's remove it from the list so we don't have to iterate over it anymore
				iter.remove();
			}
			return false;
		}
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

	public void setDownloadedBytes(long bytes) {
		return;	
	}

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
	public void setUploadedBytes(long bytes) {
		return;
	}

	/*
	 * (non-Javadoc)
	 * @see org.drftpd.stats.StatsInterface#setUploadedFiles(int)
	 * Useless since a file cannot be uploaded more than once.
	 */
	public void setUploadedFiles(int files) {
		return;
	}

	/*
	 * (non-Javadoc)
	 * @see org.drftpd.stats.StatsInterface#setUploadedTime(long)
	 * Equals to setXfertime().
	 */
	public void setUploadedTime(long millis) {
		return;
	}

	@Override
	public long getSize() {
		return _size;
	}

}
