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

import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.drftpd.common.dynamicdata.KeyNotFoundException;
import org.drftpd.master.indexation.AdvancedSearchParams;
import org.drftpd.master.indexation.LuceneUtils;
import org.drftpd.master.indexation.QueryTermExtensionInterface;

/**
 * @author scitz0
 * @version $Id: MP3QueryExtension.java 2485 2011-07-10 14:33:19Z djb61 $
 */
public class TvMazeQueryExtension implements QueryTermExtensionInterface {

    @Override
    public void addQueryTerms(BooleanQuery.Builder query, AdvancedSearchParams params) {
        try {
            TvMazeQueryParams queryParams = params.getExtensionData(TvMazeQueryParams.TvMazeQUERYPARAMS);
            if (queryParams.getName() != null) {
                Query nameQuery = LuceneUtils.analyze("tvmazename", queryParams.getName());
                query.add(nameQuery, Occur.MUST);
            }
            if (queryParams.getGenre() != null) {
                Query genreQuery = LuceneUtils.analyze("tvmazegenre", queryParams.getGenre());
                query.add(genreQuery, Occur.MUST);
            }
            if (queryParams.getSeason() != null) {
                Query seasonQuery = LuceneUtils.intRangeQuery("tvmazeseason", queryParams.getSeason(), queryParams.getSeason());
                query.add(seasonQuery, Occur.MUST);
            }
            if (queryParams.getNumber() != null) {
                Query numberQuery = LuceneUtils.intRangeQuery("tvmazenumber", queryParams.getNumber(), queryParams.getNumber());
                query.add(numberQuery, Occur.MUST);
            }
            if (queryParams.getType() != null) {
                Query typeQuery = LuceneUtils.analyze("tvmazetype", queryParams.getType());
                query.add(typeQuery, Occur.MUST);
            }
            if (queryParams.getStatus() != null) {
                Query statusQuery = LuceneUtils.analyze("tvmazestatus", queryParams.getStatus());
                query.add(statusQuery, Occur.MUST);
            }
            if (queryParams.getLanguage() != null) {
                Query languageQuery = LuceneUtils.analyze("tvmazelanguage", queryParams.getLanguage());
                query.add(languageQuery, Occur.MUST);
            }
            if (queryParams.getCountry() != null) {
                Query countryQuery = LuceneUtils.analyze("tvmazecountry", queryParams.getCountry());
                query.add(countryQuery, Occur.MUST);
            }
            if (queryParams.getNetwork() != null) {
                Query networkQuery = LuceneUtils.analyze("tvmazenetwork", queryParams.getNetwork());
                query.add(networkQuery, Occur.MUST);
            }
        } catch (KeyNotFoundException e) {
            // No TvMaze terms to include, return without amending query
        }
    }

}
