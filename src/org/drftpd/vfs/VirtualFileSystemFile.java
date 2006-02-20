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
import java.util.HashSet;
import java.util.Set;

import org.drftpd.master.RemoteSlave;

public class VirtualFileSystemFile extends VirtualFileSystemInode {

	private long _checksum = 0;

	protected Set<RemoteSlave> _slaves;

	private long _xfertime = 0;

	public VirtualFileSystemFile(String username, String group, long size) {
		super(username, group, size);
		_slaves = new HashSet<RemoteSlave>();
		_xfertime = 0;
	}

	public synchronized void addSlave(RemoteSlave rslave) {
		_slaves.add(rslave);
	}

	public long getChecksum() {
		return _checksum;
	}

	public long getXfertime() {
		return _xfertime;
	}

	public synchronized void removeSlave(RemoteSlave rslave) {
		_slaves.remove(rslave);
		if (_slaves.isEmpty()) {
			delete();
		}
	}

	public void setChecksum(long checksum) {
		_checksum = checksum;
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
						"group", "size" }));
	}

	public void setXfertime(long xfertime) {
		_xfertime = xfertime;
	}

}
