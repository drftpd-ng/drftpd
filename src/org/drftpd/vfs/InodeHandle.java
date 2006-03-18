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

import org.drftpd.GlobalContext;
/**
 * @author zubov
 * @version $Id$
 */
public abstract class InodeHandle {
	String _path = null;

	public InodeHandle(String path) {
		_path = path;
	}
	
	public GlobalContext getGlobalContext() {
		return GlobalContext.getGlobalContext();
	}
	
	protected VirtualFileSystemInode getInode() throws FileNotFoundException {
		return VirtualFileSystem.getVirtualFileSystem().getInodeByPath(_path);
	}

	public String getPath() {
		return _path;
	}

	public String getName() {
		return VirtualFileSystem.getLast(_path);
	}

	public boolean isDirectory() throws FileNotFoundException {
		return getInode().isDirectory();
	}

	public long lastModified() throws FileNotFoundException {
		return getInode().getLastModified();
	}

	public boolean isFile() throws FileNotFoundException {
		return getInode().isFile();
	}

	public void delete() throws FileNotFoundException {
		getInode().delete();
	}

	public long getSize() throws FileNotFoundException {
		return getInode().getSize();
	}

	public String getUsername() throws FileNotFoundException {
		return getInode().getUsername();
	}

	public String getGroup() throws FileNotFoundException {
		return getInode().getGroup();
	}

	/**
	 * Throws IllegalStateException if this object is the RootDirectory
	 * @return
	 */
	public DirectoryHandle getParent() {
		if (_path.equals(VirtualFileSystem.pathSeparator)) {
			throw new IllegalStateException("Can't get the parent of the root directory");
		}
		return new DirectoryHandle(VirtualFileSystem.stripLast(getPath()));
	}

	/* (non-Javadoc)
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

	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		return _path.hashCode();
	}	

}
