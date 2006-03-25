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

/**
 * @author zubov
 * @version $Id$
 */
public abstract class InodeHandle implements InodeHandleInterface {
	String _path = null;
	protected static final Logger logger = Logger.getLogger(InodeHandle.class.getName());

	public InodeHandle(String path) {
		if (path == null || !path.startsWith(VirtualFileSystem.separator)) {
			throw new IllegalArgumentException("InodeHandle needs an absolute path, argument was [" + path + "]");
		}
		_path = path;
	}
	
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

	public boolean exists() {
		try {
			getInode();
			return true;
		} catch (FileNotFoundException e) {
			return false;
		}
	}

	public GlobalContext getGlobalContext() {
		return GlobalContext.getGlobalContext();
	}

	public String getGroup() throws FileNotFoundException {
		return getInode().getGroup();
	}

	protected VirtualFileSystemInode getInode() throws FileNotFoundException {
		return VirtualFileSystem.getVirtualFileSystem().getInodeByPath(_path);
	}

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

	public String getPath() {
		return _path;
	}

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

	public boolean isDirectory() throws FileNotFoundException {
		return getInode().isDirectory();
	}

	public boolean isFile() throws FileNotFoundException {
		return getInode().isFile();
	}

	public boolean isLink() throws FileNotFoundException {
		return getInode().isLink();
	}

	public long lastModified() throws FileNotFoundException {
		return getInode().getLastModified();
	}
	
	public String toString() {
		return getPath();
	}

	public void setUsername(String owner) throws FileNotFoundException {
		getInode().setUsername(owner);
	}

	public void setGroup(String group) throws FileNotFoundException {
		getInode().setGroup(group);
	}

	public void renameTo(InodeHandle toFile) throws FileExistsException, FileNotFoundException {
		getInode().rename(toFile.getPath());
	}

}
