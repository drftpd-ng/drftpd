package net.sf.drftpd;

import java.util.Vector;

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
		canRead = file.canRead();
		canWrite = file.canWrite();
		lastModified = file.lastModified();
		length = file.length();
		//isHidden = file.isHidden();
		isDirectory = file.isDirectory();
		isFile = file.isFile();
		path = file.getPath();
		/* serialize directory*/
		//this.parent = parent;
		slaves = (Vector)file.getSlaves();
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

}
