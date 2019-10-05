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
package org.drftpd.plugins.mirror;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.drftpd.master.RemoteSlave;
import org.drftpd.usermanager.User;
import org.drftpd.vfs.DirectoryHandle;
import org.drftpd.vfs.FileHandle;
import org.drftpd.vfs.InodeHandle;

import java.io.FileNotFoundException;
import java.util.ArrayList;

/**
 * @author lh
 */
public class MirrorUtils {
	private static final Logger logger = LogManager.getLogger(MirrorUtils.class);

	public static void unMirrorDir(DirectoryHandle dir, User user, ArrayList<String> excludePaths) throws FileNotFoundException {
		for (String excludePath : excludePaths) {
			if (dir.getPath().matches(excludePath)) {
				// Skip this dir
				return;
			}
		}
		for (InodeHandle inode : user == null ? dir.getInodeHandlesUnchecked() : dir.getInodeHandles(user)) {
			if (inode.isFile()) {
				boolean skipFile = false;
				for (String excludePath : excludePaths) {
					if (inode.getPath().matches(excludePath)) {
						// Skip this file
						skipFile = true;
						break;
					}
				}
				if (!skipFile) unMirrorFile((FileHandle) inode);
			} else if (inode.isDirectory()) {
				unMirrorDir((DirectoryHandle) inode, user, excludePaths);
			}
		}
	}

	public static void unMirrorFile(FileHandle file) {
		try {
			boolean first = true;
			for (RemoteSlave slave : file.getSlaves()) {
				if (first) {
					first = false;
				} else {
					slave.simpleDelete(file.getPath());
					file.removeSlave(slave);
				}
			}
		} catch (FileNotFoundException e) {
			// Just ignore, file doesn't exist any more
		}
	}
}
