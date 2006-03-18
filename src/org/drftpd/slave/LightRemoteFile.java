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
package org.drftpd.slave;

import java.io.Serializable;

/**
 * @author zubov
 * @version $Id$ For use
 *          in sending the filelist from the slave to the master
 */
public final class LightRemoteFile implements Serializable {
	private String _filename;

	private long _lastModified;

	private long _length;

	private boolean _isDirectory;

	private void setName(String name) {
		if (name.indexOf("\\") != -1) {
			throw new RuntimeException(
					"\\ is not an allowed character in filenames");
		}
		_filename = name;
	}

	public LightRemoteFile(LightRemoteFileInterface file) {
		setName(file.getName());
		_lastModified = file.lastModified();
		_length = file.length();
		_isDirectory = file.isDirectory();
	}

	/**
	 * Will create a directory
	 */
	public LightRemoteFile(String filename, long lastModified) {
		setName(filename);
		_lastModified = lastModified;
		_length = 0;
		_isDirectory = true;
	}

	/**
	 * Will create a file
	 */
	public LightRemoteFile(String filename, long lastModified, long length) {
		setName(filename);
		_lastModified = lastModified;
		_length = length;
		_isDirectory = false;
	}

	public boolean isDirectory() {
		return _isDirectory;
	}

	public boolean isFile() {
		return !_isDirectory;
	}

	public long lastModified() {
		return _lastModified;
	}

	public long length() {
		return _length;
	}

	public String getName() {
		return _filename;
	}
}
