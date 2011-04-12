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

package org.drftpd.vfs.event;

import org.drftpd.vfs.InodeHandle;
import org.drftpd.vfs.VFSUtils;
import org.drftpd.vfs.VirtualFileSystemInode;

/**
 * This class represents an event that occured in the Virtual File System.
 * All events that are fired from the VFS must extend this class.
 * 
 * @author fr0w
 * @version $Id$
 */
public abstract class VirtualFileSystemEvent {
	
	private InodeHandle _inode;
	
	private ImmutableInodeHandle _immutableInode;

	/**
	 * This constructor accepts a {@link VirtualFileSystemInode} but in order
	 * to prevent futher access to low level VFS API it uses {@link VFSUtils}
	 * to convert the current <code>realInode</code> to an {@link InodeHandle}
	 * @param realInode
	 */
	public VirtualFileSystemEvent(VirtualFileSystemInode realInode, String path) {
		_inode = VFSUtils.getInodeHandleFor(realInode);
		_immutableInode = new ImmutableInodeHandle(realInode, path);
	}
	
	/**
	 * @return the {@link InodeHandle} that this event is related to.
	 */
	public InodeHandle getInode() {
		return _inode;
	}
	
	/**
	 * @return the {@link ImmutableInodeHandle} that this event is related to.
	 */
	public ImmutableInodeHandle getImmutableInode() {
		return _immutableInode;
	}
}
