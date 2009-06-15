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
import java.util.Set;

import org.apache.log4j.Logger;
import org.drftpd.GlobalContext;
import org.drftpd.dynamicdata.Key;
import org.drftpd.dynamicdata.KeyNotFoundException;
import org.drftpd.exceptions.FileExistsException;
import org.drftpd.exceptions.ObjectNotFoundException;
import org.drftpd.master.RemoteSlave;
import org.drftpd.master.SlaveManager;
import org.drftpd.usermanager.User;
import org.drftpd.vfs.perms.VFSPermissions;

import se.mog.io.PermissionDeniedException;

/**
 * @author zubov
 * @version $Id$
 */
public abstract class InodeHandle implements InodeHandleInterface, Comparable<InodeHandle> {
	protected String _path = null;
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
	
	public int compareTo(InodeHandle handle) {
		return(handle._path.compareTo(_path));
	}
	
	/**
	 * Delete the inode.
	 * @throws FileNotFoundException if it doesn't exists.
	 * @throws PermissionDeniedException if the user is not allowed to delete the file.
	 */
	public void delete(User user) throws FileNotFoundException, PermissionDeniedException {
		if (user == null) {
			throw new PermissionDeniedException("User cannot be null");
		}
		
		DirectoryHandle dir = null;
		if (this instanceof DirectoryHandle) {
			dir = (DirectoryHandle) this;
		} else {
			dir = this.getParent();
		}
		
		checkHiddenPath(this, user);
		
		boolean allowed = false;
		if (user.getName().equals(getUsername())) {
			if (!getVFSPermissions().checkPathPermission("deleteown", user, dir)) {
				// the user owns the file althought it doesnt have enough perms to delete it.
				throw new PermissionDeniedException("You are not allowed to delete "+getPath());
			} else {
				// the user owns the file and has enough perms to delete it.
				
				// deleteown > delete
				allowed = true; 
			}
		}

		if (!allowed && !getVFSPermissions().checkPathPermission("delete", user, dir)) {
			throw new PermissionDeniedException("You are not allowed to delete "+getPath());
		}
		
		deleteUnchecked();
	}
	
	public void deleteUnchecked() throws FileNotFoundException {	
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
			throw new IllegalStateException("Can't get the parent of the root directory");
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

	public void renameTo(User user, InodeHandle toInode) 
			throws PermissionDeniedException, FileNotFoundException, FileExistsException {
		if (user == null) {
			throw new PermissionDeniedException("User cannot be null");
		}
		
		DirectoryHandle dir = null;
		if (this instanceof DirectoryHandle) {
			dir = (DirectoryHandle) this;
		} else {
			dir = this.getParent();
		}
		
		checkHiddenPath(this, user); // checking the current inode.
		checkHiddenPath(toInode, user); // also check the destination inode.
		
		boolean allowed = false;
		if (user.getName().equals(getUsername())) {
			if (!getVFSPermissions().checkPathPermission("renameown", user, dir)) {
				// the user owns the file althought it doesnt have enough perms to rename it.
				throw new PermissionDeniedException("You are not allowed to rename "+getPath());
			} else {
				// the user owns the file and has enough perms to rename it.
				
				// renameown > rename
				allowed = true; 
			}
		}

		if (!allowed && !getVFSPermissions().checkPathPermission("rename", user, dir)) {
			throw new PermissionDeniedException("You are not allowed to rename "+getPath());
		}
		
		renameToUnchecked(toInode);
	}
	
	/**
	 * Renames the Inode.
	 * @param toInode
	 * @throws FileExistsException if the destination inode already exists.
	 * @throws FileNotFoundException if the source inode does not exist.
	 */
	public void renameToUnchecked(InodeHandle toInode) throws FileExistsException, FileNotFoundException {
		String fromPath = getPath();
		VirtualFileSystemInode inode = getInode();
		SlaveManager sm = getGlobalContext().getSlaveManager();
		if (inode.isFile()) {
			Set<String> slaves = ((VirtualFileSystemFile) inode).getSlaves();
			for (String slaveName : slaves) {
				try {
					sm.getRemoteSlave(slaveName).simpleRename(fromPath, toInode.getParent().getPath(), toInode.getName());
				} catch (ObjectNotFoundException e) {
					// slave doesn't exist, no reason to tell it to rename this file
				}
			}
		} else if (inode.isDirectory()){
			getGlobalContext().getSlaveManager().renameOnAllSlaves(fromPath, toInode.getParent().getPath(), toInode.getName());
		} else {
			// it's a link! who cares! :)
		}
		inode.rename(toInode.getPath());
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
	
	public boolean isHidden(User user) throws FileNotFoundException {
		// if getInode() fails the file really does not exist.
		getInode();
		
		try {			
			checkHiddenPath(this, user);
			
			//exception not thrown here means that the file is hidden.
			return false;
		} catch (FileNotFoundException e) {
			return true;
		}
	}
	
	protected static VFSPermissions getVFSPermissions() {
		return GlobalContext.getConfig().getVFSPermissions();
	}
	
	protected static void checkHiddenPath(InodeHandle inode, User user) throws FileNotFoundException {	
		if (user == null) {
			throw new FileNotFoundException("Denied, unable to check perms against 'null' user");
		}
		
		DirectoryHandle dir = inode.isDirectory() ? (DirectoryHandle) inode : inode.getParent();
		
		if (getVFSPermissions().checkPathPermission("privpath", user, dir)) {
			// this has to mirror what is said in
			// VirtualFileSystemDirectory.getInodeByName()
			throw new FileNotFoundException("FileNotFound: " + inode.getName()
					+ " does not exist");
		}
	}
	
	public static boolean isFile(String path) throws FileNotFoundException {
		FileHandle file = new FileHandle(path);
		
		try {
			file.getInode();
			return true;
		} catch (ClassCastException e) {
			return false;
		}
	}
	
	public static boolean isDirectory(String path) throws FileNotFoundException {
		DirectoryHandle dir = new DirectoryHandle(path);
		
		try {
			dir.getInode();
			return true;
		} catch (ClassCastException e) {
			return false;
		}
	}
	
	public static boolean isLink(String path) throws FileNotFoundException {		
		LinkHandle link = new LinkHandle(path);
		
		try {
			link.getInode();
			return true;
		} catch (ClassCastException e) {
			return false;
		}
	}

	public <T> void addKey(Key<T> key, T object) throws FileNotFoundException {
		getInode().getKeyedMap().setObject(key,object);
		getInode().commit();
	}

	@SuppressWarnings("unchecked")
	public <T> T removeKey(Key<T> key) throws FileNotFoundException {
		T value = (T) getInode().getKeyedMap().remove(key);
		getInode().commit();
		return value;
	}

	public <T> T getKey(Key<T> key) throws FileNotFoundException, KeyNotFoundException {
		return getInode().getKeyedMap().getObject(key);
	}
}
