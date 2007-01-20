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
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.beans.XMLEncoder;
import java.io.FileNotFoundException;
import java.lang.ref.SoftReference;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeMap;

import net.sf.drftpd.FileExistsException;

/**
 * Lowest representation of a directory.<br>
 * This class deals directly w/ the actual VFS and should not be used
 * outside the of the VirtualFileSystem part.
 * @see org.drftpd.vfs.DirectoryHandle
 */
public class VirtualFileSystemDirectory extends VirtualFileSystemInode {

	protected static final Collection<String> transientListDirectory = Arrays
			.asList(new String[] { "name", "parent", "files" });

	private transient TreeMap<String, SoftReference<VirtualFileSystemInode>> _files = null;

	public VirtualFileSystemDirectory(String user, String group) {
		super(user, group, 0);
		_files = new CaseInsensitiveTreeMap<String, SoftReference<VirtualFileSystemInode>>();
	}

	/**
	 * Add another inode to the directory tree.
	 * @param inode
	 */
	protected synchronized void addChild(VirtualFileSystemInode inode) {
		_files.put(inode.getName(), new SoftReference<VirtualFileSystemInode>(
				inode));
		if (getLastModified() < inode.getLastModified()) {
			setLastModified(inode.getLastModified());
		}
		addSize(inode.getSize());
		commit();
	}

	protected void addSize(long l) {
		_size = getSize() + l;
		getParent().addSize(l);
		commit();
	}

	/**
	 * Create a directory inside the current Directory.
	 * @param name
	 * @param user
	 * @param group
	 * @throws FileExistsException if this directory already exists.
	 */
	public synchronized void createDirectory(String name, String user,
			String group) throws FileExistsException {
		if (_files.containsKey(name)) {
			throw new FileExistsException(name + " already exists in "
					+ getPath());
		}
		VirtualFileSystemInode inode = new VirtualFileSystemDirectory(user,
				group);
		inode.setName(name);
		inode.setParent(this);
		inode.setLastModified(System.currentTimeMillis());
		inode.commit();
		addChild(inode);
		logger.info("createDirectory(" + inode + ")");
	}

	/**
	 * Create a file inside the current directory.
	 * @param name
	 * @param user
	 * @param group
	 * @param initialSlave
	 * @throws FileExistsException if this file already exists.
	 */
	public synchronized void createFile(String name, String user, String group,
			String initialSlave) throws FileExistsException {
		if (_files.containsKey(name)) {
			throw new FileExistsException(name + " already exists");
		}
		VirtualFileSystemInode inode = new VirtualFileSystemFile(user, group,
				0, initialSlave);
		inode.setName(name);
		inode.setParent(this);
		inode.setLastModified(System.currentTimeMillis());
		inode.commit();
		addChild(inode);
		commit();
		logger.info("createFile(" + inode + ")");
	}

	/**
	 * Create a link inside the current directory.
	 * @param name
	 * @param target
	 * @param user
	 * @param group
	 * @throws FileExistsException if this link already exists.
	 */
	public synchronized void createLink(String name, String target,
			String user, String group) throws FileExistsException {
		if (_files.containsKey(name)) {
			throw new FileExistsException(name + " already exists");
		}
		VirtualFileSystemInode inode = new VirtualFileSystemLink(user, group,
				target);
		inode.setName(name);
		inode.setParent(this);
		inode.setLastModified(System.currentTimeMillis());
		inode.commit();
		addChild(inode);
		commit();
		logger.info("createLink(" + inode + ")");
	}

	/**
	 * @return a Set containing all inode names inside this directory.
	 */
	public Set<String> getInodeNames() {
		return new HashSet<String>(_files.keySet());
	}

	/**
	 * @return a set containing all Inode objects inside this directory.
	 */
	public Set<InodeHandle> getInodes() {
		HashSet<InodeHandle> set = new HashSet<InodeHandle>();
		String path = getPath() + VirtualFileSystem.separator;
		// not dynamically called for efficiency
		for (String inodeName : new HashSet<String>(_files.keySet())) {
			VirtualFileSystemInode inode;
			try {
				inode = getInodeByName(inodeName);
			} catch (FileNotFoundException e) {
				throw new RuntimeException(
						"Stop deleting files outside of drftpd", e);
			}
			if (inode.isDirectory()) {
				set.add(new DirectoryHandle(path + inodeName));
			} else if (inode.isFile()) {
				set.add(new FileHandle(path + inodeName));
			} else if (inode.isLink()) {
				set.add(new LinkHandle(path + inodeName));
			}
		}
		return set;

	}

	/**
	 * @param name
	 * @return VirtualFileSystemInode object if 'name' exists on the dir.
	 * @throws FileNotFoundException
	 */
	protected synchronized VirtualFileSystemInode getInodeByName(String name)
			throws FileNotFoundException {
		//logger.debug("getInodeByName(" + name + ")");
		name = VirtualFileSystem.fixPath(name);
		if (name.startsWith(VirtualFileSystem.separator)) {
			return VirtualFileSystem.getVirtualFileSystem().getInodeByPath(name);
		}
		if (!_files.containsKey(name)) {
			throw new FileNotFoundException(name + " does not exist in "
					+ getPath());
		}
		SoftReference<VirtualFileSystemInode> sf = _files.get(name);
		VirtualFileSystemInode inode = null;
		if (sf != null) {
			inode = sf.get();
		}
		if (inode == null) {
			inode = getVFS().loadInode(
					getPath() + VirtualFileSystem.separator + name);
			inode.setParent(this);
			// _files.remove(name);
			// Map instance replaces what is previously there with put()
			_files.put(name, new SoftReference<VirtualFileSystemInode>(inode));
		}
		return inode;
	}

	/**
	 * Remove the inode from the directory tree.
	 * 
	 * @param child
	 */
	protected synchronized void removeChild(VirtualFileSystemInode child) {
		_files.remove(child.getName());
		addSize(-child.getSize());
		setLastModified(System.currentTimeMillis());
		commit();
	}

	/**
	 * Changes the directory tree.
	 * @param files
	 */
	public synchronized void setFiles(Collection<String> files) {
		for (String file : files) {
			_files.put(file, null);
		}
	}

	/**
	 * Configure the serialization of the Directory.
	 */
	@Override
	protected void setupXML(XMLEncoder enc) {
		PropertyDescriptor[] pdArr;
		try {
			pdArr = Introspector.getBeanInfo(VirtualFileSystemDirectory.class)
					.getPropertyDescriptors();
		} catch (IntrospectionException e) {
			logger.error("I don't know what to do here", e);
			throw new RuntimeException(e);
		}
		for (int x = 0; x < pdArr.length; x++) {
			// logger.debug("PropertyDescriptor - VirtualFileSystemDirectory - "
			// + pdArr[x].getDisplayName());
			if (transientListDirectory.contains(pdArr[x].getName())) {
				pdArr[x].setValue("transient", Boolean.TRUE);
			}
		}
		enc.setPersistenceDelegate(VirtualFileSystemDirectory.class,
				new DefaultPersistenceDelegate(new String[] { "username",
						"group" }));
	}

	@Override
	public String toString() {
		return "Directory" + super.toString();
	}

}
