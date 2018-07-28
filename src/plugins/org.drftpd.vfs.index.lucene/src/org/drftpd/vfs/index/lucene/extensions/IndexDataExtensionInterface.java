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
package org.drftpd.vfs.index.lucene.extensions;

import org.apache.lucene.document.Document;
import org.drftpd.vfs.event.ImmutableInodeHandle;

/**
 * @author djb61
 * @version $Id$
 */
public interface IndexDataExtensionInterface {

	/**
	 * This method is called once when the extension is loaded. All field
	 * instances the extension intends to use for storing data should be
	 * added to the document at this time.
	 * 
	 * @param doc
	 *            The document to add fields to.
	 */
    void initializeFields(Document doc);
	
	/**
	 * This method is called whenever an inode is being added to the index.
	 * The fields added to the document in the initializeFields call will be
	 * present in this document and should have their values set appropriately.
	 * Any fields not relevant to this particular inode should be cleared.
	 * 
	 * @param doc
	 *            The document to populate fields in.
	 * 
	 * @param inode
	 *            The inode currently being indexed.
	 */
    void addData(Document doc, ImmutableInodeHandle inode);
}
