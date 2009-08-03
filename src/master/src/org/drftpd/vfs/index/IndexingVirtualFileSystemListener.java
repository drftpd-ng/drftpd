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

import java.io.FileNotFoundException;

import org.apache.log4j.Logger;
import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.bushe.swing.event.annotation.EventSubscriber;
import org.drftpd.GlobalContext;
import org.drftpd.vfs.DirectoryHandle;
import org.drftpd.vfs.FileHandle;
import org.drftpd.vfs.InodeHandle;
import org.drftpd.vfs.event.VirtualFileSystemInodeEvent;
import org.drftpd.vfs.event.VirtualFileSystemOwnershipEvent;
import org.drftpd.vfs.event.VirtualFileSystemRenameEvent;
import org.drftpd.vfs.event.VirtualFileSystemSlaveEvent;
import org.drftpd.vfs.event.VirtualFileSystemInodeEvent.VirtualFileSystemInodeEventType;

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
	
	private static final String FILE_WAS_ADDED_BUT_DOESNT_EXISTS = "The file was added to index but now it doesn't exist";
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
	 * Helper method to return the correct implementation of the InodeHandle class.
	 * @return a {@link DirectoryHandle} if path represents a directory or
	 * a {@link FileHandle} if path represents a file. Links aren't supported.
	 */
	private static InodeHandle getInodeHandleFor(String path) throws FileNotFoundException {
		if (InodeHandle.isDirectory(path)) {
			return new DirectoryHandle(path);
		} else if (InodeHandle.isFile(path)) {
			return new FileHandle(path);
		} else {
			throw new UnsupportedOperationException("This listener is not capable of handling symbolic links");
		}
	}
	
	/**
	 * Method called whenever the slave's set is changed on file.
	 * @param event
	 */
	@EventSubscriber
	public void slavesChanged(VirtualFileSystemSlaveEvent event) {
		inodeUpdated(event.getPath());
	}
	
	/**
	 * Method called whenever the ownership of the given inode is changed
	 * @param event
	 */
	@EventSubscriber
	public void ownershipChanged(VirtualFileSystemOwnershipEvent event) {
		inodeUpdated(event.getPath());
	}
	
	/**
	 * Changing the slave set's and the ownership represents the same operation on the index,
	 * so this place is used to avoid code repetition.
	 * @param path
	 */
	protected void inodeUpdated(String path) {
		try {
			InodeHandle inode = getInodeHandleFor(path);

			getIndexEngine().updateInode(inode);
		} catch (FileNotFoundException e) {
			logger.error(FILE_WAS_ADDED_BUT_DOESNT_EXISTS, e);
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
		// TODO support renames.
	}
	
	/**
	 * Method called whenever an inode is creatd or deleted.
	 * Depends on {@link VirtualFileSystemInodeEvent} <code>type</code> property.
	 * @param event
	 */
	@EventSubscriber
	public void inodeEvent(VirtualFileSystemInodeEvent event) {
		InodeHandle inode = null;
		
		try {
			inode = getInodeHandleFor(event.getPath());
		} catch (FileNotFoundException e) {
			logger.error(FILE_WAS_ADDED_BUT_DOESNT_EXISTS, e);
			return;
		}
		
		if (event.getType() == VirtualFileSystemInodeEventType.CREATED) {
			inodeCreated(inode);
		} else if (event.getType() == VirtualFileSystemInodeEventType.DELETED) {
			inodeDeleted(inode);
		} else {
			throw new UnsupportedOperationException("The enum was changed but this code wasn't!");
		}
	}
	
	protected void inodeCreated(InodeHandle inode) {
		try {
			getIndexEngine().addInode(inode);
		} catch (IndexException e) {
			logger.error(EXCEPTION_OCCURED_WHILE_INDEXING, e);
		}
	}
	
	protected void inodeDeleted(InodeHandle inode) {
		try {
			getIndexEngine().deleteInode(inode);
		} catch (IndexException e) {
			logger.error(EXCEPTION_OCCURED_WHILE_INDEXING, e);
		}
	}
}


