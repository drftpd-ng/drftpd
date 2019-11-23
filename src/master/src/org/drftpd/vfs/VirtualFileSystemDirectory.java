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

import org.drftpd.exceptions.FileExistsException;

import java.io.FileNotFoundException;
import java.lang.ref.SoftReference;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * Lowest representation of a directory.<br>
 * This class deals directly w/ the actual VFS and should not be used
 * outside the of the VirtualFileSystem part.
 * @see org.drftpd.vfs.DirectoryHandle
 */
public class VirtualFileSystemDirectory extends VirtualFileSystemInode {

	private transient TreeMap<String, SoftReference<VirtualFileSystemInode>> _files = 
		new CaseInsensitiveTreeMap<String, SoftReference<VirtualFileSystemInode>>();

	private boolean _placeHolderLastModified;

	protected long _size = 0;

	private Map<String,AtomicInteger> _slaveRefCounts = new TreeMap<>();

	public VirtualFileSystemDirectory(String user, String group) {
		super(user, group);
	}

	protected VirtualFileSystemDirectory(String user, String group, boolean placeHolderLastModified) {
		super(user,group);
		_placeHolderLastModified = placeHolderLastModified;
	}

	/**
	 * Add another inode to the directory tree.
	 * @param inode
	 */
	protected synchronized void addChild(VirtualFileSystemInode inode, boolean updateLastModified) {
		_files.put(inode.getName(), new SoftReference<>(
                inode));
		if (updateLastModified && 
				(getLastModified() < inode.getLastModified() || _placeHolderLastModified)) {
			setLastModified(inode.getLastModified());
		}
		if (getCreationTime() > inode.getLastModified() ||
                getCreationTime() > inode.getCreationTime()) {
			setCreationTime(inode.getCreationTime() > inode.getLastModified() ? inode.getLastModified() : inode.getCreationTime());
		}
		addSize(inode.getSize());
		addChildSlaveRefCounts(inode, inode.getSlaveRefCounts());
	}

	protected synchronized void addSize(long l) {
		if (l != 0L) {
			_size = getSize() + l;
			getParent().addSize(l);
			commit();
			getVFS().notifySizeChanged(this,_size);
		}
	}

	/**
	 * Create a directory inside the current Directory.
	 * @param name
	 * @param user
	 * @param group
	 * @throws FileExistsException if this directory already exists.
	 */
	public synchronized void createDirectory(String name, String user,
			String group) throws FileExistsException {
		createDirectory(name, user, group, false);
	}

	/**
	 * Create a directory inside the current Directory.
	 * @param name
	 * @param user
	 * @param group
	 * @param placeHolderLastModified
	 * @throws FileExistsException if this directory already exists.
	 */
	protected synchronized void createDirectory(String name, String user,
			String group, boolean placeHolderLastModified) throws FileExistsException {
		if (_files.containsKey(name)) {
			throw new FileExistsException("An object named " + name
					+ " already exists in " + getPath());
		}
		VirtualFileSystemDirectory inode = createDirectoryRaw(name, user, group, placeHolderLastModified);

		getVFS().notifyInodeCreated(inode);
	}

	/**
	 * Do not use this method unless you REALLY know what you're doing. This
	 * method should only be used in two cases, in the createDirectory(string,
	 * string, string) method of this class and in VirtualFileSystem.loadInode()
	 * 
	 * @param name
	 * @param user
	 * @param group
	 * @return the created directory
	 */
	protected VirtualFileSystemDirectory createDirectoryRaw(String name, String user, String group) {
		return createDirectoryRaw(name, user, group, false);
	}

	/**
	 * Do not use this method unless you REALLY know what you're doing. This
	 * method should only be used in two cases, in the createDirectory(string,
	 * string, string) method of this class and in VirtualFileSystem.loadInode()
	 * 
	 * @param name
	 * @param user
	 * @param group
	 * @param placeHolderLastModified
	 * @return the created directory
	 */
	protected VirtualFileSystemDirectory createDirectoryRaw(String name, String user,
			String group, boolean placeHolderLastModified) {
		VirtualFileSystemDirectory inode = new VirtualFileSystemDirectory(user,
				group, placeHolderLastModified);
		inode.setName(name);
		inode.setParent(this);
		inode.inodeLoadCompleted();
		inode.commit();
		addChild(inode, !placeHolderLastModified);
        logger.info("createDirectory({})", inode);

		return inode;
	}

	/**
	 * Create a file inside the current directory.
	 * @param name
	 * @param user
	 * @param group
	 * @param initialSlave
	 * @throws FileExistsException if this file already exists.
	 */
	public synchronized void createFile(String name, String user, String group,
			String initialSlave) throws FileExistsException {
		createFile(name, user, group, initialSlave, 0L, false, 0L);
	}

	/**
	 * Create a file inside the current directory.
	 * @param name
	 * @param user
	 * @param group
	 * @param initialSlave
	 * @param size
	 * @throws FileExistsException if this file already exists.
	 */
	public synchronized void createFile(String name, String user, String group,
			String initialSlave, long size) throws FileExistsException {
		createFile(name, user, group, initialSlave, 0L, false, size);
	}

	/**
	 * Create a file inside the current directory.
	 * @param name
	 * @param user
	 * @param group
	 * @param initialSlave
	 * @param lastModified
	 * @param setLastModified
	 * @param size
	 * @throws FileExistsException if this file already exists.
	 */
	protected synchronized void createFile(String name, String user, String group,
			String initialSlave, long lastModified, boolean setLastModified, long size) throws FileExistsException {
		if (_files.containsKey(name)) {
			throw new FileExistsException(name + " already exists");
		}
		VirtualFileSystemInode inode = new VirtualFileSystemFile(user, group,
				size, initialSlave);
		inode.setName(name);
		inode.setParent(this);
		if (setLastModified) {
			if (inode.getCreationTime() > lastModified) {
				inode.setCreationTime(lastModified);
			}
			inode.setLastModified(lastModified);
		}
		inode.commit();
		inode.inodeLoadCompleted();
		addChild(inode, true);
		commit();
        logger.info("createFile({})", inode);

		getVFS().notifyInodeCreated(inode);
	}

	/**
	 * Create a link inside the current directory.
	 * @param name
	 * @param target
	 * @param user
	 * @param group
	 * @throws FileExistsException if this link already exists.
	 */
	public synchronized void createLink(String name, String target,
			String user, String group) throws FileExistsException {
		if (_files.containsKey(name)) {
			throw new FileExistsException(name + " already exists");
		}
		VirtualFileSystemInode inode = new VirtualFileSystemLink(user, group,
				target);
		inode.setName(name);
		inode.setParent(this);
		inode.commit();
		inode.inodeLoadCompleted();
		addChild(inode, true);
		commit();
        logger.info("createLink({})", inode);

		getVFS().notifyInodeCreated(inode);
	}

	/**
	 * @return a Set containing all inode names inside this directory.
	 */
	public synchronized Set<String> getInodeNames() {
		return new HashSet<>(_files.keySet());
	}

	/**
	 * @return a set containing all Inode objects inside this directory.
	 */
	public Set<InodeHandle> getInodes() {
		HashSet<InodeHandle> set = new HashSet<>();
		String path = getPath() + (getPath().equals("/") ? "" : VirtualFileSystem.separator);
		// not dynamically called for efficiency
		HashSet<String> inodeKeys = null;
		synchronized (this) {
			inodeKeys = new HashSet<>(_files.keySet());
		}
		for (String inodeName : inodeKeys) {
			VirtualFileSystemInode inode = null;
			try {
				inode = getInodeByName(inodeName);
			} catch (FileNotFoundException e) {
				// This entry is already removed from the REAL _files Set, but we're iterating over a copy
				continue;
			}
			if (inode.isDirectory()) {
				set.add(new DirectoryHandle(path + inodeName));
			} else if (inode.isFile()) {
				set.add(new FileHandle(path + inodeName));
			} else if (inode.isLink()) {
				set.add(new LinkHandle(path + inodeName));
			}
		}
		return set;

	}

	/**
	 * @param name
	 * @return VirtualFileSystemInode object if 'name' exists on the dir.
	 * @throws FileNotFoundException
	 */
	protected VirtualFileSystemInode getInodeByName(String name)
	throws FileNotFoundException {
		name = VirtualFileSystem.fixPath(name);
		if (name.startsWith(VirtualFileSystem.separator)) {
			return VirtualFileSystem.getVirtualFileSystem().getInodeByPath(name);
		}
		if (name.contains(VirtualFileSystem.separator)) {
			return VirtualFileSystem.getVirtualFileSystem().getInodeByPath(
					getPath() + VirtualFileSystem.separator + name);
		}
		if (name.equals("..")) {
			return getParent();
		}
		if (name.equals(".")) {
			return this;
		}
		VirtualFileSystemInode inode = null;
		synchronized (this) {
			if (!_files.containsKey(name)) {
				throw new FileNotFoundException("FileNotFound: " + name + " does not exist");
			}
			SoftReference<VirtualFileSystemInode> sf = _files.get(name);
			if (sf != null) {
				inode = sf.get();
			}
			if (inode == null) {
				// The next line is so that we load the file from disk using the casing of the name
				// stored against the parent directory not the casing passed by the caller
				name = _files.ceilingKey(name);
				inode = getVFS().loadInode(
						getPath() + VirtualFileSystem.separator + name);
				inode.setParent(this);
				// _files.remove(name);
				// Map instance replaces what is previously there with put()
				_files.put(name, new SoftReference<>(inode));
			}
		}
		return inode;
	}

	/**
	 * Remove the inode from the directory tree.
	 * 
	 * @param child
	 */
	protected synchronized void removeChild(VirtualFileSystemInode child) {
		addSize(-child.getSize());
		removeChildSlaveRefCounts(child, child.getSlaveRefCounts());
		removeMissingChild(child.getName());
	}

	/**
	 * Changes the directory tree.
	 * @param files
	 */
	public synchronized void setFiles(String[] files) {
		for (String file : files) {
			_files.put(file, null);
		}
	}

	@Override
	public String toString() {
		return "Directory" + super.toString();
	}

	@Override
	public long getSize() {
		return _size;
	}

	@Override
	public void setSize(long l) {
		if (_size != l) {
			_size = l;
			if (isInodeLoaded()) {
				commit();
				getVFS().notifySizeChanged(this,_size);
			}
		}
	}

	public boolean getPlaceHolderLastModified() {
		return _placeHolderLastModified;
	}

	public void setPlaceHolderLastModified(boolean placeHolderLastModified) {
		_placeHolderLastModified = placeHolderLastModified;
	}

	public synchronized void removeMissingChild(String name) {
		if (_files.remove(name) != null) {
			setLastModified(System.currentTimeMillis());
			commit();
		}
	}

	@Override
	public void setLastModified(long modified) {
		if (isInodeLoaded()) {
			_placeHolderLastModified = false;
		}
		super.setLastModified(modified);
	}

	protected synchronized void compareAndUpdateLastModified(long lastModified) {
		if (getLastModified() < lastModified || _placeHolderLastModified) {
			setLastModified(lastModified);
		}
	}

	public void setSlaveRefCounts(Map<String,AtomicInteger> slaveRefCounts) {
		_slaveRefCounts = slaveRefCounts;
	}

	public Map<String,AtomicInteger> getSlaveRefCounts() {
		synchronized (_slaveRefCounts) {
			return new TreeMap<>(_slaveRefCounts);
		}
	}

	protected void addChildSlaveRefCounts(VirtualFileSystemInode childInode, Map<String,AtomicInteger> childRefCounts) {
		if (!childRefCounts.isEmpty()) {
			for (Map.Entry<String,AtomicInteger> refEntry : childRefCounts.entrySet()) {
				AtomicInteger currentCount;
				synchronized (_slaveRefCounts) {
					currentCount = _slaveRefCounts.get(refEntry.getKey());
					if (currentCount == null) {
						currentCount = new AtomicInteger(0);
						_slaveRefCounts.put(refEntry.getKey(), currentCount);
					}
				}
				currentCount.addAndGet(refEntry.getValue().intValue());
			}
			if (!isRoot()) {
				getParent().addChildSlaveRefCounts(childInode, childRefCounts);
			}
		}
		commit();
	}

	protected void removeChildSlaveRefCounts(VirtualFileSystemInode childInode, Map<String,AtomicInteger> childRefCounts) {
		if (!childRefCounts.isEmpty()) {
			for (Map.Entry<String,AtomicInteger> refEntry : childRefCounts.entrySet()) {
				AtomicInteger currentCount;
				currentCount = _slaveRefCounts.get(refEntry.getKey());
				if (currentCount == null) {
					// Shouldn't happen since we're removing a child, therefore we should have
					// counts for the slaves referenced by the child
                    logger.error("Removing child {} from {} child contained a count of {} for slave {} but the slave has no count against this directory", childInode.getPath(), getPath(), refEntry.getValue().intValue(), refEntry.getKey());
					continue;
				}
				currentCount.addAndGet(-refEntry.getValue().intValue());
			}
			if (!isRoot()) {
				getParent().removeChildSlaveRefCounts(childInode, childRefCounts);
			}
		}
		commit();
	}

	protected void incrementSlaveRefCount(String slave) {
		AtomicInteger currentCount;
		synchronized (_slaveRefCounts) {
			currentCount = _slaveRefCounts.get(slave);
			if (currentCount == null) {
				currentCount = new AtomicInteger(0);
				_slaveRefCounts.put(slave, currentCount);
			}
		}
		currentCount.incrementAndGet();
		if (!isRoot()) {
			getParent().incrementSlaveRefCount(slave);
		}
		commit();
	}

	protected void decrementSlaveRefCount(String slave) {
		AtomicInteger currentCount;
		synchronized (_slaveRefCounts) {
			currentCount = _slaveRefCounts.get(slave);
			if (currentCount == null) {
				currentCount = new AtomicInteger(0);
				_slaveRefCounts.put(slave, currentCount);
			}
		}
		currentCount.decrementAndGet();
		if (!isRoot()) {
			getParent().decrementSlaveRefCount(slave);
		}
		commit();
	}

	protected boolean isRoot() {
		return false;
	}

	protected int getRefCountForSlave(String slave) {
		AtomicInteger slaveCount = _slaveRefCounts.get(slave);
		if (slaveCount == null) {
			return 0;
		}
		return slaveCount.get();
	}

	protected void recalcSlaveRefCounts() {
		TreeMap<String,AtomicInteger> updCounts = new TreeMap<>();
		for (InodeHandle inode : getInodes()) {
			if (inode.isDirectory()) {
				try {
					((DirectoryHandle)inode).recalcSlaveRefCounts();
				} catch (FileNotFoundException e) {
					// Dir has been deleted, skip it
					continue;
				}
			}
			try {
				Map<String,AtomicInteger> inodeCounts = inode.getSlaveRefCounts();
				for (String slave : inodeCounts.keySet()) {
					AtomicInteger currCount = updCounts.get(slave);
					if (currCount == null) {
						currCount = inodeCounts.get(slave);
					} else {
						currCount.addAndGet(inodeCounts.get(slave).get());
					}
					updCounts.put(slave, currCount);
				}
			} catch (FileNotFoundException e) {
				// Inode has been deleted, skip it
            }
		}
		synchronized (_slaveRefCounts) {
			_slaveRefCounts.clear();
			_slaveRefCounts.putAll(updCounts);
		}
		commit();
	}
}
