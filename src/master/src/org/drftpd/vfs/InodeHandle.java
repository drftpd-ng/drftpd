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

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.drftpd.GlobalContext;
import org.drftpd.dynamicdata.Key;
import org.drftpd.dynamicdata.KeyNotFoundException;
import org.drftpd.exceptions.FileExistsException;
import org.drftpd.exceptions.ObjectNotFoundException;
import org.drftpd.io.PermissionDeniedException;
import org.drftpd.master.RemoteSlave;
import org.drftpd.master.SlaveManager;
import org.drftpd.usermanager.User;
import org.drftpd.vfs.perms.VFSPermissions;

import java.io.FileNotFoundException;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * @author zubov
 * @version $Id$
 */
public abstract class InodeHandle implements InodeHandleInterface, Comparable<InodeHandle> {
	protected String _path = null;
	protected static final Logger logger = LogManager.getLogger(InodeHandle.class.getName());
	
	/**
	 * Creates an InodleHandle for the given path.
	 * @param path
	 */
	public InodeHandle(String path) {
		if (path == null || !path.startsWith(VirtualFileSystem.separator)) {
			throw new IllegalArgumentException("InodeHandle needs an absolute path, argument was [" + path + "]");
		}
		_path = VirtualFileSystem.fixPath(path);
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
		
		checkHiddenPath(this, user);
		
		boolean allowed = false;
		if (user.getName().equals(getUsername())) {
			if (!getVFSPermissions().checkPathPermission("deleteown", user, this)) {
				// the user owns the file althought it doesnt have enough perms to delete it.
				throw new PermissionDeniedException("You are not allowed to delete "+getPath());
			}
			// the user owns the file and has enough perms to delete it.

			// deleteown > delete
			allowed = true; 
		}

		if (!allowed && !getVFSPermissions().checkPathPermission("delete", user, this)) {
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
	
	public long creationTime() throws FileNotFoundException {
		return getInode().getCreationTime();
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
		
		checkHiddenPath(this, user); // checking the current inode.
		checkHiddenPath(toInode, user); // also check the destination inode.
		
		boolean allowed = false;
		if (user.getName().equals(getUsername())) {
			if (!getVFSPermissions().checkPathPermission("renameown", user, this)) {
				// the user owns the file althought it doesnt have enough perms to rename it.
				throw new PermissionDeniedException("You are not allowed to rename "+getPath());
			}
			// the user owns the file and has enough perms to rename it.

			// renameown > rename
			allowed = true; 
		}

		if (!allowed && !getVFSPermissions().checkPathPermission("rename", user, this)) {
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
		SlaveManager sm = getGlobalContext().getSlaveManager();
		if (toInode.exists() && toInode.isFile()) {
            /**
             * not relevant here, check is done in code below, maybe should refactor this slightly!
             * If target exists and its a file, maybe its time to check against some SFV or
             * compare filesize and if they match delete one
             *
             * But mostly toInode is a directory.
             */

			//throw new FileExistsException(toInode.getPath() + " already exists");
        } else if (!getInode().getPath().equalsIgnoreCase(toInode.getPath())
                && toInode.exists() && toInode.isDirectory() && getInode().isDirectory()) {
			VirtualFileSystemDirectory dir = (VirtualFileSystemDirectory)getInode();
			Set<InodeHandle> dirInodes = dir.getInodes();
			Iterator<InodeHandle> it = dirInodes.iterator();

            boolean merge;
			while(it.hasNext()) {
				InodeHandle ih = it.next();
				if(ih.isDirectory()) {
					DirectoryHandle subDir = new DirectoryHandle(toInode.getPath()+"/"+ih.getName());
					ih.renameToUnchecked(subDir);
					if(ih.exists()) {
						ih.deleteUnchecked(); // This deletes subdir after we have moved subdircontent
					}
				} else {
                    merge=false;
					VirtualFileSystemInode inode = ih.getInode();
					FileHandle targetInode = new FileHandle(toInode.getPath()+"/"+inode.getName());

					try {
						if(targetInode.getSize()<inode.getSize()) {
							//TODO implement checksum against sfv
							targetInode.deleteUnchecked();
                            merge=true;
						}
					} catch (FileNotFoundException e) {
                        merge=true;
					}

					if(merge) {
						Set<String> slaves = ((VirtualFileSystemFile) inode).getSlaves();
						for (String slaveName : slaves) {
							try {
								sm.getRemoteSlave(slaveName).simpleRename(inode.getPath(), toInode.getPath(), inode.getName());
								inode.rename(toInode.getPath()+"/"+inode.getName());
							} catch (ObjectNotFoundException e) {
							}
						}
					} else {
						ih.deleteUnchecked();
					}
				}
			}

			if(exists()) {
				deleteUnchecked();
			}

			return;
		}

		String fromPath = getPath();
		VirtualFileSystemInode inode = getInode();
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
		DirectoryHandle dir = inode.isDirectory() ? (DirectoryHandle) inode : inode.getParent();

		if (getVFSPermissions().checkPathPermission("privpath", user, dir, false, true)) {
			// this has to mirror what is said in
			// VirtualFileSystemDirectory.getInodeByName()
			throw new FileNotFoundException("FileNotFound: " + inode.getName()
					+ " does not exist");
		}

		if (!inode.isDirectory()) {
			if (getVFSPermissions().checkPathPermission("privpath", user, inode, false, true)) {
				throw new FileNotFoundException("FileNotFound: " + inode.getName()
						+ " does not exist");
			}
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

	/**
	 * Add custom meta data provided by a plugin to the map serialized as part of the inode
	 * for future retrieval. It is safe to use custom classes and keys defined in the plugin
	 * for storing data here but be the data will be lost if the inode is deserialized when
	 * the plugin providing the classes is not loaded. This should only be used for data which
	 * can be repopulated as required.
	 * 
	 * @param  key
	 *         An instance of <tt>Key</tt> to store the data against
	 *
	 * @param  object
	 *         The data to be stored in the map
	 *
	 * @throws  FileNotFoundException
	 *          If the inode for this handle does not exist
	 */
	public <T> void addPluginMetaData(Key<T> key, T object) throws FileNotFoundException {
		getInode().addPluginMetaData(key, object);
	}

	/**
	 * Remove custom meta data provided by a plugin from the map serialized as part of the inode.
	 * 
	 * @param  key
	 *         An instance of <tt>Key</tt> which was used to store the data against
	 * 
	 * @return  The data which was stored against the key or <tt>null</tt> if the map contained
	 *          no entry for the key
	 *
	 * @throws  FileNotFoundException
	 *          If the inode for this handle does not exist
	 */
	public <T> T removePluginMetaData(Key<T> key) throws FileNotFoundException {
		return getInode().removePluginMetaData(key);
	}

	/**
	 * Retrieve custom meta data provided by a plugin from the map serialized as part of the inode.
	 * 
	 * @param  key
	 *         An instance of <tt>Key</tt> which was used to store the data against
	 * 
	 * @return  The data which was stored against the key
	 * 
	 * @throws  FileNotFoundException
	 *          If the inode for this handle does not exist
	 * 
	 * @throws  KeyNotFoundException
	 *          If no entry exists in the map for the provided key
	 */
	public <T> T getPluginMetaData(Key<T> key) throws FileNotFoundException, KeyNotFoundException {
		return getInode().getPluginMetaData(key);
	}

	/**
	 * Add meta data provided by a plugin to the map serialized as part of the inode
	 * for future retrieval. It is not safe to use custom classes defined in the plugin
	 * for storing data here, only classes provided by the Java API or the master plugin and
	 * its parents should be used, using other classes will lead to potential memory leaks.
	 * Data stored against this map will not be lost if the plugin which stored it is not
	 * loaded at the time the inode is deserialized providing the stated conditions are met.
	 * 
	 * @param  key
	 *         A unique key to store the meta data against
	 * 
	 * @param  object
	 *         The data to be stored
	 * 
	 * @throws  FileNotFoundException
	 *          If the inode for this handle does not exist
	 */
	public <T> void addUntypedPluginMetaData(String key, T object) throws FileNotFoundException {
		getInode().addUntypedPluginMetaData(key, object);
	}

	/**
	 * Remove meta data provided by a plugin from the map serialized as part of the inode.
	 * 
	 * @param  key
	 *         The key which was used when storing the data in the map
	 * 
	 * @param  clazz
	 *         A class representing the type of the object to return
	 * 
	 * @return  The data which was stored against the key or <tt>null</tt> if the map contained
	 *          no entry for the key
	 *
	 * @throws  FileNotFoundException
	 *          If the inode for this handle does not exist
	 */
	public <T> T removeUntypedPluginMetaData(String key, Class<T> clazz) throws FileNotFoundException {
		return getInode().removeUntypedPluginMetaData(key);
	}

	/**
	 * Retrieve custom meta data provided by a plugin from the map serialized as part of the inode.
	 * 
	 * @param  key
	 *         The key which was used when storing the data in the map
	 *         
	 * @param  clazz
	 *         A class representing the type of the object to return
	 * 
	 * @return  The data which was stored against the key or <tt>null</tt> if the map contained
	 *          no entry for the provided key
	 * 
	 * @throws  FileNotFoundException
	 *          If the inode for this handle does not exist
	 */
	public <T> T getUntypedPluginMetaData(String key, Class<T> clazz) throws FileNotFoundException {
		return getInode().getUntypedPluginMetaData(key);
	}

	public Map<String,AtomicInteger> getSlaveRefCounts() throws FileNotFoundException {
		return getInode().getSlaveRefCounts();
	}
	
	/**
	 * Request that a refresh notification is issued for this inode to inform VFS listeners
	 * that they may want to update any information held regarding this inode.
	 * 
	 * @param  sync
	 *         Whether the refresh should processed synchronously or not
	 *         
	 * @throws FileNotFoundException
	 *         If the inode for this handle does not exist
	 */
	public void requestRefresh(boolean sync) throws FileNotFoundException {
		getInode().refresh(sync);
	}
}
