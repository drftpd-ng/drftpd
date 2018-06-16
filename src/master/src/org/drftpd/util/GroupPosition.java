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
public class GroupPosition implements Comparable<GroupPosition> {
	long bytes;

	int files;

	String groupname;

	long xfertime;

	public GroupPosition(String groupname, long bytes, int files, long xfertime) {
		this.groupname = groupname;
		this.bytes = bytes;
		this.files = files;
		this.xfertime = xfertime;
	}

	/**
	 * Sorts in reverse order so that the biggest shows up first.
	 * 
	 * @see java.lang.Comparable#compareTo(java.lang.Object)
	 */
	public int compareTo(GroupPosition o) {
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
		if (!(obj instanceof GroupPosition)) {
			return false;
		}

		GroupPosition other = (GroupPosition) obj;

		return getGroupname().equals(other.getGroupname());
	}

	public long getBytes() {
		return this.bytes;
	}

	public int getFiles() {
		return this.files;
	}

	public String getGroupname() {
		return groupname;
	}

	public long getXferspeed() {
		if (getXfertime() == 0) {
			return 0;
		}

		return (long) (getBytes() / (getXfertime() / 1000.0));
	}

	public long getXfertime() {
		return xfertime;
	}

	public int hashCode() {
		return getGroupname().hashCode();
	}

	public void updateBytes(long updatebytes) {
		bytes += updatebytes;
	}

	public void updateFiles(int updatefiles) {
		files += updatefiles;
	}

	public void updateXfertime(long updatexfertime) {
		xfertime += updatexfertime;
	}
}
