package net.sf.drftpd.remotefile;

/**
 * @author mog
 * @version $Id: RemoteFile.java,v 1.23 2004/01/22 21:49:42 mog Exp $
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
