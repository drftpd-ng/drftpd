package net.sf.drftpd.remotefile;

import java.util.Collection;
import java.util.Collections;
import java.util.ArrayList;
import java.util.List;

/**
 * Creates a single RemoteFile object that is not linked to any other objects.
 * 
 * Useful when doing RMI call and you do not want to send the entire
 * linked directory structure to the remote VM.
 * 
 * @author <a href="mailto:drftpd@mog.se">Morgan Christiansson</a>
 */
public class StaticRemoteFile extends RemoteFile {
	private boolean _isDeleted;
//	private boolean _isDirectory;
//	private boolean _isFile;
	private long _lastModified;
	private long _length;
	private String _name;
	private List _rslaves;
	private long _xfertime;
	
//	//no longer used
//	public StaticRemoteFile(RemoteFile file) {
////		canRead = file.canRead();
////		canWrite = file.canWrite();
//		this.lastModified = file.lastModified();
//		this.length = file.length();
//		//isHidden = file.isHidden();
//		this.isDirectory = file.isDirectory();
//		this.isFile = file.isFile();
//		this.path = file.getPath();
//		this.slaves = new ArrayList(0);
//		/* serialize directory*/
//		//slaves = file.getSlaves();
//	}
	
	public StaticRemoteFile(List rslaves, String name, String owner, String group, long size, long lastModified) {
		//if(rslaves == null) throw new NullPointerException("rslaves cannot be null");
		_rslaves = rslaves;
		_name = name;
//		if(name.indexOf("/") != -1) {
//			throw new IllegalArgumentException("constructor only does files and not paths");
//			isDirectory = true;
//			isFile = false;
//		} else {
//			_isDirectory = false;
//			_isFile = true;
//		}
		this._username = owner;
		this._groupname = group;
		_length = size;
		_lastModified = lastModified;
	}
	
	public StaticRemoteFile(List rslaves, String name, String owner, String group, long size, long lastModified, long checkSum) {
		this(rslaves, name, owner, group, size, lastModified);
		this.checkSum = checkSum;
	}
	public StaticRemoteFile(String name) {
		_name = name;
	}
	
	/**
	 * @see java.lang.Object#equals(Object)
	 */
	public boolean equals(Object file) {
		if(!(file instanceof RemoteFile)) return false;
		return getPath().equals(((RemoteFile)file).getPath());
	}

	/* (non-Javadoc)
	 * @see net.sf.drftpd.remotefile.RemoteFile#getFiles()
	 */
	public Collection getFiles() {
		throw new UnsupportedOperationException("getFiles() does not exist in StaticRemoteFile");
	}

	/**
	 * @see net.sf.drftpd.RemoteFile#getName()
	 */
	public String getName() {
		return _name;
	}

	/**
	 * @see net.sf.drftpd.RemoteFile#getParent()
	 */
	public String getParent() {
		throw new UnsupportedOperationException("getParent() does not exist in StaticRemoteFile");
	}
	
	/**
	 * @see net.sf.drftpd.RemoteFile#getPath()
	 */
	public String getPath() {
		throw new UnsupportedOperationException();
	}

	/* (non-Javadoc)
	 * @see net.sf.drftpd.remotefile.RemoteFile#getSlaves()
	 */
	public Collection getSlaves() {
		return _rslaves;
	}

	public long getXfertime() {
		return _xfertime;
	}

	public boolean isDeleted() {
		return _isDeleted;
	}

	public boolean isDirectory() {
		return _rslaves == null;
	}

	public boolean isFile() {
		return _rslaves != null;
	}

	public long lastModified() {
		return _lastModified;
	}

	public long length() {
		return _length;
	}
	
	/**
	 * StaticRemoteFile cannot be linked, returns new RemoteFileInterface[0] 
	 */
	public RemoteFileInterface[] listFiles() {
		return new RemoteFileInterface[0];
	}

	public void setChecksum(long l) {
		checkSum = l;
	}

	/**
	 * @param b
	 */
	public void setDeleted(boolean b) {
		_isDeleted = b;
	}

	public void setGroupname(String v) {
		_groupname = v;
	}

//	public void setIsDirectory(boolean b) {
//		_isDirectory = b;
//	}

//	public void setIsFile(boolean b) {
//		_isFile = b;
//	}

	public void setLastModified(long l) {
		_lastModified = l;
	}

	public void setLength(long l) {
		_length = l;
	}

	public void setRSlaves(List rslaves) {
		_rslaves = rslaves;
	}

	public void setUsername(String v) {
		_username = v;
	}

	public void setXfertime(long l) {
		_xfertime = l;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	public String toString() {
		
		StringBuffer ret = new StringBuffer();
		ret.append(getClass().getName()+"[");
		if (isDirectory())
			ret.append("[isDirectory(): true]");
		if(isFile())
			ret.append("[isFile(): true]");
		ret.append("[length(): "+length()+"]");
		ret.append(getName());
		ret.append("]");
		ret.append("[rslaves:"+_rslaves+"]");
		return ret.toString();
	}

}
