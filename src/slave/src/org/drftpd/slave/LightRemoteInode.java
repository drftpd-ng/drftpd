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

import org.drftpd.vfs.InodeHandleInterface;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.Serializable;

/**
 * @author zubov
 * @version $Id$
 * For use in sending the filelist from the slave to the master
 * Also used for creating files/directories that are superfluous
 */
@SuppressWarnings("serial")
public class LightRemoteInode implements Serializable, InodeHandleInterface {
	private String _filename;

	private long _lastModified;

	private long _length;

	private boolean _isDirectory;
	
	private String _username = "drftpd";
	
	private String _group = "drftpd";

	private void setName(String name) {
		if (name.contains("\\")) {
			throw new RuntimeException(
					"\\ is not an allowed character in filenames");
		}
		_filename = name;
	}

	/**
	 * Will create a File or Directory depending on what the File Object is.
	 * @param file
	 */
	public LightRemoteInode(File file) {
		setName(file.getName());
		_lastModified = file.lastModified();
		_length = file.length();
		_isDirectory = file.isDirectory();
	}

	/**
	 * Will create a file
	 */
	public LightRemoteInode(String filename, long lastModified, long length) {
		setName(filename);
		_lastModified = lastModified;
		_length = length;
	}

	/**
	 * Will create a file
	 * @param fileName
	 * @param username
	 * @param group
	 * @param size
	 * @param lastModified
	 */
	public LightRemoteInode(String fileName, String username, String group, long lastModified, long size) {
		this(fileName, lastModified, size);
		_username = username;
		_group = group;
	}
	
	public LightRemoteInode(String fileName, String username, String group, boolean isDir, long lastModified, long size) {
		this(fileName, username, group, lastModified, size);
		_isDirectory = isDir;
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

	public boolean isAvailable() {
		return true;
	}

	public String getGroup() throws FileNotFoundException {
		return _group;
	}

	public long getSize() throws FileNotFoundException {
		return length();
	}

	public String getUsername() throws FileNotFoundException {
		return _username;
	}

	public boolean isLink() throws FileNotFoundException {
		return false;
	}

	public String getPath() {
		return Slave.separator + getName();
	}

}
