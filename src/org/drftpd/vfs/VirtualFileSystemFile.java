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
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import net.sf.drftpd.ObjectNotFoundException;

import org.drftpd.GlobalContext;
import org.drftpd.dynamicdata.Key;

public class VirtualFileSystemFile extends VirtualFileSystemInode {

	protected static final Collection<String> transientListFile = Arrays
			.asList(new String[] { "lastModified", "name", "parent",
					"xfertime", "checksum" });

	public static final Key CRC = new Key(VirtualFileSystemFile.class,
			"checksum", Long.class);

	public static final Key MD5 = new Key(VirtualFileSystemFile.class, "md5",
			Long.class);

	public static final Key XFERTIME = new Key(VirtualFileSystemFile.class,
			"xfertime", Long.class);

	protected Set<String> _slaves;

	public synchronized Set<String> getSlaves() {
		return new HashSet<String>(_slaves);
	}

	@Override
	public String toString() {
		StringBuffer ret = new StringBuffer();
		ret.append("File" + super.toString() + "[slaves=");
		for (String slave : getSlaves()) {
			ret.append(slave + ",");
		}
		ret.replace(ret.length() - 1, ret.length(), "]");
		return ret.toString();
	}

	public synchronized void setSlaves(Set<String> slaves) {
		_slaves = slaves;
	}

	public VirtualFileSystemFile(String username, String group, long size,
			String initialSlave) {
		this(username, group, size, new HashSet<String>(Arrays
				.asList(new String[] { initialSlave })));
	}

	public VirtualFileSystemFile(String username, String group, long size,
			Set<String> slaves) {
		super(username, group, size);
		_slaves = slaves;
	}

	public synchronized void addSlave(String rslave) {
		_slaves.add(rslave);
		commit();
	}

	public long getChecksum() {
		return getKeyedMap().getObjectLong(CRC);
	}

	public long getXfertime() {
		return getKeyedMap().getObjectLong(XFERTIME);
	}

	public synchronized void removeSlave(String rslave) {
		_slaves.remove(rslave);
		if (_slaves.isEmpty()) {
			delete();
		} else {
			commit();
		}
	}

	public void setChecksum(long checksum) {
		getKeyedMap().setObject(CRC, checksum);
		commit();
	}

	@Override
	protected void setupXML(XMLEncoder enc) {
		PropertyDescriptor[] pdArr;
		try {
			pdArr = Introspector.getBeanInfo(VirtualFileSystemFile.class)
					.getPropertyDescriptors();
		} catch (IntrospectionException e) {
			logger.error("I don't know what to do here", e);
			throw new RuntimeException(e);
		}
		for (int x = 0; x < pdArr.length; x++) {
			// logger.debug("PropertyDescriptor - VirtualFileSystemFile - "
			// + pdArr[x].getDisplayName());
			if (transientListFile.contains(pdArr[x].getName())) {
				pdArr[x].setValue("transient", Boolean.TRUE);
			}
		}
		enc.setPersistenceDelegate(VirtualFileSystemFile.class,
				new DefaultPersistenceDelegate(new String[] { "username",
						"group", "size", "slaves" }));
	}

	public void setXfertime(long xfertime) {
		getKeyedMap().setObject(XFERTIME, xfertime);
		commit();
	}

	public void setSize(long size) {
		if (size < 0) {
			throw new IllegalArgumentException("File size cannot be < 0");
		}
		getParent().addSize(-_size);
		_size = size;
		getParent().addSize(_size);
		commit();
	}

	public boolean isUploading() {
		// TODO Auto-generated method stub
		return false;
	}

	public synchronized boolean isAvailable() {
		for (String slave : _slaves) {
			try {
				if (GlobalContext.getGlobalContext().getSlaveManager()
						.getRemoteSlave(slave).isAvailable()) {
					return true;
				}
			} catch (ObjectNotFoundException e) {
			}
		}
		return false;
	}

}
