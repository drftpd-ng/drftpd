/**
 * 
 */
package org.drftpd.remotefile;

import java.io.FileNotFoundException;

/**
 * @author zubov
 * @version $Id$
 *
 */
final class Link extends Inode {
	
	String _destination = null;
	/**
	 * @param path
	 */
	public Link(String path, String destination) {
		super(path);
		validatePath(destination);
		_destination = destination;
		_size = _destination.length();
	}
	
	public Inode getDestination() throws FileNotFoundException {
		return FileManager.getFileManager().getInode(_destination);
	}
}
