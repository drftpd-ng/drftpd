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
import java.util.Arrays;
import java.util.Collection;

import net.sf.drftpd.FileExistsException;

import org.apache.log4j.Logger;

public abstract class VirtualFileSystemInode {

	protected static final Logger logger = Logger
			.getLogger(VirtualFileSystemInode.class.getName());

	protected static final Collection<String> transientListDirectory = Arrays
			.asList(new String[] { "lastModified", "name", "parent", "files" });

	protected static final Collection<String> transientListFile = Arrays
			.asList(new String[] { "lastModified", "name", "parent" });

	protected static final Collection<String> transientListLink = Arrays
			.asList(new String[] { "lastModified", "name", "parent", "size" });

	public static VirtualFileSystem getVFS() {
		return VirtualFileSystem.getVirtualFileSystem();
	}

	public String _group;

	public transient long _lastModified;

	public transient String _name;

	public transient VirtualFileSystemDirectory _parent;

	public long _size = 0;

	public String _username;

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
		logger.info("delete(" + getPath() + ")");
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
		return this instanceof VirtualFileSystemInode;
	}

	public boolean isLink() {
		return this instanceof VirtualFileSystemLink;
	}

	protected void rename(String destination) throws FileExistsException {
		if (!destination.startsWith(getVFS().pathSeparator)) {
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
		_parent.removeChild(this);
		VirtualFileSystem.getVirtualFileSystem().renameXML(this.getPath(),
				destinationDir.getPath());
		_name = VirtualFileSystem.getLast(destination);
		destinationDir.addChild(this);
	}

	/**
	 * @param group
	 *            The group to set.
	 */
	public void setGroup(String group) {
		_group = group;
		commit();
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
}
