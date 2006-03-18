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

public class VirtualFileSystemDirectory extends VirtualFileSystemInode {

	protected static final Collection<String> transientListDirectory = Arrays
			.asList(new String[] { "lastModified", "name", "parent", "files" });

	private transient TreeMap<String, SoftReference<VirtualFileSystemInode>> _files = null;

	public VirtualFileSystemDirectory(String user, String group) {
		super(user, group, 0);
		_files = new CaseInsensitiveTreeMap<String, SoftReference<VirtualFileSystemInode>>();
	}

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
		_parent.addSize(l);
	}

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
		inode.commit();
		addChild(inode);
		logger.info("createDirectory(" + inode + ")");
	}

	public synchronized void createFile(String name, String user, String group,
			String initialSlave) throws FileExistsException {
		if (_files.containsKey(name)) {
			throw new FileExistsException(name + " already exists");
		}
		VirtualFileSystemInode inode = new VirtualFileSystemFile(user, group,
				0, initialSlave);
		inode.setName(name);
		inode.setParent(this);
		inode.commit();
		addChild(inode);
		commit();
		logger.info("createFile(" + inode + ")");
	}

	public synchronized void createLink(String name, String target,
			String user, String group) throws FileExistsException {
		if (_files.containsKey(name)) {
			throw new FileExistsException(name + " already exists");
		}
		VirtualFileSystemInode inode = new VirtualFileSystemLink(user, group,
				target);
		inode.setName(name);
		inode.setParent(this);
		inode.commit();
		addChild(inode);
		commit();
		logger.info("createLink(" + inode + ")");
	}
	
	public Set<String> getInodeNames() {
		return new HashSet(_files.keySet());
	}

	public Set<InodeHandle> getInodes() {
		HashSet<InodeHandle> set = new HashSet<InodeHandle>();
		String path = getPath() + VirtualFileSystem.pathSeparator;
		// not dynamically called for efficiency
		for (String inodeName : _files.keySet()) {
			VirtualFileSystemInode inode;
			try {
				inode = getInodeByName(inodeName);
			} catch (FileNotFoundException e) {
				throw new RuntimeException("Stop deleting files outside of drftpd", e);
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

	protected synchronized VirtualFileSystemInode getInodeByName(String name)
			throws FileNotFoundException {
		if (!_files.containsKey(name)) {
			throw new FileNotFoundException(name + " does not exist in "
					+ getPath());
		}
		SoftReference<VirtualFileSystemInode> sf = _files.get(name);
		VirtualFileSystemInode inode = null;
		if (sf == null || sf.get() == null) {
			inode = getVFS().loadInode(
					getPath() + VirtualFileSystem.pathSeparator + name);
			inode.setParent(this);
			_files.remove(name);
			_files.put(name, new SoftReference<VirtualFileSystemInode>(inode));
		} else {
			inode = sf.get();
		}
		return inode;
	}

	protected synchronized void removeChild(VirtualFileSystemInode child) {
		_files.remove(child.getName());
		addSize(-child.getSize());
		setLastModified(System.currentTimeMillis());
		commit();
	}

	public synchronized void setFiles(Collection<String> files) {
		for (String file : files) {
			_files.put(file, null);
		}
	}

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
