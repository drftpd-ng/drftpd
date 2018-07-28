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

import org.drftpd.vfs.DirectoryHandle;
import org.drftpd.vfs.event.ImmutableInodeHandle;
import org.drftpd.vfs.index.AdvancedSearchParams.InodeType;

import java.io.FileNotFoundException;
import java.util.Map;
import java.util.Set;

/**
 * To create a new Indexing engine this interface must be implemented.
 * Further details of how it should work, check the rest of the API documentation.
 * @author fr0w
 * @version $Id$
 */
public interface IndexEngineInterface {
	/**
	 * This method should initialize the engine, making it ready to receive queries.<br>
	 * Below a list of what might be done here:
	 * <ul>
	 * <li>Estabilishing the connection to the database</li>
	 * <li>Read configuration parameters</li> 
	 * <li>Subscribe to some daemon event, like ReloadEvent.</li>
	 * <li>Etc...</li>
	 * </ul>
	 * @throws IndexException
	 */
    void init() throws IndexException;

	/**
	 * Adds an inode to the index.<br>
	 * The kind of information stored in the index is up to the implementation.
	 * @throws IndexException
	 */
    void addInode(ImmutableInodeHandle inode) throws IndexException;
	
	/**
	 * Deletes an inode from the index.
	 * @param inode
	 * @throws IndexException
	 */
    void deleteInode(ImmutableInodeHandle inode) throws IndexException;
	
	/**
	 * Updates the data of an inode.
	 * @param inode
	 * @throws IndexException
	 */
    void updateInode(ImmutableInodeHandle inode) throws IndexException;
	
	/**
	 * Renames the inode in the index
	 * @param fromInode
	 * @param toInode
	 * @throws IndexException
	 */
    void renameInode(ImmutableInodeHandle fromInode, ImmutableInodeHandle toInode) throws IndexException;
	
	/**
	 * Force the engine to save its current data.<br>
	 * Not all databases need this operation, if that's your case, make it a NoOp method.
	 * @throws IndexException
	 */
    void commit() throws IndexException;
	
	/**
	 * Removes ALL the content from the Index and recurse through the VFS recreating the index.
	 * @throws FileNotFoundException 
	 */
    void rebuildIndex() throws IndexException, FileNotFoundException;
	
	/**
	 * This method should return a Map containing information about the index engine.<br>
	 * Any kind of information is allowed but there are twothat are mandatory:
	 * <ul>
	 * <li>Number of inodes (the key must be called "inodes")</li>
	 * <li>Storage backend (the key must be called "backend")</li>
	 * </ul>
	 * @return
	 */
    Map<String, String> getStatus();
	
	Set<String> findInode(DirectoryHandle startNode, String text, InodeType inodeType) throws IndexException;
	Map<String,String> advancedFind(DirectoryHandle startNode, AdvancedSearchParams params) throws IndexException, IllegalArgumentException;
}
