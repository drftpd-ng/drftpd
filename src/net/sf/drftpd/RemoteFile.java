package net.sf.drftpd;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.Serializable;
import java.util.Collection;
import java.util.Iterator;
import java.util.Vector;
import java.util.Hashtable;
import java.util.Random;
import java.util.Enumeration;

import java.util.regex.Pattern;
import java.util.regex.Matcher;

import net.sf.drftpd.slave.*;


/**
 * @author mog
 *
 * To change this generated comment edit the template variable "typecomment":
 * Window>Preferences>Java>Templates.
 * To enable and disable the creation of type comments go to
 * Window>Preferences>Java>Code Generation.
 */
public abstract class RemoteFile implements Serializable {


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
		if (getPath().startsWith("."))
			return true;
		return false;
	}

	protected boolean canRead;
	public boolean canRead() {
		return canRead;
	}

	protected boolean canWrite;
	public boolean canWrite() {
		return canWrite;
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
		ret.append("[net.sf.drftpd.RemoteFile[");
		//ret.append(slaves);
		if (isDirectory())
			ret.append("[directory: true]");
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
}
