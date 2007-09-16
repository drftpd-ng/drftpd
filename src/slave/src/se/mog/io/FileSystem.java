/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * DrFTPD is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with DrFTPD; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package se.mog.io;

import java.io.IOException;

/**
 * @author <a href="mailto:drftpd@mog.se">Morgan Christiansson</a>
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
	 * On UNIX this also lists file systems such as /proc. To make
	 * listMountsbehave like df omit volumes with 0 bytes using
	 * se.mog.io.File#getAvailableDiskSpace()
	 * 
	 * @return
	 */
	public abstract File[] listMounts() throws IOException;

	public abstract DiskFreeSpace getDiskFreeSpace(File file);
}
