/*
 * Created on 2003-aug-04
 *
 * To change the template for this generated file go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
package net.sf.drftpd.master;

/**
 * @author mog
 * @version $Id: GroupPosition.java,v 1.1 2004/01/20 16:56:48 flowman Exp $
 */
public class GroupPosition implements Comparable {
	long bytes;
	int files;
	String groupname;
	long xfertime;

	public GroupPosition(
		String groupname,
		long bytes,
		int files,
		long xfertime) {
		this.groupname = groupname;
		this.bytes = bytes;
		this.files = files;
		this.xfertime = xfertime;
	}

	public int compareTo(Object o) {
		return compareTo((GroupPosition) o);
	}

	/** Sorts in reverse order so that the biggest shows up first.
	 * @see java.lang.Comparable#compareTo(java.lang.Object)
	 */
	public int compareTo(GroupPosition o) {
		long thisVal = getBytes();
		long anotherVal = o.getBytes();
		return (thisVal < anotherVal ? 1 : (thisVal == anotherVal ? 0 : -1));
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	public boolean equals(Object obj) {
		//if(obj instanceof String && obj.equals(getUsername())) return true;
		if (!(obj instanceof GroupPosition))
			return false;
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
		if (getXfertime() == 0)
			return 0;
		return (long) ((long) getBytes() / ((long) getXfertime() / 1000.0));
	}

	public long getXfertime() {
		return xfertime;
	}

	public int hashCode() {
		return getGroupname().hashCode();
	}
	public void updateBytes(long bytes) {
		this.bytes += bytes;
	}
	public void updateFiles(int files) {
		this.files += files;
	}
	public void updateXfertime(long xfertime) {
		this.xfertime += xfertime;
	}
}
