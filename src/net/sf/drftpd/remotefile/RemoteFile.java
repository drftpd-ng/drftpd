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

/**
 * @author mog
 * @version $Id: RemoteFile.java,v 1.24 2004/02/10 00:03:15 mog Exp $
 */
public abstract class RemoteFile implements RemoteFileInterface {
	/**
	 * separatorChar is always "/" as "/" is always used in (SYST type UNIX) FTP.
	 */
	public static final char separatorChar = '/';

	protected long _checkSum = 0;

	protected String _groupname;

	protected long _lastModified = -1;
	
	protected String _username;

	public boolean equals(Object file) {
		if (!(file instanceof RemoteFileInterface))
			return false;
		return getPath().equals(((RemoteFile) file).getPath());
	}
	/**
	 * Gets the checkSum
	 */
	public long getCheckSumCached() {
		return _checkSum;	
	}
	
	public String getGroupname() {
		if (_groupname == null)
			return "drftpd";
		return _groupname;
	}

	public RemoteFileInterface getLink() {
		throw new UnsupportedOperationException();
	}
	
	public String getUsername() {
		if (_username == null)
			return "drftpd";
		return _username;
	}
	
	public long getXfertime() {
		throw new UnsupportedOperationException();
	}

	public int hashCode() {
		return getName().hashCode();
	}

	public boolean isLink() {
		return false;
	}
	
	/**
	 * Sets the checkSum.
	 * @param checkSum The checkSum to set
	 */
	public void setCheckSum(long checkSum) {
		_checkSum = checkSum;
	}

	public String toString() {
		StringBuffer ret = new StringBuffer();
		ret.append(getClass().getName()+"[");
		if (isDirectory())
			ret.append("[directory: true]");
		if(isFile())
			ret.append("[file: true]");
		ret.append("[length(): "+this.length()+"]");
		ret.append(getPath());
		ret.append("]");
		return ret.toString();
	}

	public String getLinkPath() {
		return getLink().getPath();
	}
}
