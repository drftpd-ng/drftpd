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

import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.NumericRangeQuery;
import org.apache.lucene.search.Query;
import org.drftpd.common.dynamicdata.KeyNotFoundException;
import org.drftpd.master.indexation.AdvancedSearchParams;
import org.drftpd.master.indexation.LuceneUtils;
import org.drftpd.master.indexation.QueryTermExtensionInterface;

/**
 * @author scitz0
 * @version $Id: MP3QueryExtension.java 2485 2011-07-10 14:33:19Z djb61 $
 */
public class IMDBQueryExtension implements QueryTermExtensionInterface {

    private static final Term TERM_TITLE = new Term("imdbtitle", "");
    private static final Term TERM_DIRECTOR = new Term("imdbdirector", "");
    private static final Term TERM_GENRES = new Term("imdbgenres", "");

    @Override
    public void addQueryTerms(BooleanQuery query, AdvancedSearchParams params) {
        try {
            IMDBQueryParams queryParams = params.getExtensionData(IMDBQueryParams.IMDBQUERYPARAMS);
            if (queryParams.getTitle() != null) {
                Query titleQuery = LuceneUtils.analyze("imdbtitle", TERM_TITLE, queryParams.getTitle());
                query.add(titleQuery, Occur.MUST);
            }
            if (queryParams.getDirector() != null) {
                Query directorQuery = LuceneUtils.analyze("imdbdirector", TERM_DIRECTOR, queryParams.getDirector());
                query.add(directorQuery, Occur.MUST);
            }
            if (queryParams.getGenres() != null) {
                Query genresQuery = LuceneUtils.analyze("imdbgenres", TERM_GENRES, queryParams.getGenres());
                query.add(genresQuery, Occur.MUST);
            }
            if (queryParams.getMinVotes() != null || queryParams.getMaxVotes() != null) {
                Query votesQuery = NumericRangeQuery.newIntRange("imdbvotes",
                        queryParams.getMinVotes(), queryParams.getMaxVotes(), true, true);
                query.add(votesQuery, Occur.MUST);
            }
            if (queryParams.getMinRating() != null || queryParams.getMaxRating() != null) {
                Query ratingQuery = NumericRangeQuery.newIntRange("imdbrating",
                        queryParams.getMinRating(), queryParams.getMaxRating(), true, true);
                query.add(ratingQuery, Occur.MUST);
            }
            if (queryParams.getMinYear() != null || queryParams.getMaxYear() != null) {
                Query yearQuery = NumericRangeQuery.newIntRange("imdbyear",
                        queryParams.getMinYear(), queryParams.getMaxYear(), true, true);
                query.add(yearQuery, Occur.MUST);
            }
            if (queryParams.getMinRuntime() != null || queryParams.getMaxRuntime() != null) {
                Query runtimeQuery = NumericRangeQuery.newIntRange("imdbruntime",
                        queryParams.getMinRuntime(), queryParams.getMaxRuntime(), true, true);
                query.add(runtimeQuery, Occur.MUST);
            }
        } catch (KeyNotFoundException e) {
            // No IMDB terms to include, return without amending query
        }
    }

}
