/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * DrFTPD is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with DrFTPD; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.drftpd.master.indexation;

import org.apache.lucene.document.Document;
import org.drftpd.master.vfs.event.ImmutableInodeHandle;

/**
 * Extension point to store additional (plugin) data about an inode in the index.
 * <p>
 * Implementations are discovered by reflection and instantiated once.
 * {@link #addData(Document, ImmutableInodeHandle)} may be called concurrently from several threads,
 * so it must not keep per-call state in fields.
 */
public interface IndexDataExtensionInterface {

    /**
     * This method is called whenever an inode is being added to, or updated in, the index.
     * A new document is passed for every call: add one field for every value the extension
     * stores, using a "no value" placeholder (empty string, -1) when the inode has no data, so that
     * all documents have the same fields.
     * <p>
     * Fields must be added with {@link LuceneUtils#addTextField}, {@link LuceneUtils#addIntField} or
     * {@link LuceneUtils#addLongField}: when a directory is renamed the documents of its children are
     * rebuilt from their stored values, a stored string is then re-indexed as a text field and a
     * stored int/long as an int/long point field.
     *
     * @param doc   The document to populate fields in.
     * @param inode The inode currently being indexed.
     */
    void addData(Document doc, ImmutableInodeHandle inode);
}
