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

import java.beans.XMLEncoder;
import java.io.FileNotFoundException;

import net.sf.drftpd.FileExistsException;

import org.apache.log4j.Logger;
import org.drftpd.dynamicdata.Key;
import org.drftpd.dynamicdata.KeyedMap;

import se.mog.io.PermissionDeniedException;

public abstract class VirtualFileSystemInode {

	protected static final Logger logger = Logger
			.getLogger(VirtualFileSystemInode.class.getName());

	public static VirtualFileSystem getVFS() {
		return VirtualFileSystem.getVirtualFileSystem();
	}

	protected String _group;

	protected KeyedMap<Key, Object> _keyedMap = new KeyedMap<Key, Object>();

	protected transient long _lastModified;

	protected transient String _name;

	protected transient VirtualFileSystemDirectory _parent;

	protected long _size = 0;

	protected String _username;

	public VirtualFileSystemInode(String user, String group, long size) {
		_username = user;
		_group = group;
		_size = size;
		_lastModified = System.currentTimeMillis();
	}

	/**
	 * Need to ensure that this is called after each (non-transient) change to
	 * the Inode
	 */
	protected void commit() {
		VirtualFileSystem.getVirtualFileSystem().writeInode(this);
	}

	/**
	 * Deletes a file, directory, or link, RemoteSlave handles issues with
	 * slaves being offline and queued deletes
	 */
	public void delete() {
		logger.info("delete(" + this + ")");
		// GlobalContext.getGlobalContext().getSlaveManager().deleteOnAllSlaves(this);
		VirtualFileSystem.getVirtualFileSystem().deleteXML(getPath());
		_parent.removeChild(this);
		_parent.addSize(-getSize());
	}

	/**
	 * @return Returns the _group.
	 */
	public String getGroup() {
		return _group;
	}

	public KeyedMap<Key, Object> getKeyedMap() {
		return _keyedMap;
	}

	/**
	 * @return Returns the _lastModified.
	 */
	public long getLastModified() {
		return _lastModified;
	}

	public String getName() {
		return _name;
	}

	public VirtualFileSystemDirectory getParent() {
		return _parent;
	}

	/**
	 * @return Returns the full path.
	 */
	protected String getPath() {
		if (getParent().getPath().equals(VirtualFileSystem.pathSeparator)) {
			return VirtualFileSystem.pathSeparator + getName();
		}
		return getParent().getPath() + VirtualFileSystem.pathSeparator
				+ getName();
	}

	/**
	 * @return Returns the size.
	 */
	public long getSize() {
		return _size;
	}

	/**
	 * @return Returns the user.
	 */
	public String getUsername() {
		return _username;
	}

	public boolean isDirectory() {
		return this instanceof VirtualFileSystemDirectory;
	}

	public boolean isFile() {
		return this instanceof VirtualFileSystemFile;
	}

	public boolean isLink() {
		return this instanceof VirtualFileSystemLink;
	}

	protected void rename(String destination) throws FileExistsException {
		if (!destination.startsWith(VirtualFileSystem.pathSeparator)) {
			throw new IllegalArgumentException("Accepts a full path and name");
		}
		try {
			getVFS().getInodeByPath(destination);
			throw new FileExistsException("Destination exists");
		} catch (FileNotFoundException e) {
			// This is good
		}
		VirtualFileSystemDirectory destinationDir = null;
		try {
			destinationDir = (VirtualFileSystemDirectory) getVFS()
					.getInodeByPath(VirtualFileSystem.stripLast(destination));
		} catch (FileNotFoundException e) {
			throw new RuntimeException(
					"Error in logic, this should not happen", e);
		}
		String fileString = "rename(" + this;
		_parent.removeChild(this);
		try {
			VirtualFileSystem.getVirtualFileSystem().renameXML(
					this.getPath(),
					destinationDir.getPath() + VirtualFileSystem.pathSeparator
							+ VirtualFileSystem.getLast(destination));
		} catch (FileNotFoundException e) {
			// we may be able to handle this
			throw new RuntimeException("FileSystemError", e);
		} catch (PermissionDeniedException e) {
			throw new RuntimeException("FileSystemError", e);
		}
		_name = VirtualFileSystem.getLast(destination);
		destinationDir.addChild(this);
		fileString = fileString + ",(" + this + ")";
		logger.info(fileString);
	}

	/**
	 * @param group
	 *            The group to set.
	 */
	public void setGroup(String group) {
		_group = group;
		commit();
	}

	public void setKeyedMap(KeyedMap<Key, Object> data) {
		_keyedMap = data;
	}

	/**
	 * @param The
	 *            lastModified to set.
	 */
	public void setLastModified(long modified) {
		_lastModified = modified;
	}

	/**
	 * @param The
	 *            name to set.
	 */
	public void setName(String name) {
		_name = name;
	}

	public void setParent(VirtualFileSystemDirectory directory) {
		_parent = directory;
	}

	protected abstract void setupXML(XMLEncoder enc);

	/**
	 * @param user
	 *            The user to set.
	 */
	public void setUsername(String user) {
		_username = user;
		commit();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		StringBuffer ret = new StringBuffer();
		ret.append("[path=" + getPath() + "]");
		ret.append("[user,group=" + getUsername() + "," + getGroup() + "]");
		ret.append("[lastModified=" + getLastModified() + "]");
		ret.append("[size=" + getSize() + "]");
		return ret.toString();
	}
}
