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
package org.drftpd.zipscript.master.mp3.indexation;

import org.apache.commons.lang3.math.NumberUtils;
import org.apache.lucene.document.Document;
import org.drftpd.common.dynamicdata.KeyNotFoundException;
import org.drftpd.master.indexation.IndexDataExtensionInterface;
import org.drftpd.master.indexation.LuceneUtils;
import org.drftpd.master.vfs.event.ImmutableInodeHandle;
import org.drftpd.zipscript.common.mp3.ID3Tag;
import org.drftpd.zipscript.common.mp3.MP3Info;

/**
 * @author scitz0
 * @version $Id$
 */
public class MP3DataExtension implements IndexDataExtensionInterface {

    @Override
    public void addData(Document doc, ImmutableInodeHandle inode) {
        ID3Tag id3Tag = null;
        try {
            MP3Info mp3Info = inode.getPluginMetaData(MP3Info.MP3INFO);
            id3Tag = mp3Info.getID3Tag();
        } catch (KeyNotFoundException e) {
            // Fields will be cleared below
        }
        if (id3Tag == null) {
            LuceneUtils.addTextField(doc, "mp3genre", "");
            LuceneUtils.addTextField(doc, "mp3title", "");
            LuceneUtils.addTextField(doc, "mp3artist", "");
            LuceneUtils.addTextField(doc, "mp3album", "");
            LuceneUtils.addIntField(doc, "mp3year", -1);
        } else {
            LuceneUtils.addTextField(doc, "mp3genre", id3Tag.getGenre());
            LuceneUtils.addTextField(doc, "mp3title", id3Tag.getTitle());
            LuceneUtils.addTextField(doc, "mp3artist", id3Tag.getArtist());
            LuceneUtils.addTextField(doc, "mp3album", id3Tag.getAlbum());
            LuceneUtils.addIntField(doc, "mp3year", NumberUtils.isDigits(id3Tag.getYear()) ?
                    Integer.parseInt(id3Tag.getYear()) : -1);
        }
    }

}
