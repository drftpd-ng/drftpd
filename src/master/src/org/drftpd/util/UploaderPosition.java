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
package org.drftpd.util;

/**
 * @author mog
 * @version $Id$
 */
public class UploaderPosition implements Comparable<UploaderPosition> {
	long _bytes;

	int _files;

	String _username;

	long _xfertime;

	public UploaderPosition(String username, long bytes, int files,
			long xfertime) {
		_username = username;
		_bytes = bytes;
		_files = files;
		_xfertime = xfertime;
	}

	/**
	 * Sorts in reverse order so that the biggest shows up first.
	 * 
	 * @see java.lang.Comparable#compareTo(java.lang.Object)
	 */
	public int compareTo(UploaderPosition o) {
		long thisVal = getBytes();
		long anotherVal = o.getBytes();

		return (Long.compare(anotherVal, thisVal));
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	public boolean equals(Object obj) {
		// if(obj instanceof String && obj.equals(getUsername())) return true;
		if (!(obj instanceof UploaderPosition)) {
			return false;
		}

		UploaderPosition other = (UploaderPosition) obj;

		return getUsername().equals(other.getUsername());
	}

	public long getBytes() {
		return _bytes;
	}

	public int getFiles() {
		return _files;
	}

	public String getUsername() {
		return _username;
	}

	public long getXferspeed() {
		if (getXfertime() == 0) {
			return 0;
		}

		return (long) (getBytes() / (getXfertime() / 1000.0));
	}

	public long getXfertime() {
		return _xfertime;
	}

	public int hashCode() {
		return getUsername().hashCode();
	}

	public void updateBytes(long bytes) {
		_bytes += bytes;
	}

	public void updateFiles(int files) {
		_files += files;
	}

	public void updateXfertime(long xfertime) {
		_xfertime += xfertime;
	}
}
