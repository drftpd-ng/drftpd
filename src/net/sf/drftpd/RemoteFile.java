package net.sf.drftpd;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Collection;
import java.util.Vector;
import java.util.Hashtable;
import java.util.Random;
import java.util.Enumeration;

import java.util.regex.Pattern;
import java.util.regex.Matcher;

import net.sf.drftpd.RemoteSlave;

/**
 * @author mog
 *
 * To change this generated comment edit the template variable "typecomment":
 * Window>Preferences>Java>Templates.
 * To enable and disable the creation of type comments go to
 * Window>Preferences>Java>Code Generation.
 */
public abstract class RemoteFile {

	public RemoteFile() {
		
	}
	
	protected Vector slaves;
	public void addSlave(RemoteSlave slave) {
		slaves.add(slave);
	}
	public void addSlaves(Collection addslaves) {
		if (addslaves == null)
			throw new IllegalArgumentException("addslaves cannot be null");
		System.out.println("Adding " + addslaves + " to " + slaves);
		slaves.addAll(addslaves);
		System.out.println("slaves.size() is now " + slaves.size());
	}
	public Collection getSlaves() {
		return slaves;
	}
	private Random rand = new Random();
	public RemoteSlave getAnySlave() {
		int num = rand.nextInt(slaves.size());
		System.out.println(
			"Returning slave "
				+ num
				+ " out of "
				+ slaves.size()
				+ " possible slaves");
		return (RemoteSlave) slaves.get(num);
	}

	public void removeSlave(RemoteSlave slave) {
		slaves.remove(slave);
	}

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
		Enumeration e = slaves.elements();
		ret.append("slaves:[");
		while (e.hasMoreElements()) {
			//[endpoint:[213.114.146.44:2012](remote),objID:[2b6651:ef0b3c7162:-8000, 0]]]]]
			Pattern p = Pattern.compile("endpoint:\\[(.*?):.*?\\]");
			Matcher m = p.matcher(e.nextElement().toString());
			m.find();
			ret.append(m.group(1));
			//ret.append(e.nextElement());
			if (e.hasMoreElements())
				ret.append(",");
		}
		ret.append("]");
		//ret.append("isDirectory(): " + isDirectory() + " ");
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

	/**
	 * A remote file never exists locally, therefore we return false.
	 * If the current RemoteSlave has the file this call could return true
	 * but as we don't know the root directory it's not possible right now.
	 */
	public boolean exists() {
		return false;
	}

	/////////////////////// abstract ////////////////////////////
	/**
	 * @see java.lang.Object#equals(Object)
	 */
	public abstract boolean equals(Object arg0);
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

}
