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

package org.drftpd.vfs;

/**
 * @author fr0w
 * @version $Id$
 */
public class VFSUtils {

	
	/**
	 * Utility method to convert a Real VFS Inode into a simple {@link InodeHandle}
	 * @param realInode
	 */
	public static InodeHandle getInodeHandleFor(VirtualFileSystemInode realInode) {
		String path = realInode.getPath();
		
		if (realInode.isDirectory()) {
			return new DirectoryHandle(path);
		} else if (realInode.isFile()) {
			return new FileHandle(path);
		} else if (realInode.isLink()) {
			return new LinkHandle(path);
		} else {
			throw new UnsupportedOperationException("This listener is not capable of handling symbolic links");
		}
	}
}
