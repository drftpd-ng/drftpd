package net.sf.drftpd.remotefile;

import java.io.FileNotFoundException;
import java.util.Collection;

/**
 * @author mog
 * @version $Id: RemoteFileInterface.java,v 1.6 2004/01/13 20:30:55 mog Exp $
 */
public interface RemoteFileInterface {
	public long getCheckSumCached();
	/**
	 * Returns a Collection of RemteFileInterface objects.
	 */
	public Collection getFiles();

	/**
	 * Get the group owner of the file as a String.
	 * <p>
	 * getUser().getGroupname() if the implementing class uses a User object.
	 * @return primary group of the owner of this file
	 */
	public String getGroupname();
	
	/**
	 * Returns the target of the link.
	 * @return target of the link.
	 * @see #isLink()
	 */
	public RemoteFileInterface getLink();

	/**
	 * @see java.io.File#getName()
	 */
	public String getName();
	
	public abstract String getParent() throws FileNotFoundException;

	public abstract String getPath();

	public Collection getSlaves();
	/**
	 * Returns string representation of the owner of this file.
	 * <p>
	 * getUser().getUsername() if the implementing class uses a User object.
	 * @return username of the owner of this file.
	 */
	public String getUsername();
	public long getXfertime();
	
	/**
	 * <p>
	 * A flag indicating whether this file is queued for deletion.
	 * <p>
	 * A file will be queued for deletion when a slave was unable to delete the file, most likely because it was offline at the time of deletion. Deleted will be retried when the slave filelist is merged with the master.
	 * @return true if the file is deleted.
	 */
	public boolean isDeleted();
	
	/**
	 * @see java.io.File#isDirectory()
	 */
	public boolean isDirectory();
	
	/**
	 * @see java.io.File#isFile()
	 */
	public boolean isFile();
	
	/**
	 * boolean flag whether this file is a 'link', it can be linked to another file.
	 * This is for the moment used for "ghost files".
	 */
	public boolean isLink();
	
	/**
	 * @see java.io.File#lastModified()
	 */
	public long lastModified();
	
	/**
	 * @see java.io.File#length()
	 */
	public long length();
	
	/**
	 * @see java.io.File#listFiles()
	 */
	public RemoteFileInterface[] listFiles();

}
