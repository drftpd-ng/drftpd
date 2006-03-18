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
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.drftpd.GlobalContext;
import org.drftpd.master.RemoteSlave;
import org.drftpd.slave.CaseInsensitiveHashtable;

/**
 * @author zubov
 * @version $Id$
 */
public class DirectoryHandle extends InodeHandle {

	public DirectoryHandle(String path) {
		super(path);
	}

	protected VirtualFileSystemDirectory getInode()
			throws FileNotFoundException {
		VirtualFileSystemInode inode = super.getInode();
		if (inode instanceof VirtualFileSystemLink) {
			return (VirtualFileSystemDirectory) inode;
		}
		throw new ClassCastException(
				"DirectoryHandle object pointing to Inode:" + inode);
	}

	public Set<FileHandle> getFiles() throws FileNotFoundException {
		Set<FileHandle> set = new HashSet<FileHandle>();
		for (Iterator<InodeHandle> iter = getInode().getInodes().iterator(); iter
				.hasNext();) {
			InodeHandle handle = iter.next();
			if (handle instanceof FileHandle) {
				set.add((FileHandle) handle);
			}
		}
		return (Set<FileHandle>) set;
	}

	public Set<InodeHandle> getAllHandles() throws FileNotFoundException {
		return getInode().getInodes();
	}

	public Set<DirectoryHandle> getDirectories() throws FileNotFoundException {
		Set<DirectoryHandle> set = new HashSet<DirectoryHandle>();
		for (Iterator<InodeHandle> iter = getInode().getInodes().iterator(); iter
				.hasNext();) {
			InodeHandle handle = iter.next();
			if (handle instanceof DirectoryHandle) {
				set.add((DirectoryHandle) handle);
			}
		}
		return (Set<DirectoryHandle>) set;
	}

	public DirectoryHandle getDirectory(String name)
			throws FileNotFoundException {
		return null;
	}

	public FileHandle getFile(String string) throws FileNotFoundException {
		// TODO Auto-generated method stub
		return null;
	}

	public void remerge(CaseInsensitiveHashtable files, RemoteSlave rslave)
			throws IOException {
		// TODO Auto-generated method stub

	}

	public void unmergeDir(RemoteSlave rslave) {
		// TODO Auto-generated method stub

	}

	/**
	 * Creates a Directory object in the FileSystem with this path
	 * 
	 * @param user
	 * @param group
	 * @return
	 */
	public void createDirectory(String user, String group) {
		// TODO Auto-generated method stub

	}

	public boolean isRoot() {
		return equals(GlobalContext.getGlobalContext().getRoot());
	}
}
