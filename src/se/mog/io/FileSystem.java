/*
 * Created on 2003-maj-06
 *
 * To change the template for this generated file go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
package se.mog.io;

/**
 * @author mog
 *
 * To change the template for this generated type comment go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
abstract class FileSystem {
	static {
		System.loadLibrary("FileSystem");
	}
	public static native FileSystem getFileSystem();
	/**
	 * Lists mount points and disk drives
	 * 
	 * On UNIX this also lists file systems such as /proc.
	 * To make listMountsbehave like df omit volumes with 0 bytes using se.mog.io.File#getAvailableDiskSpace() 
	 * @return
	 */
	public abstract File[] listMounts();
	public abstract DiskFreeSpace getDiskFreeSpace(File file);
}
