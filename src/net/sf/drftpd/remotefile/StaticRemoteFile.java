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
package net.sf.drftpd.remotefile;

import java.util.Collection;
import java.util.List;

/**
 * Creates a single RemoteFile object that is not linked to any other objects.
 * 
 * @author mog
 * @version $Id: StaticRemoteFile.java,v 1.26 2004/03/01 00:21:09 mog Exp $
 */
public class StaticRemoteFile extends RemoteFile {
	private boolean _isDeleted;
	private long _lastModified;
	private long _length;
	private String _link = null;
	private String _name;
	private List _rslaves;
	private long _xfertime;

	/**
	 * @param rslaves null indicates that this is a directory.
	 */
	public StaticRemoteFile(
		List rslaves,
		String name,
		String owner,
		String group,
		long size,
		long lastModified) {
		_rslaves = rslaves;
		_name = name;
		//		if(name.indexOf("/") != -1) {
		//			throw new IllegalArgumentException("constructor only does files and not paths");
		//		}
		_username = owner;
		_groupname = group;
		_length = size;
		_lastModified = lastModified;
	}

	public StaticRemoteFile(
		List rslaves,
		String name,
		String owner,
		String group,
		long size,
		long lastModified,
		long checkSum) {
		this(rslaves, name, owner, group, size, lastModified);
		_checkSum = checkSum;
	}

	public StaticRemoteFile(String name) {
		_name = name;
	}

	public StaticRemoteFile(String name, List rslaves) {
		this(name);
		_rslaves = rslaves;
	}
	
	public Collection getFiles() {
		throw new UnsupportedOperationException("getFiles() does not exist in StaticRemoteFile");
	}

	public String getLinkPath() {
		return _link;
	}

	public String getName() {
		return _name;
	}

	public String getParent() {
		throw new UnsupportedOperationException("getParent() does not exist in StaticRemoteFile");
	}

	public String getPath() {
		throw new UnsupportedOperationException();
	}

	public Collection getSlaves() {
		return _rslaves;
	}

	public long getXfertime() {
		return _xfertime;
	}

	public boolean isDeleted() {
		return _isDeleted;
	}

	public boolean isDirectory() {
		return _rslaves == null;
	}

	public boolean isFile() {
		return _rslaves != null;
	}

	public boolean isLink() {
		return _link != null;
	}

	public long lastModified() {
		return _lastModified;
	}

	public long length() {
		return _length;
	}

	/**
	 * StaticRemoteFile cannot be linked
	 * @return new RemoteFileInterface[0] 
	 */
	public RemoteFileInterface[] listFiles() {
		return new RemoteFileInterface[0];
	}

	public void setDeleted(boolean isDeleted) {
		_isDeleted = isDeleted;
	}

	public void setGroupname(String groupname) {
		_groupname = groupname;
	}

	public void setLastModified(long lastmodified) {
		_lastModified = lastmodified;
	}

	public void setLength(long length) {
		_length = length;
	}

	public void setLink(String link) {
		_link = link;
	}

	public void setRSlaves(List rslaves) {
		_rslaves = rslaves;
	}

	public void setUsername(String username) {
		_username = username;
	}

	public void setXfertime(long xfertime) {
		_xfertime = xfertime;
	}

	public String toString() {

		StringBuffer ret = new StringBuffer();
		ret.append(getClass().getName() + "[");
		if (isDirectory())
			ret.append("[isDirectory(): true]");
		if (isFile())
			ret.append("[isFile(): true]");
		ret.append("[length(): " + length() + "]");
		ret.append(getName());
		ret.append("]");
		ret.append("[rslaves:" + _rslaves + "]");
		return ret.toString();
	}

}
