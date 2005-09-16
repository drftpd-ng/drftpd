/**
 * 
 */
package org.drftpd.remotefile;

import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * @author zubov
 * Used for loading/saving files.
 * This is the implementation of SQL backend or local FS or whatever
 */
public interface FileLoader {

	public Inode getInode(String path) throws FileNotFoundException;
	
	public void storeInode(Inode inode) throws IOException;

}
