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
package org.drftpd.vfs.index;

import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.bushe.swing.event.annotation.EventSubscriber;
import org.drftpd.vfs.event.VirtualFileSystemInodeEvent;
import org.drftpd.vfs.event.VirtualFileSystemOwnershipEvent;
import org.drftpd.vfs.event.VirtualFileSystemRenameEvent;
import org.drftpd.vfs.event.VirtualFileSystemSlaveEvent;

/**
 * 
 * @author fr0w
 * @version $Id: IndexVirtualFileSIndexVirtualFileSystemListener
 */
public class IndexingVirtualFileSystemListener {
	
	public IndexingVirtualFileSystemListener() {
		AnnotationProcessor.process(this);
	}
	
	@EventSubscriber
	public void slavesChanged(VirtualFileSystemSlaveEvent event) {
		
	}
	
	@EventSubscriber
	public void ownershipChanged(VirtualFileSystemOwnershipEvent event) {
		
	}
	
	@EventSubscriber
	public void inodeRenamed(VirtualFileSystemRenameEvent event) {
		
	}
	
	@EventSubscriber
	public void inodeCreated(VirtualFileSystemInodeEvent event) {
		
	}
	
	@EventSubscriber
	public void inodeDeleted(VirtualFileSystemInodeEvent event) {
		
	}
}


