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

import java.beans.DefaultPersistenceDelegate;
import java.beans.XMLEncoder;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.log4j.Logger;
import org.drftpd.dynamicdata.Key;
import org.drftpd.dynamicdata.KeyNotFoundException;
import org.drftpd.dynamicdata.KeyedMap;
import org.drftpd.exceptions.FileExistsException;
import org.drftpd.master.CommitManager;
import org.drftpd.master.Commitable;

import se.mog.io.PermissionDeniedException;

/**
 * VirtualFileSystemInode is an abstract class used to handle basic functions
 * of files/dirs/links and to keep an hierarchy/organization of the FS.
 */
public abstract class VirtualFileSystemInode implements Commitable {

	protected static final Logger logger = Logger
			.getLogger(VirtualFileSystemInode.class.getName());

	/**
	 * @return the VirtualFileSystem instance.
	 */
	public static VirtualFileSystem getVFS() {
		return VirtualFileSystem.getVirtualFileSystem();
	}

	protected transient String _name;

	protected transient VirtualFileSystemDirectory _parent;
	
	protected String _username;
	
	protected String _group;

	protected KeyedMap<Key<?>, Object> _keyedMap = new KeyedMap<Key<?>, Object>();

	protected KeyedMap<Key<?>, Object> _pluginMap = new KeyedMap<Key<?>, Object>();

	protected ConcurrentHashMap<String,Object> _untypedPluginMap = new ConcurrentHashMap<String,Object>();

	protected long _lastModified;
	
	public String descriptiveName() {
		return getPath();
	}

	public void writeToDisk() throws IOException {
		VirtualFileSystem.getVirtualFileSystem().writeInode(this);
	}

	public VirtualFileSystemInode(String user, String group) {
		_username = user;
		_group = group;
		_lastModified = System.currentTimeMillis();
	}

	/**
	 * Need to ensure that this is called after each (non-transient) change to
	 * the Inode.<br>
	 * When called, this method will save the Inode data to the disk.
	 */
	public void commit() {
		//logger.debug("Committing " + getPath());
		CommitManager.getCommitManager().add(this);
	}

	/**
	 * Deletes a file, directory, or link, RemoteSlave handles issues with
	 * slaves being offline and queued deletes
	 */
	public void delete() {
		logger.info("delete(" + this + ")");
		
		VirtualFileSystem.getVirtualFileSystem().deleteInode(getPath());
		_parent.removeChild(this);
		CommitManager.getCommitManager().remove(this);
		
		getVFS().notifyInodeDeleted(getPath());
	}

	/**
	 * @return the owner primary group.
	 */
	public String getGroup() {
		return _group;
	}

	/**
	 * @return the KeyedMap containing the Dynamic Data. 
	 */
	public KeyedMap<Key<?>, Object> getKeyedMap() {
		return _keyedMap;
	}

	/**
	 * @return when the file was last modified.
	 */
	public long getLastModified() {
		return _lastModified;
	}

	/**
	 * @return the file name.
	 */
	public String getName() {
		return _name;
	}

	/**
	 * @return parent dir of the file/directory/link.
	 */
	public VirtualFileSystemDirectory getParent() {
		return _parent;
	}

	/**
	 * @return Returns the full path.
	 */
	protected String getPath() {
		if (this instanceof VirtualFileSystemRoot) {
			return VirtualFileSystem.separator;
		}
		if (getParent() instanceof VirtualFileSystemRoot) {
			return VirtualFileSystem.separator + getName();
		}
		return getParent().getPath() + VirtualFileSystem.separator + getName();
	}

	/**
	 * @return Returns the size of the dir/file/link.
	 */
	public abstract long getSize();
	
	/**
	 * Set the size of the dir/file/link.
	 */
	public abstract void setSize(long l);

	/**
	 * @return the owner username.
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

	/**
	 * Renames this Inode.
	 * @param destination
	 * @throws FileExistsException
	 */
	protected void rename(String destination) throws FileExistsException {
		if (!destination.startsWith(VirtualFileSystem.separator)) {
			throw new IllegalArgumentException(destination + " is a relative path and it should be a full path");
		}
		
		try {
			getVFS().getInodeByPath(destination);
			throw new FileExistsException(destination + "already exists");
		} catch (FileNotFoundException e) {
			// This is good
		}
		
		VirtualFileSystemDirectory destinationDir = null;
		try {
			destinationDir = (VirtualFileSystemDirectory) getVFS().getInodeByPath(VirtualFileSystem.stripLast(destination));
		} catch (FileNotFoundException e) {
			throw new RuntimeException("Error in logic, this should not happen", e);
		}
		String fileString = "rename(" + this + ")";
		_parent.removeChild(this);
		try {			
			VirtualFileSystem.getVirtualFileSystem().renameInode(
					this.getPath(),
					destinationDir.getPath() + VirtualFileSystem.separator
							+ VirtualFileSystem.getLast(destination));
		} catch (FileNotFoundException e) {
			
			// if the file is in the commit queue
			// and a FileNotFoundException was thrown in this situation
			// this file was just created and never commited.
			boolean inCommitQueue = CommitManager.getCommitManager().contains(this);
			if (!inCommitQueue) {
				// file is not on the commit queue
				// and does not exist, this is an error.
				throw new RuntimeException("Tried to rename a file that does not exist: " + getPath(), e);
			}
			
		} catch (PermissionDeniedException e) {
			throw new RuntimeException("FileSystemError", e);
		}
		_name = VirtualFileSystem.getLast(destination);
		_parent = destinationDir;
		_parent.addChild(this);
		fileString = fileString + ",(" + this + ")";
		logger.info(fileString);
	}

	/**
	 * @param group
	 * Sets the group which owns the Inode.
	 */
	public void setGroup(String group) {
		String oldGroup = _group;
		
		_group = group;
		
		getVFS().notifyOwnershipChanged(getPath(), getUsername(), getUsername(), oldGroup, _group);
	}

	public void setKeyedMap(KeyedMap<Key<?>, Object> data) {
		_keyedMap = data;
	}

	/**
	 * @param modified
	 * Set when the file was last modified.
	 */
	public void setLastModified(long modified) {
		_lastModified = modified;
	}

	/**
	 * Sets the Inode name.
	 */
	protected void setName(String name) {
		_name = name;
	}

	public void setParent(VirtualFileSystemDirectory directory) {
		_parent = directory;
	}

	protected void setupXML(XMLEncoder enc) {
		enc.setPersistenceDelegate(Key.class,
				new DefaultPersistenceDelegate(new String[] { "owner", "key" }));
	}

	/**
	 * @param user
	 *            The user to set.
	 */
	public void setUsername(String user) {
		String oldUser = _username;
		
		_username = user; 
		
		getVFS().notifyOwnershipChanged(getPath(), oldUser, _username, getGroup(), getGroup());
	}

	public KeyedMap<Key<?>, Object> getPluginMap() {
		return _pluginMap;
	}

	public void setPluginMap(KeyedMap<Key<?>, Object> data) {
		_pluginMap = data;
	}

	public ConcurrentHashMap<String,Object> getUntypedPluginMap() {
		return _untypedPluginMap;
	}

	public void setUntypedPluginMap(ConcurrentHashMap<String,Object> data) {
		_untypedPluginMap = data;
	}

	protected <T> void addPluginMetaData(Key<T> key, T object) {
		_pluginMap.setObject(key,object);
		commit();
	}

	@SuppressWarnings("unchecked")
	protected <T> T removePluginMetaData(Key<T> key) {
		T value = (T)_pluginMap.remove(key);
		commit();
		return value;
	}

	protected <T> T getPluginMetaData(Key<T> key) throws KeyNotFoundException {
		return _pluginMap.getObject(key);
	}

	protected <T> void addUntypedPluginMetaData(String key, T object) {
		_untypedPluginMap.put(key,object);
		commit();
	}

	@SuppressWarnings("unchecked")
	protected <T> T removeUntypedPluginMetaData(String key) {
		T value = (T)_untypedPluginMap.remove(key);
		commit();
		return value;
	}

	@SuppressWarnings("unchecked")
	protected <T> T getUntypedPluginMetaData(String key) {
		return (T)_untypedPluginMap.get(key);
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
	
	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof VirtualFileSystemInode))
			return false;
		
		return ((VirtualFileSystemInode) obj).getPath().equalsIgnoreCase(getPath());
	}
	
}
