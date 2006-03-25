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

import net.sf.drftpd.FileExistsException;

import org.drftpd.GlobalContext;
import org.drftpd.master.RemoteSlave;
import org.drftpd.slave.CaseInsensitiveHashtable;

/**
 * @author zubov
 * @version $Id$
 */
public class DirectoryHandle extends InodeHandle implements DirectoryHandleInterface {

	public DirectoryHandle(String path) {
		super(path);
	}

	
	@Override
	protected VirtualFileSystemDirectory getInode()
			throws FileNotFoundException {
		VirtualFileSystemInode inode = super.getInode();
		if (inode instanceof VirtualFileSystemDirectory) {
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

	public Set<InodeHandle> getInodeHandles() throws FileNotFoundException {
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
	
	public InodeHandle getInodeHandle(String name) throws FileNotFoundException {
		VirtualFileSystemInode inode = getInode().getInodeByName(name);
		if (inode.isDirectory()) {
			return new DirectoryHandle(inode.getPath());
		} else if (inode.isFile()) {
			return new FileHandle(inode.getPath());
		} else if (inode.isLink()) {
			return new LinkHandle(inode.getPath());
		}
		throw new IllegalStateException("Not a directory, file, or link -- punt");
	}

	public DirectoryHandle getDirectory(String name)
			throws FileNotFoundException, ObjectNotValidException {
		logger.debug("getDirectory(" + name + ")");
		if (name.equals("..")) {
			return getParent();
		} else if (name.equals(".")) {
			return this;
		}
		InodeHandle handle = getInodeHandle(name);
		if (handle.isDirectory()) {
			return (DirectoryHandle) handle;
		}
		throw new ObjectNotValidException(name + " is not a directory");
	}

	public FileHandle getFile(String name) throws FileNotFoundException, ObjectNotValidException {
		InodeHandle handle = getInodeHandle(name);
		if (handle.isFile()) {
			return (FileHandle) handle;
		}
		throw new ObjectNotValidException(name + " is not a file");
	}
	
	public LinkHandle getLink(String name) throws FileNotFoundException, ObjectNotValidException {
		InodeHandle handle = getInodeHandle(name);
		if (handle.isLink()) {
			return (LinkHandle) handle;
		}
		throw new ObjectNotValidException(name + " is not a link");
	}

	public void remerge(CaseInsensitiveHashtable files, RemoteSlave rslave)
			throws IOException {
		// TODO Auto-generated method stub
	}

	public void unmergeDir(RemoteSlave rslave) {
		// TODO Auto-generated method stub

	}

	/**
	 * Creates a Directory object in the FileSystem with this directory as its parent
	 * 
	 * @param user
	 * @param group
	 * @return
	 * @throws FileNotFoundException 
	 * @throws FileExistsException 
	 */
	public DirectoryHandle createDirectory(String name, String user, String group) throws FileExistsException, FileNotFoundException {
		getInode().createDirectory(name, user, group);
		try {
			return getDirectory(name);
		} catch (FileNotFoundException e) {
			throw new RuntimeException("somethin really funky happened, we just created it", e);
		} catch (ObjectNotValidException e) {
			throw new RuntimeException("somethin really funky happened, we just created it", e);
		}
	}
	/**
	 * Creates a File object in the FileSystem with this directory as its parent
	 *
	 */
	public FileHandle createFile(String name, String user, String group, RemoteSlave initialSlave) throws FileExistsException, FileNotFoundException {
		getInode().createFile(name, user, group, initialSlave.getName());
		try {
			return getFile(name);
		} catch (FileNotFoundException e) {
			throw new RuntimeException("somethin really funky happened, we just created it", e);
		} catch (ObjectNotValidException e) {
			throw new RuntimeException("somethin really funky happened, we just created it", e);
		}
	}
	
	/**
	 * Creates a File object in the FileSystem with this directory as its parent
	 *
	 */
	public LinkHandle createLink(String name, String target, String user, String group) throws FileExistsException, FileNotFoundException {
		getInode().createLink(name, target, user, group);
		try {
			return getLink(name);
		} catch (FileNotFoundException e) {
			throw new RuntimeException("somethin really funky happened, we just created it", e);
		} catch (ObjectNotValidException e) {
			throw new RuntimeException("somethin really funky happened, we just created it", e);
		}
	}
	
	public boolean isRoot() {
		return equals(GlobalContext.getGlobalContext().getRoot());
	}

	public FileHandle getNonExistentFileHandle(String argument) {
		if (argument.startsWith(VirtualFileSystem.separator)) {
			// absolute path, easy to handle
			return new FileHandle(argument);
		}
		// path must be relative
		return new FileHandle(getPath() + VirtualFileSystem.separator + argument);
	}
}
