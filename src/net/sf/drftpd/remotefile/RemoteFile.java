package net.sf.drftpd.remotefile;

import java.io.FileNotFoundException;
import java.util.Collection;

/**
 * @author <a href="mailto:drftpd@mog.se">Morgan Christiansson</a>
 */
public abstract class RemoteFile implements RemoteFileInterface {
	
	/**
	 * separatorChar is always "/" as "/" is always used in (SYST type UNIX) FTP.
	 */
	public static final char separatorChar = '/';

	protected long checkSum = 0;

	protected String group;

	protected long lastModified = -1;
	
	protected String owner;

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
	 * Gets the checkSum
	 */
	public long getCheckSum() {
		return checkSum;	
	}
	
//	public abstract Collection getFiles();
	
	public String getGroupname() {
		if (group == null)
			return "drftpd";
		return group;
	}

	/**
	 * @see java.io.File#getName()
	 */
//	public abstract String getName();
	
	public String getUsername() {
		if (owner == null)
			return "drftpd";
		return owner;
	}
	/**
	 * @see java.io.File#getParent()
	 */
	public abstract String getParent() throws FileNotFoundException;
	/**
	 * @see java.io.File#getPath()
	 */
	public abstract String getPath();

	public abstract Collection getSlaves();
	
	public long getXfertime() {
		throw new NoSuchMethodError();
	}
	/**
	 * @see java.lang.Object#hashCode()
	 */
	public int hashCode() {
		return getName().hashCode();
	}

//	public abstract boolean isFile();
//	public abstract boolean isDirectory();
	
//	public abstract long lastModified();
//	public abstract long length();
			
//	public abstract RemoteFileInterface[] listFiles();
	
	/**
	 * Sets the checkSum.
	 * @param checkSum The checkSum to set
	 */
	public void setCheckSum(long checkSum) {
		this.checkSum = checkSum;
	}

	/**
	 * @see java.lang.Object#toString()
	 */
	public String toString() {
		StringBuffer ret = new StringBuffer();
		ret.append(getClass().getName()+"[");
		if (isDirectory())
			ret.append("[directory: "+listFiles().length+"]");
		if(isFile())
			ret.append("[file: true]");
		ret.append("[length(): "+this.length()+"]");
		ret.append(getPath());
		ret.append("]");
		return ret.toString();
	}

}
