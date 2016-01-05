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
package org.drftpd.vfs.index.lucene.extensions.tvmaze;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.NumericRangeQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.BooleanClause.Occur;
import org.drftpd.dynamicdata.KeyNotFoundException;
import org.drftpd.vfs.index.AdvancedSearchParams;
import org.drftpd.vfs.index.lucene.LuceneUtils;
import org.drftpd.vfs.index.lucene.extensions.QueryTermExtensionInterface;

/**
 * @author scitz0
 * @version $Id: MP3QueryExtension.java 2485 2011-07-10 14:33:19Z djb61 $
 */
public class TvMazeQueryExtension implements QueryTermExtensionInterface {

	private static final Term TERM_NAME = new Term("tvmazename", "");
	private static final Term TERM_GENRE = new Term("tvmazegenre", "");
	private static final Term TERM_TYPE = new Term("tvmazetype", "");
	private static final Term TERM_STATUS = new Term("tvmazestatus", "");
	private static final Term TERM_LANGUAGE = new Term("tvmazelanguage", "");
	private static final Term TERM_COUNTRY = new Term("tvmazecountry", "");
	private static final Term TERM_NETWORK = new Term("tvmazenetwork", "");
	
	@Override
	public void addQueryTerms(BooleanQuery query, AdvancedSearchParams params) {
		try {
			TvMazeQueryParams queryParams = params.getExtensionData(TvMazeQueryParams.TvMazeQUERYPARAMS);
			if (queryParams.getName() != null) {
				Query nameQuery = LuceneUtils.analyze("tvmazename", TERM_NAME, queryParams.getName());
				query.add(nameQuery, Occur.MUST);
			}
			if (queryParams.getGenre() != null) {
				Query genreQuery = LuceneUtils.analyze("tvmazegenre", TERM_GENRE, queryParams.getGenre());
				query.add(genreQuery, Occur.MUST);
			}
			if (queryParams.getSeason() != null) {
				Query seasonQuery = NumericRangeQuery.newIntRange("tvmazeseason",
						queryParams.getSeason(), queryParams.getSeason(), true, true);
				query.add(seasonQuery, Occur.MUST);
			}
			if (queryParams.getNumber() != null) {
				Query numberQuery = NumericRangeQuery.newIntRange("tvmazenumber",
						queryParams.getNumber(), queryParams.getNumber(), true, true);
				query.add(numberQuery, Occur.MUST);
			}
			if (queryParams.getType() != null) {
				Query typeQuery = LuceneUtils.analyze("tvmazetype", TERM_TYPE, queryParams.getType());
				query.add(typeQuery, Occur.MUST);
			}
			if (queryParams.getStatus() != null) {
				Query statusQuery = LuceneUtils.analyze("tvmazestatus", TERM_STATUS, queryParams.getStatus());
				query.add(statusQuery, Occur.MUST);
			}
			if (queryParams.getLanguage() != null) {
				Query languageQuery = LuceneUtils.analyze("tvmazelanguage", TERM_LANGUAGE, queryParams.getLanguage());
				query.add(languageQuery, Occur.MUST);
			}
			if (queryParams.getCountry() != null) {
				Query countryQuery = LuceneUtils.analyze("tvmazecountry", TERM_COUNTRY, queryParams.getCountry());
				query.add(countryQuery, Occur.MUST);
			}
			if (queryParams.getNetwork() != null) {
				Query networkQuery = LuceneUtils.analyze("tvmazenetwork", TERM_NETWORK, queryParams.getNetwork());
				query.add(networkQuery, Occur.MUST);
			}
		} catch (KeyNotFoundException e) {
			// No TvMaze terms to include, return without amending query
		}
	}

}
