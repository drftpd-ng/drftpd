package se.mog.io;


/**
 * @author <a href="mailto:drftpd@mog.se">Morgan Christiansson</a>
 * @version $Id: WindowsFileSystem.java,v 1.3 2003/11/17 20:13:11 mog Exp $
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

	public native DiskFreeSpace getDiskFreeSpace(File file);
}
