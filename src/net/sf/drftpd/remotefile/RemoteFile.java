package net.sf.drftpd.remotefile;

import java.io.FileNotFoundException;
import java.util.Collection;

/**
 * @author mog
 * @version $Id: RemoteFile.java,v 1.21 2003/11/19 00:20:53 mog Exp $
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

	public boolean equals(Object obj) {
		if(!(obj instanceof RemoteFile)) return false;
		RemoteFile remotefile = (RemoteFile) obj;
		
		if(getPath().equals(remotefile.getPath())) return true;
		
		return false;
	}
	/**
	 * Gets the checkSum
	 */
	public long getCheckSumCached() {
		return _checkSum;	
	}
	
//	public abstract Collection getFiles();
	
	public String getGroupname() {
		if (_groupname == null)
			return "drftpd";
		return _groupname;
	}

	/**
	 * @see java.io.File#getName()
	 */
//	public abstract String getName();
	
	public String getUsername() {
		if (_username == null)
			return "drftpd";
		return _username;
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
		throw new UnsupportedOperationException();
	}
	/**
	 * @see java.lang.Object#hashCode()
	 */
	public int hashCode() {
		return getName().hashCode();
	}
	
	/**
	 * Sets the checkSum.
	 * @param checkSum The checkSum to set
	 */
	public void setCheckSum(long checkSum) {
		this._checkSum = checkSum;
	}

	/**
	 * @see java.lang.Object#toString()
	 */
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

	public RemoteFileInterface getLink() {
		throw new UnsupportedOperationException();
	}

	public boolean isLink() {
		return false;
	}

}
