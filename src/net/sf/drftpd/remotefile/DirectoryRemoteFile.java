/*
 * Created on 2003-jul-31
 *
 * To change the template for this generated file go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
package net.sf.drftpd.remotefile;

import java.util.ArrayList;
import java.util.Collection;


/**
 * @author <a href="mailto:drftpd@mog.se">Morgan Christiansson</a>
 *
 * To change this generated comment edit the template variable "typecomment":
 * Window>Preferences>Java>Templates.
 * To enable and disable the creation of type comments go to
 * Window>Preferences>Java>Code Generation.
 */
public class DirectoryRemoteFile extends RemoteFile {
	private final LinkedRemoteFile file;

	private String name;
	private long length;
	private long lastModified;

	public DirectoryRemoteFile(LinkedRemoteFile file, String owner, String group, String name) {
		this.name = name;
		this.file = file;
		lastModified = System.currentTimeMillis();
		//canWrite = true;
		//canRead = true;
		this._username = owner;
		this._groupname = group;
	}
	
	/**
	 * @see net.sf.drftpd.remotefile.RemoteFile#getName()
	 */
	public String getName() {
		return name;
	}

	/**
	 * @see net.sf.drftpd.remotefile.RemoteFile#getParent()
	 */
	public String getParent() {
		throw new UnsupportedOperationException("getParent() is not implemented on LinkedRemoteFile.DirectoryRemoteFile");
	}

	/**
	 * @see net.sf.drftpd.remotefile.RemoteFile#getPath()
	 */
	public String getPath() {
		throw new UnsupportedOperationException("getPath() is not implemented on LinkedRemoteFile.DirectoryRemoteFile");
	}
	/**
	 * @see net.sf.drftpd.remotefile.RemoteFileTree#listFiles()
	 */
	public RemoteFileInterface[] listFiles() {
		return new RemoteFileInterface[0];
	}
	public String toString() {
		return "[" + getClass().getName() + "[name: " + getName() + "]]";
	}

	/**
	 * @see net.sf.drftpd.remotefile.RemoteFile#getFiles()
	 */
	public Collection getFiles() {
		return new ArrayList(0);
	}

	/**
	 * @see net.sf.drftpd.remotefile.RemoteFile#getSlaves()
	 */
	public Collection getSlaves() {
		return new ArrayList(0);
	}

	public boolean isDirectory() {
		return true;
	}
	public boolean isFile() {
		return false;
	}

	/**
	 * @see net.sf.drftpd.remotefile.RemoteFile#length()
	 */
	public long length() {
		return 0;
	}

	/* (non-Javadoc)
	 * @see net.sf.drftpd.remotefile.RemoteFile#lastModified()
	 */
	public long lastModified() {
		return this.lastModified;
	}

	/* (non-Javadoc)
	 * @see net.sf.drftpd.remotefile.RemoteFile#hasFile(java.lang.String)
	 * we never have any files, so always returns false
	 */
	public boolean hasFile(String filename) {
		return false;
	}

	/* (non-Javadoc)
	 * @see net.sf.drftpd.remotefile.RemoteFileInterface#isDeleted()
	 */
	public boolean isDeleted() {
		return false;
	}
}