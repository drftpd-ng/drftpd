package net.sf.drftpd.remotefile;

import java.io.IOException;

import net.sf.drftpd.SFVFile;


/**
 * @author <a href="mailto:mog@linux.nu">Morgan Christiansson</a>
 */
public abstract class RemoteFile {
	
	protected SFVFile sfvFile;
	
	public SFVFile getSFVFile() throws IOException {
		return sfvFile;
	}
	
	public abstract RemoteFile[] listFiles();
	
	
	
	protected String user;
	public String getUser() {
		if (user == null)
			return "drftpd";
		return user;
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

	protected long lastModified;
	public long lastModified() {
		return lastModified;
	}

	protected long length;
	public long length() {
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

	/////////////////////// abstract ////////////////////////////
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
	 * @see net.sf.drftpd.RemoteFile#getName()
	 */
	public abstract String getName();
	/**
	 * @see net.sf.drftpd.RemoteFile#getParent()
	 */
	public abstract String getParent();
	/**
	 * @see net.sf.drftpd.RemoteFile#getPath()
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
	public long getCheckSum() throws IOException {
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
