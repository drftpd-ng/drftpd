package se.mog.io;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

import java.util.Enumeration;
import java.util.StringTokenizer;
import java.util.Vector;

/**
 * @author mog
 *
 * To change the template for this generated type comment go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
class UnixFileSystem extends FileSystem {

	public File[] listMounts() {
		try {
			BufferedReader reader =
				new BufferedReader(new FileReader("/etc/mtab"));
			Vector mountPoints = new Vector();
			String line;
			while ((line = reader.readLine()) != null) {
				if (line.charAt(0) == '#')
					continue;
				Enumeration st = new StringTokenizer(line, " \t");
				if(!st.hasMoreElements()) continue;
				/*String fs_spec = */
				st.nextElement();
				if(!st.hasMoreElements()) {
					System.err.println("WARN: /etc/mtab is corrupt, skipping line");
					continue;
				} 
				//String fs_file = st.nextToken();
				mountPoints.add(new File((String)st.nextElement()));
				/*
				String fs_vfstype = st.nextToken();
				String fs_mntops = st.nextToken()
				int fs_freq = Integer.parseInt(st.nextToken());
				int fs_passno = Integer.parseInt(st.nextToken());
				*/
			}
			return (File[]) mountPoints.toArray(new File[mountPoints.size()]);
		} catch (IOException ex) {
			ex.printStackTrace();
			return new File[0];
		}

	}

	public static void main(String args[]) {
		File mounts[] = new UnixFileSystem().listMounts();
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
