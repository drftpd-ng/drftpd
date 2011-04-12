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
 * This event is fired whenever a rename happens in the Virtual File System.
 * @author flavio
 * @version $Id$
 */
public class VirtualFileSystemRenameEvent extends VirtualFileSystemEvent {

	private ImmutableInodeHandle _source;
	
	public VirtualFileSystemRenameEvent(String sourcePath, VirtualFileSystemInode destination, String destinationPath) {
		super(destination, destinationPath);
		
		_source = new ImmutableInodeHandle(destination, sourcePath);
	}
	
	/**
	 * @return where the file is being renamed to.
	 */
	public ImmutableInodeHandle getSource() {
		return _source;
	}

}
