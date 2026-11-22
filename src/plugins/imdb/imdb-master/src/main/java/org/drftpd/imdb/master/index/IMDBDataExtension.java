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
package org.drftpd.imdb.master.index;

import org.apache.lucene.document.Document;
import org.drftpd.common.dynamicdata.KeyNotFoundException;
import org.drftpd.imdb.common.IMDBInfo;
import org.drftpd.master.indexation.IndexDataExtensionInterface;
import org.drftpd.master.indexation.LuceneUtils;
import org.drftpd.master.vfs.event.ImmutableInodeHandle;

/**
 * @author scitz0
 * @version $Id: MP3DataExtension.java 2491 2011-07-11 21:56:53Z scitz0 $
 */
public class IMDBDataExtension implements IndexDataExtensionInterface {

    @Override
    public void addData(Document doc, ImmutableInodeHandle inode) {
        IMDBInfo imdbInfo = null;
        try {
            imdbInfo = inode.getPluginMetaData(IMDBInfo.IMDBINFO);
        } catch (KeyNotFoundException e) {
            // Fields will be cleared below
        }
        if (imdbInfo == null || !imdbInfo.getMovieFound()) {
            LuceneUtils.addTextField(doc, "imdbtitle", "");
            LuceneUtils.addTextField(doc, "imdbdirector", "");
            LuceneUtils.addTextField(doc, "imdbgenres", "");
            LuceneUtils.addIntField(doc, "imdbvotes", -1);
            LuceneUtils.addIntField(doc, "imdbrating", -1);
            LuceneUtils.addIntField(doc, "imdbyear", -1);
            LuceneUtils.addIntField(doc, "imdbruntime", -1);
        } else {
            LuceneUtils.addTextField(doc, "imdbtitle", imdbInfo.getTitle());
            LuceneUtils.addTextField(doc, "imdbdirector", imdbInfo.getDirector());
            LuceneUtils.addTextField(doc, "imdbgenres", imdbInfo.getGenres());
            LuceneUtils.addIntField(doc, "imdbvotes", imdbInfo.getVotes() != null ? imdbInfo.getVotes() : -1);
            LuceneUtils.addIntField(doc, "imdbrating", imdbInfo.getRating() != null ? imdbInfo.getRating() : -1);
            LuceneUtils.addIntField(doc, "imdbyear", imdbInfo.getYear() != null ? imdbInfo.getYear() : -1);
            LuceneUtils.addIntField(doc, "imdbruntime", imdbInfo.getRuntime() != null ? imdbInfo.getRuntime() : -1);
        }
    }

}
