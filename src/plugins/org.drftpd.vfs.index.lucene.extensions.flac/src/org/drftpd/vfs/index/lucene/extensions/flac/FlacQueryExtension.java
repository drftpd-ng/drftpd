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
package org.drftpd.vfs.index.lucene.extensions.flac;

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
 * @author norox
 */
public class FlacQueryExtension implements QueryTermExtensionInterface {

	private static final Term TERM_GENRE = new Term("flacGenre", "");
	private static final Term TERM_TITLE = new Term("flacTitle", "");
	private static final Term TERM_ARTIST = new Term("flacArtist", "");
	private static final Term TERM_ALBUM = new Term("flacAlbum", "");
	
	@Override
	public void addQueryTerms(BooleanQuery query, AdvancedSearchParams params) {
		try {
			FlacQueryParams queryParams = params.getExtensionData(FlacQueryParams.FLACQUERYPARAMS);
			if (queryParams.getGenre() != null) {
				Query genreQuery = LuceneUtils.analyze("flacGenre", TERM_GENRE, queryParams.getGenre());
				query.add(genreQuery, Occur.MUST);
			}
			if (queryParams.getTitle() != null) {
				Query titleQuery = LuceneUtils.analyze("flacTitle", TERM_TITLE, queryParams.getTitle());
				query.add(titleQuery, Occur.MUST);
			}
			if (queryParams.getArtist() != null) {
				Query artistQuery = LuceneUtils.analyze("flacArtist", TERM_ARTIST, queryParams.getArtist());
				query.add(artistQuery, Occur.MUST);
			}
			if (queryParams.getAlbum() != null) {
				Query albumQuery = LuceneUtils.analyze("flacAlbum", TERM_ALBUM, queryParams.getAlbum());
				query.add(albumQuery, Occur.MUST);
			}
			if (queryParams.getMinYear() != null || queryParams.getMaxYear() != null) {
				Query yearQuery = NumericRangeQuery.newIntRange("flacYear",
						queryParams.getMinYear(), queryParams.getMaxYear(), true, true);
				query.add(yearQuery, Occur.MUST);
			}
		} catch (KeyNotFoundException e) {
			// No FLAC terms to include, return without amending query
		}
	}

}
