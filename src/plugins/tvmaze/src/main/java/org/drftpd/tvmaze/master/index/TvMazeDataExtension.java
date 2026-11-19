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
package org.drftpd.tvmaze.master.index;

import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.document.Document;
import org.drftpd.common.dynamicdata.KeyNotFoundException;
import org.drftpd.master.indexation.IndexDataExtensionInterface;
import org.drftpd.master.indexation.LuceneUtils;
import org.drftpd.master.vfs.event.ImmutableInodeHandle;
import org.drftpd.tvmaze.master.metadata.TvMazeInfo;

public class TvMazeDataExtension implements IndexDataExtensionInterface {

    @Override
    public void addData(Document doc, ImmutableInodeHandle inode) {
        TvMazeInfo tvmazeInfo = null;
        try {
            tvmazeInfo = inode.getPluginMetaData(TvMazeInfo.TVMAZEINFO);
        } catch (KeyNotFoundException e) {
            // Fields will be cleared below
        }
        if (tvmazeInfo == null) {
            LuceneUtils.addTextField(doc, "tvmazename", "");
            LuceneUtils.addTextField(doc, "tvmazegenre", "");
            LuceneUtils.addIntField(doc, "tvmazeseason", -1);
            LuceneUtils.addIntField(doc, "tvmazenumber", -1);
            LuceneUtils.addTextField(doc, "tvmazetype", "");
            LuceneUtils.addTextField(doc, "tvmazestatus", "");
            LuceneUtils.addTextField(doc, "tvmazelanguage", "");
            LuceneUtils.addTextField(doc, "tvmazecountry", "");
            LuceneUtils.addTextField(doc, "tvmazenetwork", "");
        } else {
            LuceneUtils.addTextField(doc, "tvmazename", tvmazeInfo.getName());
            LuceneUtils.addTextField(doc, "tvmazegenre", StringUtils.join(tvmazeInfo.getGenres(), " "));
            if (tvmazeInfo.getEPList().length == 1) {
                LuceneUtils.addIntField(doc, "tvmazeseason", tvmazeInfo.getEPList()[0].getSeason());
                LuceneUtils.addIntField(doc, "tvmazenumber", tvmazeInfo.getEPList()[0].getNumber());
            } else {
                LuceneUtils.addIntField(doc, "tvmazeseason", -1);
                LuceneUtils.addIntField(doc, "tvmazenumber", -1);
            }
            LuceneUtils.addTextField(doc, "tvmazetype", tvmazeInfo.getType());
            LuceneUtils.addTextField(doc, "tvmazestatus", tvmazeInfo.getStatus());
            LuceneUtils.addTextField(doc, "tvmazelanguage", tvmazeInfo.getLanguage());
            LuceneUtils.addTextField(doc, "tvmazecountry", tvmazeInfo.getCountry());
            LuceneUtils.addTextField(doc, "tvmazenetwork", tvmazeInfo.getNetwork());
        }
    }

}
