package net.sf.drftpd;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Collection;
import java.util.Hashtable;

/**
 * @author mog
 *
 * To change this generated comment edit the template variable "typecomment":
 * Window>Preferences>Java>Templates.
 * To enable and disable the creation of type comments go to
 * Window>Preferences>Java>Code Generation.
 */
public abstract class RemoteFile {
	/**
	 * @see net.sf.drftpd.RemoteFile#addSlave(RemoteSlave)
	 */
	public abstract void addSlave(RemoteSlave slave);
	/**
	 * @see net.sf.drftpd.RemoteFile#addSlaves(Collection)
	 */
	public abstract void addSlaves(Collection addslaves);
	/**
	 * @see net.sf.drftpd.RemoteFile#canRead()
	 */
	public abstract boolean canRead();
	/**
	 * @see net.sf.drftpd.RemoteFile#canWrite()
	 */
	public abstract boolean canWrite();

	/**
	 * @see java.lang.Object#equals(Object)
	 */
	public abstract boolean equals(Object arg0);
	/**
	 * @see net.sf.drftpd.RemoteFile#exists()
	 */
	public abstract boolean exists();
	/**
	 * @see net.sf.drftpd.RemoteFile#getAnySlave()
	 */
	public abstract RemoteSlave getAnySlave();
	/**
	 * @see net.sf.drftpd.RemoteFile#getGroup()
	 */
	public abstract String getGroup();
	/**
	 * @see net.sf.drftpd.RemoteFile#getHashtable()
	 */
	public abstract Hashtable getHashtable();
	/**
	 * @see net.sf.drftpd.RemoteFile#getName()
	 */
	public abstract String getName();
	/**
	 * @see net.sf.drftpd.RemoteFile#getParent()
	 */
	public abstract String getParent();

	/**
	 * @see net.sf.drftpd.RemoteFile#getParentFile()
	 */
	public abstract LinkedRemoteFile getParentFile();
	/**
	 * @see net.sf.drftpd.RemoteFile#getPath()
	 */
	public abstract String getPath();
	/**
	 * @see net.sf.drftpd.RemoteFile#getSlaves()
	 */
	public abstract Collection getSlaves();
	/**
	 * @see net.sf.drftpd.RemoteFile#getUser()
	 */
	public abstract String getUser();
	/**
	 * @see net.sf.drftpd.RemoteFile#isDirectory()
	 */
	public abstract boolean isDirectory();
	/**
	 * @see net.sf.drftpd.RemoteFile#isFile()
	 */
	public abstract boolean isFile();
	/**
	 * @see net.sf.drftpd.RemoteFile#isHidden()
	 */
	public abstract boolean isHidden();
	/**
	 * @see net.sf.drftpd.RemoteFile#lastModified()
	 */
	public abstract long lastModified();
	/**
	 * @see net.sf.drftpd.RemoteFile#length()
	 */
	public abstract long length();
	/**
	 * @see net.sf.drftpd.RemoteFile#listFiles()
	 */
	public abstract LinkedRemoteFile[] listFiles();
	/**
	 * @see net.sf.drftpd.RemoteFile#lookupFile(String)
	 */
	public abstract LinkedRemoteFile lookupFile(String path) throws FileNotFoundException;
	/**
	 * @see net.sf.drftpd.RemoteFile#merge(RemoteFile)
	 */
	public abstract void merge(LinkedRemoteFile dir);
	/**
	 * @see java.lang.Object#toString()
	 */
	public abstract String toString();

}
