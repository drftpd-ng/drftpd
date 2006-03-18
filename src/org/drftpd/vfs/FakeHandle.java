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

/**
 * @author zubov
 * @version $Id$
 * This object is immutable
 */
public abstract class FakeHandle implements InodeHandleInterface {

	private String _name;
	private String _user;
	private String _group;
	private long _size;
	private long _lastModified;

	/**
	 * Used to create a temporary file that doesn't exist and isn't actually in the filesystem
	 * e.g., FileA-OFFLINE (It needs certain properties, but can't fetch them from the filesystem)
	 */
	public FakeHandle(String name, String user, String group, long size, long lastModified) {
		_name = name;
		_user = user;
		_group = group;
		_size = size;
		_lastModified = lastModified;
	}

	public String getGroup() throws FileNotFoundException {
		return _group;
	}

	public String getName() {
		return _name;
	}

	public long getSize() throws FileNotFoundException {
		return _size;
	}

	public String getUsername() throws FileNotFoundException {
		return _user;
	}

	public long lastModified() throws FileNotFoundException {
		return _lastModified;
	}
	
	public String toString() {
		return _name;
	}
	
	public boolean isDirectory() {
		return (this instanceof FakeDirectoryHandle);
	}
	
	public boolean isFile() {
		return (this instanceof FakeFileHandle);
	}
	
	public boolean isLink() {
		return (this instanceof FakeLinkHandle);
	}

}
