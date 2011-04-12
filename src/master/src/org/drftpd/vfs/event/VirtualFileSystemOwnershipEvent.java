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

import org.drftpd.vfs.VirtualFileSystemInode;

/**
 * This event is fired whenever the ownership of an inode changes
 * @author fr0w
 * @version $Id$
 */
public class VirtualFileSystemOwnershipEvent extends VirtualFileSystemEvent {

	private String _owner;
	private String _group;
	
	public VirtualFileSystemOwnershipEvent(VirtualFileSystemInode inode, String path, String owner, String group) {
		super(inode, path);
		
		_owner = owner;
		_group = group;
	}

	/**
	 * @return the new owner of the inode
	 */
	public String getOwner() {
		return _owner;
	}

	/**
	 * @return the new group of the inode
	 */
	public String getGroup() {
		return _group;
	}
	
	
	
	

}
