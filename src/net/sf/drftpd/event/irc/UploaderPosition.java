/*
 * Created on 2003-aug-04
 *
 * To change the template for this generated file go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
package net.sf.drftpd.event.irc;

/**
 * @author mog
 *
 * To change the template for this generated type comment go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
public class UploaderPosition implements Comparable {
	long bytes;
	int files;
	long xfertime;
	String username;
	
	public UploaderPosition(String username, long bytes, int files, long xfertime) {
		this.username = username;
		this.bytes = bytes;
		this.files = files;
		this.xfertime = xfertime;
	}
	public void updateBytes(long bytes) {
		this.bytes += bytes;
	}
	public void updateFiles(int files) {
		this.files += files;
	}
	public long getBytes() {
		return this.bytes;
	}
	public int getFiles() {
		return this.files;
	}
	
	
	/** Sorts in reverse order so that the biggest shows up first.
	 * @see java.lang.Comparable#compareTo(java.lang.Object)
	 */
	public int compareTo(UploaderPosition o) {
		long thisVal = getBytes();
		long anotherVal = o.getBytes();
		return (thisVal<anotherVal ? 1 : (thisVal==anotherVal ? 0 : -1));
	}
	/* (non-Javadoc)
	 * @see java.lang.Comparable#compareTo(java.lang.Object)
	 */
	public int compareTo(Object o) {
		return compareTo((UploaderPosition)o);
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	public boolean equals(Object obj) {
		//if(obj instanceof String && obj.equals(getUsername())) return true;
		if(!(obj instanceof UploaderPosition)) return false;
		UploaderPosition other = (UploaderPosition)obj;
		return getUsername().equals(other.getUsername());
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	public int hashCode() {
		return getUsername().hashCode();
	}

	/**
	 * @return
	 */
	public String getUsername() {
		return username;
	}
	public void updateXfertime(long xfertime) {
		this.xfertime += xfertime;
	}
	/**
	 * @return
	 */
	public long getXfertime() {
		return xfertime;
	}

}
