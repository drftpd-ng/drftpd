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

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Lowest representation of a directory.
 */
public class VirtualFileSystemLink extends VirtualFileSystemInode {

	private String _link;

	public VirtualFileSystemLink(String user, String group, String link) {
		super(user, group);
		_link = link;
	}

	public String getLinkPath() {
		return _link;
	}

	public void setLinkPath(String link) {
		_link = link;
		commit();
	}
	
	/* (non-Javadoc)
	 * @see org.drftpd.vfs.VirtualFileSystemInode#getSize()
	 */
	@Override
	public long getSize() {
		return 0L;
	}

	/* (non-Javadoc)
	 * @see org.drftpd.vfs.VirtualFileSystemInode#setSize(long)
	 */
	@Override
	public void setSize(long l) {
        // size of links are always zero
	}

	@Override
	public String toString() {
		return "Link" + super.toString() + "[link=" + getLinkPath() + "]";
	}

	protected Map<String,AtomicInteger> getSlaveRefCounts() {
		// Links don't reside on slaves so return an empty Map
		return new TreeMap<>();
	}
}
