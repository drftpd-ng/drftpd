/*
 * Created on 2003-aug-29
 *
 * To change the template for this generated file go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
package net.sf.drftpd.remotefile;

import java.util.Collection;

/**
 * @author mog
 *
 * To change the template for this generated type comment go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
public interface RemoteFileInterface {
	/**
	 * Gets the checkSum
	 */
	public long getCheckSumCached();
	
	/**
	 * Get the group owner of the file as a String.
	 * getUser().getGroupname() if the implementing class uses a User object.
	 * @return
	 */
	public String getGroupname();
	
	/**
	 * Get the name of the file
	 * @see java.io.File#getName()
	 */
	public String getName();
	public String getUsername();

	public Collection getSlaves();
	public long getXfertime();
	/**
	 * @see java.lang.Object#hashCode()
	 */
	public boolean isFile();
	public boolean isDirectory();
	public long lastModified();
	public long length();
	public RemoteFileInterface[] listFiles();
	public Collection getFiles();
}