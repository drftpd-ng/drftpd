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
package org.drftpd.zipscript.master.flac.indexation;

import org.apache.commons.lang3.math.NumberUtils;
import org.apache.lucene.document.Document;
import org.drftpd.common.dynamicdata.KeyNotFoundException;
import org.drftpd.master.indexation.IndexDataExtensionInterface;
import org.drftpd.master.indexation.LuceneUtils;
import org.drftpd.master.vfs.event.ImmutableInodeHandle;
import org.drftpd.zipscript.common.flac.FlacInfo;
import org.drftpd.zipscript.common.flac.VorbisTag;

/**
 * @author norox
 */
public class FlacDataExtension implements IndexDataExtensionInterface {

    @Override
    public void addData(Document doc, ImmutableInodeHandle inode) {
        VorbisTag vorbisTag = null;
        try {
            FlacInfo flacInfo = inode.getPluginMetaData(FlacInfo.FLACINFO);
            vorbisTag = flacInfo.getVorbisTag();
        } catch (KeyNotFoundException e) {
            // Fields will be cleared below
        }
        if (vorbisTag == null) {
            LuceneUtils.addTextField(doc, "flacGenre", "");
            LuceneUtils.addTextField(doc, "flacTitle", "");
            LuceneUtils.addTextField(doc, "flacArtist", "");
            LuceneUtils.addTextField(doc, "flacAlbum", "");
            LuceneUtils.addIntField(doc, "flacYear", -1);
        } else {
            LuceneUtils.addTextField(doc, "flacGenre", vorbisTag.getGenre());
            LuceneUtils.addTextField(doc, "flacTitle", vorbisTag.getTitle());
            LuceneUtils.addTextField(doc, "flacArtist", vorbisTag.getArtist());
            LuceneUtils.addTextField(doc, "flacAlbum", vorbisTag.getAlbum());
            LuceneUtils.addIntField(doc, "flacYear", NumberUtils.isDigits(vorbisTag.getYear()) ?
                    Integer.parseInt(vorbisTag.getYear()) : -1);
        }
    }
}
