package net.sf.drftpd.remotefile;

import net.sf.drftpd.master.usermanager.User;

/**
 * @author mog
 *
 * To change this generated comment edit the template variable "typecomment":
 * Window>Preferences>Java>Templates.
 * To enable and disable the creation of type comments go to
 * Window>Preferences>Java>Code Generation.
 */
public class StaticRemoteFile extends RemoteFile {
	private String path;

	public StaticRemoteFile(RemoteFile file) {
//		canRead = file.canRead();
//		canWrite = file.canWrite();
		lastModified = file.lastModified();
		length = file.length();
		//isHidden = file.isHidden();
		isDirectory = file.isDirectory();
		isFile = file.isFile();
		path = file.getPath();
		/* serialize directory*/
		//slaves = file.getSlaves();
	}
	
	/**
	 * Creates a new RemoteFile from nothing.
	 * 
	 * If 'path' ends with "/" the RemoteFile will be marked as a directory
	 * 
	 * If this file has no owner 'owner' may be null, then "drftpd:drftpd will be used.
	 * 
	 * If lastModified is 0 it will be set to the currentTimeMillis.
	 */
	public StaticRemoteFile(String path, User owner, long size, long lastModified) {
		this.path = path;
		if(path.endsWith("/")) {
			isDirectory = true;
			isFile = false;
		} else {
			isDirectory = false;
			isFile = true;
		}
		if(owner == null) {
			user = owner.getUsername();
			group = owner.getGroup();
		} else {
			user = "drftpd";
			group = "drftpd";
		}
		this.length = size;
		this.lastModified = lastModified;
	}
	
	/**
	 * @see java.lang.Object#equals(Object)
	 */
	public boolean equals(Object file) {
		if(!(file instanceof RemoteFile)) return false;
		return getPath().equals(((RemoteFile)file).getPath());
	}

	/**
	 * @see net.sf.drftpd.RemoteFile#getName()
	 */
	public String getName() {
		int index = path.lastIndexOf(separatorChar);
		return path.substring(index + 1);
	}

	/**
	 * @see net.sf.drftpd.RemoteFile#getParent()
	 */
	public String getParent() {
		return null;
	}

	/**
	 * @see net.sf.drftpd.RemoteFile#getPath()
	 */
	public String getPath() {
		return path;
	}

	/**
	 * @see net.sf.drftpd.remotefile.RemoteFileTree#listFiles()
	 */
	public RemoteFile[] listFiles() {
		throw new NoSuchMethodError("listFiles() does not exist in StaticRemoteFile");
	}

}
