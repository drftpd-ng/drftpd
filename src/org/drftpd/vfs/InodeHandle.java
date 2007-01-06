/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 *
 * DrFTPD is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * DrFTPD; if not, write to the Free Software Foundation, Inc., 59 Temple Place,
 * Suite 330, Boston, MA 02111-1307 USA
 */
package org.drftpd.vfs;

import java.io.FileNotFoundException;

import net.sf.drftpd.FileExistsException;

import org.apache.log4j.Logger;
import org.drftpd.GlobalContext;
import org.drftpd.master.RemoteSlave;

/**
 * @author zubov
 * @version $Id$
 */
public abstract class InodeHandle implements InodeHandleInterface, Comparable {
	String _path = null;
	protected static final Logger logger = Logger.getLogger(InodeHandle.class.getName());
	
	/**
	 * Creates an InodleHandle for the given path.
	 * @param path
	 */
	public InodeHandle(String path) {
		if (path == null || !path.startsWith(VirtualFileSystem.separator)) {
			throw new IllegalArgumentException("InodeHandle needs an absolute path, argument was [" + path + "]");
		}
		_path = path;
	}
	
	public int compareTo(Object o) {
		InodeHandle handle = (InodeHandle) o;
		return(handle._path.compareTo(_path));
	}
	
	/**
	 * Delete the inode.
	 * @throws FileNotFoundException
	 */
	public void delete() throws FileNotFoundException {
		getInode().delete();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object arg0) {
		if (!(arg0 instanceof InodeHandle)) {
			return false;
		}
		InodeHandle compareMe = (InodeHandle) arg0;
		return _path.equalsIgnoreCase(compareMe._path);

	}

	/**
	 * @return true if the Inode exists or false if it doesnt exists.
	 */
	public boolean exists() {
		try {
			getInode();
			return true;
		} catch (FileNotFoundException e) {
			return false;
		}
	}

	/**
	 * @return shortcurt to access the GlobalContext.
	 */
	public static GlobalContext getGlobalContext() {
		return GlobalContext.getGlobalContext();
	}

	/**
	 * @return the group that owns the Inode.
	 */
	public String getGroup() throws FileNotFoundException {
		return getInode().getGroup();
	}

	/**
	 * Call the lowest level of the VFS and ask it to search the Inode.
	 * @return a VirtualFileSystemInode object.
	 * @throws FileNotFoundException if the inode does not exist.
	 */
	protected VirtualFileSystemInode getInode() throws FileNotFoundException {
		return VirtualFileSystem.getVirtualFileSystem().getInodeByPath(_path);
	}

	/**
	 * Return the Inode name.
	 */
	public String getName() {
		return VirtualFileSystem.getLast(_path);
	}

	/**
	 * Throws IllegalStateException if this object is the RootDirectory
	 * 
	 * @return
	 */
	public DirectoryHandle getParent() {
		if (_path.equals(VirtualFileSystem.separator)) {
			throw new IllegalStateException(
					"Can't get the parent of the root directory");
		}
		return new DirectoryHandle(VirtualFileSystem.stripLast(getPath()));
	}

	/**
	 * @return the full path of the Inode.
	 */
	public String getPath() {
		return _path;
	}

	/**
	 * @return The size (in bytes) of the Inode.
	 */
	public long getSize() throws FileNotFoundException {
		return getInode().getSize();
	}

	public String getUsername() throws FileNotFoundException {
		return getInode().getUsername();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		return _path.hashCode();
	}

	public abstract boolean isDirectory();

	public abstract boolean isFile();

	public abstract boolean isLink();

	public long lastModified() throws FileNotFoundException {
		return getInode().getLastModified();
	}
	
	public String toString() {
		try {
			return getInode().toString();
		} catch (FileNotFoundException e) {
			return getPath() + "-FileNotFound";
		}
	}

	/**
	 * Changes the user who owns the Inode.
	 * @param owner
	 * @throws FileNotFoundException, if the Inode does not exist.
	 */
	public void setUsername(String owner) throws FileNotFoundException {
		getInode().setUsername(owner);
	}

	/**
	 * Changes the group which owns the Inode.
	 * @param group
	 * @throws FileNotFoundException, if the Inode does not exist.
	 */
	public void setGroup(String group) throws FileNotFoundException {
		getInode().setGroup(group);
	}

	/**
	 * Renames the Inode.
	 * @param toInode
	 * @throws FileExistsException if the destination inode already exists.
	 * @throws FileNotFoundException if the source inode does not exist.
	 */
	public void renameTo(InodeHandle toInode) throws FileExistsException, FileNotFoundException {
		getInode().rename(toInode.getPath());
	}

	/**
	 * Remove the slave from the slave list.
	 * @param rslave
	 * @throws FileNotFoundException
	 */
	public abstract void removeSlave(RemoteSlave rslave) throws FileNotFoundException;
	
	/**
	 * Set when it was last modified.
	 * @param l
	 * @throws FileNotFoundException
	 */
	public void setLastModified(long l) throws FileNotFoundException {
		getInode().setLastModified(l);
	}

}
