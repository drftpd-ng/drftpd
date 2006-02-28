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

public class VirtualFileSystemLink extends VirtualFileSystemInode {

	private String _link;
	
	protected static final Collection<String> transientListLink = Arrays
	.asList(new String[] { "lastModified", "name", "parent", "size" });

	public VirtualFileSystemLink(String user, String group, String link) {
		super(user, group, link.length());
		_link = link;
	}

	public String getLink() {
		return _link;
	}

	public void setLink(String link) {	
		_link = link;
	}
	
	@Override
	public String toString() {
		return "Link" + super.toString() + "[link=" + getLink() + "]";
	}

	@Override
	protected void setupXML(XMLEncoder enc) {
		PropertyDescriptor[] pdArr;
		try {
			pdArr = Introspector.getBeanInfo(VirtualFileSystemLink.class)
					.getPropertyDescriptors();
		} catch (IntrospectionException e) {
			logger.error("I don't know what to do here", e);
			throw new RuntimeException(e);
		}
		for (int x = 0; x < pdArr.length; x++) {
			// logger.debug("PropertyDescriptor - VirtualFileSystemLink - "
			// + pdArr[x].getDisplayName());
			if (transientListLink.contains(pdArr[x].getName())) {
				pdArr[x].setValue("transient", Boolean.TRUE);
			}
		}
		enc.setPersistenceDelegate(VirtualFileSystemLink.class,
				new DefaultPersistenceDelegate(new String[] { "username",
						"group", "link" }));
	}

}
