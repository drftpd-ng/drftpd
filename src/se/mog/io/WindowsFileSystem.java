package se.mog.io;


/**
 * @author <a href="mailto:drftpd@mog.se">Morgan Christiansson</a>
 *
 * To change the template for this generated type comment go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
class WindowsFileSystem extends FileSystem {

	public File[] listMounts() {
		java.io.File[] roots = java.io.File.listRoots();
		File[] ret = new File[roots.length];
		for(int i=0; i<roots.length; i++) {
			ret[i] = new File(roots[i]);
		}
		return ret;
	}

	public static void main(String args[]) {
		File mounts[] = new WindowsFileSystem().listMounts();
		for (int i = 0; i < mounts.length; i++) {
			System.out.println(mounts[i]);
		}
	}
	/* (non-Javadoc)
	 * @see se.mog.io.FileSystem#getDiskFreeSpace()
	 */

	public native DiskFreeSpace getDiskFreeSpace(File file);
	/* (non-Javadoc)
	 * @see se.mog.io.FileSystem#getDiskFreeSpace()
	public DiskFreeSpace getDiskFreeSpace() {
		DiskFreeSpace diskFreeSpace = new DiskFreeSpace();
		diskFreeSpace(diskFreeSpace);
		return diskFreeSpace;
	}
	 */
}
