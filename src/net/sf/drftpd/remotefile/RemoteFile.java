package net.sf.drftpd.remotefile;

import java.io.FileNotFoundException;

/**
 * @author <a href="mailto:mog@linux.nu">Morgan Christiansson</a>
 */
public abstract class RemoteFile {
		
	public abstract RemoteFile[] listFiles();
	
	protected String owner;
	public String getOwner() {
		if (owner == null)
			return "drftpd";
		return owner;
	}

	protected String group;
	public String getGroup() {
		if (group == null)
			return "drftpd";
		return group;
	}

	//boolean isHidden;
	public boolean isHidden() {
		return getPath().startsWith(".");
	}

	protected long lastModified = -1;
	public long lastModified() {
		if(lastModified == -1) throw new IllegalStateException("lastModified not set for "+this);
		return lastModified;
	}

	protected long length = -1;
	public long length() {
		if(length == -1) throw new IllegalStateException("length not set for "+this);
		return length;
	}

	protected boolean isDirectory;
	public boolean isDirectory() {
		return isDirectory;
	}

	protected boolean isFile;
	public boolean isFile() {
		return isFile;
	}

	/**
	 * @see java.lang.Object#toString()
	 */
	public String toString() {
		StringBuffer ret = new StringBuffer();
		ret.append("["+getClass().getName()+"[");
		//ret.append(slaves);
		if (isDirectory())
			ret.append("[directory: "+listFiles().length+"]");
		if(isFile())
			ret.append("[file: true]");
		//ret.append("isFile(): " + isFile() + " ");
		ret.append(getPath());
		ret.append("]]");
		return ret.toString();
	}

	/**
	 * separatorChar is always "/" as "/" is always used in FTP.
	 */
	public static final char separatorChar = '/';

	/**
	 * @see java.lang.Object#equals(Object)
	 */
	public boolean equals(Object obj) {
		if(!(obj instanceof RemoteFile)) return false;
		RemoteFile remotefile = (RemoteFile) obj;
		
		if(getPath().equals(remotefile.getPath())) return true;
		
		return false;
	}

	/**
	 * @see java.io.File#getName()
	 */
	public abstract String getName();
	/**
	 * @see java.io.File#getParent()
	 */
	public abstract String getParent() throws FileNotFoundException;
	/**
	 * @see java.io.File#getPath()
	 */
	public abstract String getPath();

	/**
	 * @see java.lang.Object#hashCode()
	 */
	public int hashCode() {
		return getName().hashCode();
	}

	protected long checkSum;
	/**
	 * Gets the checkSum
	 */
	public long getCheckSum() {
		return checkSum;	
	}
	/**
	 * Sets the checkSum.
	 * @param checkSum The checkSum to set
	 */
	public void setCheckSum(long checkSum) {
		this.checkSum = checkSum;
	}

}
