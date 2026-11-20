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

import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.drftpd.common.dynamicdata.KeyNotFoundException;
import org.drftpd.master.indexation.AdvancedSearchParams;
import org.drftpd.master.indexation.LuceneUtils;
import org.drftpd.master.indexation.QueryTermExtensionInterface;

/**
 * @author scitz0
 * @version $Id$
 */
public class MP3QueryExtension implements QueryTermExtensionInterface {

    @Override
    public void addQueryTerms(BooleanQuery.Builder query, AdvancedSearchParams params) {
        try {
            MP3QueryParams queryParams = params.getExtensionData(MP3QueryParams.MP3QUERYPARAMS);
            if (queryParams.getGenre() != null) {
                Query genreQuery = LuceneUtils.analyze("mp3genre", queryParams.getGenre());
                query.add(genreQuery, Occur.MUST);
            }
            if (queryParams.getTitle() != null) {
                Query titleQuery = LuceneUtils.analyze("mp3title", queryParams.getTitle());
                query.add(titleQuery, Occur.MUST);
            }
            if (queryParams.getArtist() != null) {
                Query artistQuery = LuceneUtils.analyze("mp3artist", queryParams.getArtist());
                query.add(artistQuery, Occur.MUST);
            }
            if (queryParams.getAlbum() != null) {
                Query albumQuery = LuceneUtils.analyze("mp3album", queryParams.getAlbum());
                query.add(albumQuery, Occur.MUST);
            }
            if (queryParams.getMinYear() != null || queryParams.getMaxYear() != null) {
                Query yearQuery = LuceneUtils.intRangeQuery("mp3year", queryParams.getMinYear(), queryParams.getMaxYear());
                query.add(yearQuery, Occur.MUST);
            }
        } catch (KeyNotFoundException e) {
            // No MP3 terms to include, return without amending query
        }
    }

}
