/*
 * Created on 2003-maj-06
 *
 * To change the template for this generated file go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
package se.mog.io;

import java.io.IOException;
import java.net.URI;

/**
 * @author <a href="mailto:drftpd@mog.se">Morgan Christiansson</a>
 *
 * To change the template for this generated type comment go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
public class File extends java.io.File {
	private static FileSystem fs = FileSystem.getFileSystem();

	/**
	 * @param pathname
	 */
	public File(String pathname) {
		super(pathname);
	}
	public File(java.io.File file) {
		super(file.getPath());
	}
	/**
	 * @param parent
	 * @param child
	 */
	public File(String parent, String child) {
		super(parent, child);
	}

	/**
	 * @param parent
	 * @param child
	 */
	public File(java.io.File parent, String child) {
		super(parent, child);
	}

	/**
	 * @param uri
	 */
	public File(URI uri) {
		super(uri);
	}
	/**
	 * Returns all mounted volumes on the system, this includes file system roots.
	 * 
	 * @see java.io.File#listRoots()
	 */
	public static File[] listMounts() throws IOException {
		return fs.listMounts();
	}
	
	public long getDiskSpaceAvailable() {
		return fs.getDiskFreeSpace(this).freeBytes;
	}

	public long getDiskSpaceCapacity() {
		return fs.getDiskFreeSpace(this).totalBytes;	
	}
	
	public boolean isSymbolicLink() throws IOException {
		return !getCanonicalPath().equals(getAbsolutePath());
	}
	/**
	 * Works exactly like <code>{@link java.io.File#delete()}</code> but has the added funcionality of working recursively.
	 * @see java.io.File#delete()
	 */
	public boolean deleteRecursive() {
		if(isDirectory()) {
			java.io.File files[] = listFiles();
			for (int i = 0; i < files.length; i++) {
				File file = new File(files[i]);
				file.deleteRecursive();
			}
		}
		return super.delete();
	}

}
