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

import org.apache.log4j.Logger;
import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.bushe.swing.event.annotation.EventSubscriber;
import org.drftpd.GlobalContext;
import org.drftpd.vfs.InodeHandle;
import org.drftpd.vfs.event.VirtualFileSystemEvent;
import org.drftpd.vfs.event.VirtualFileSystemInodeCreatedEvent;
import org.drftpd.vfs.event.VirtualFileSystemInodeDeletedEvent;
import org.drftpd.vfs.event.VirtualFileSystemOwnershipEvent;
import org.drftpd.vfs.event.VirtualFileSystemRenameEvent;
import org.drftpd.vfs.event.VirtualFileSystemSlaveEvent;

/**
 * This VFS listener is responsible to notify the {@link IndexEngineInterface} that some
 * modification happened to the file system. It's implementation agnostic, so it can be used by
 * any implementation of indexes.
 * 
 * @author fr0w
 * @version $Id: IndexVirtualFileSIndexVirtualFileSystemListener
 */
public class IndexingVirtualFileSystemListener {
	
	private static final Logger logger = Logger.getLogger(IndexingVirtualFileSystemListener.class);
	
	private static final String EXCEPTION_OCCURED_WHILE_INDEXING = "An exception occured while indexing, check stack trace";
	
	/**
	 * Initializes the listener, subscribing to the needed events.
	 */
	public void init() {
		AnnotationProcessor.process(this);
	}
	
	/**
	 * Shortcut to access the {@link IndexEngineInterface}
	 */
	private static IndexEngineInterface getIndexEngine() {
		return GlobalContext.getGlobalContext().getIndexEngine();
	}
	
	/**
	 * Helper method to centralize which event should we not handle in this listener
	 * @param event
	 * @return false if this event should be ignored by this listener
	 */
	private static boolean bypassEvent(VirtualFileSystemEvent event) {
		return event.getInode().isLink();
	}
	
	/**
	 * Method called whenever the slave's set is changed on file.
	 * @param event
	 */
	@EventSubscriber
	public void slavesChanged(VirtualFileSystemSlaveEvent event) {
		if (bypassEvent(event))
			return;
		
		inodeUpdated(event.getInode());
	}
	
	/**
	 * Method called whenever the ownership of the given inode is changed
	 * @param event
	 */
	@EventSubscriber
	public void ownershipChanged(VirtualFileSystemOwnershipEvent event) {
		if (bypassEvent(event))
			return;
		
		inodeUpdated(event.getInode());
	}
	
	/**
	 * Changing the slave set's and the ownership represents the same operation on the index,
	 * so this place is used to avoid code repetition.
	 * @param inode
	 */
	protected void inodeUpdated(InodeHandle inode) {
		try {
			getIndexEngine().updateInode(inode);
		} catch (IndexException e) {
			logger.error(EXCEPTION_OCCURED_WHILE_INDEXING, e);
		}
	}
	
	/**
	 * Method called whenever an inode is renamed
	 * @param event
	 */
	@EventSubscriber
	public void inodeRenamed(VirtualFileSystemRenameEvent event) {
		if (bypassEvent(event))
			return;
		try {
			getIndexEngine().renameInode(event.getSource(), event.getInode());
		} catch (IndexException e) {
			logger.error(EXCEPTION_OCCURED_WHILE_INDEXING, e);
		}
	}
	
	/**
	 * Method called whenever an inode is creatd or deleted.
	 * Depends on {@link VirtualFileSystemInodeCreatedEvent} <code>type</code> property.
	 * @param event
	 */
	@EventSubscriber
	public void inodeCreated(VirtualFileSystemInodeCreatedEvent event) {
		if (bypassEvent(event))
			return;
		
		try {
			getIndexEngine().addInode(event.getInode());
		} catch (IndexException e) {
			logger.error(EXCEPTION_OCCURED_WHILE_INDEXING, e);
		}
	}
	
	@EventSubscriber
	public void inodeDeleted(VirtualFileSystemInodeDeletedEvent event) {
		if (bypassEvent(event))
			return;
		
		try {
			getIndexEngine().deleteInode(event.getInode());
		} catch (IndexException e) {
			logger.error(EXCEPTION_OCCURED_WHILE_INDEXING, e);
		}
	}
}


