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
package org.drftpd.vfs.event;

import org.drftpd.dynamicdata.Key;
import org.drftpd.dynamicdata.KeyNotFoundException;
import org.drftpd.vfs.DirectoryHandle;
import org.drftpd.vfs.VirtualFileSystem;
import org.drftpd.vfs.VirtualFileSystemFile;
import org.drftpd.vfs.VirtualFileSystemInode;

import java.util.HashSet;
import java.util.Set;

/**
 * @author djb61
 * @version $Id$
 */
public class ImmutableInodeHandle {

	private VirtualFileSystemInode _inode;
	
	private String _path;
	
	public ImmutableInodeHandle(VirtualFileSystemInode inode, String path) {
		_inode = inode;
		_path = path;
	}
	
	public String getGroup() {
		return _inode.getGroup();
	}
	
	public long getLastModified() {
		return _inode.getLastModified();
	}
	
	public String getName() {
		return _inode.getName();
	}
	
	public DirectoryHandle getParent() {
		if (_path.equals(VirtualFileSystem.separator)) {
			throw new IllegalStateException("Can't get the parent of the root directory");
		}
		return new DirectoryHandle(VirtualFileSystem.stripLast(getPath()));
	}

	public String getPath() {
		return _path;
	}
	
	public <T> T getPluginMetaData(Key<T> key) throws KeyNotFoundException {
		return _inode.getPluginMetaData(key);
	}
	
	public long getSize() {
		return _inode.getSize();
	}
	
	public Set<String> getSlaveNames() throws UnsupportedOperationException {
		if (isFile()) {
			return new HashSet<>(((VirtualFileSystemFile) _inode).getSlaves());
		}
		throw new UnsupportedOperationException("Slaves can only be retrieved from file inodes");
	}
	
	public <T> T getUntypedPluginMetaData(String key, Class<T> clazz) {
		return _inode.getUntypedPluginMetaData(key);
	}
	
	public String getUsername() {
		return _inode.getUsername();
	}
	
	public boolean isDirectory() {
		return _inode.isDirectory();
	}
	
	public boolean isFile() {
		return _inode.isFile();
	}
	
	public boolean isLink() {
		return _inode.isLink();
	}
	
	public long lastModified() {
		return _inode.getLastModified();
	}
}
