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
package org.drftpd.vfs.index.lucene.extensions.zipscript;

import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.NumericRangeQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.BooleanClause.Occur;
import org.drftpd.dynamicdata.KeyNotFoundException;
import org.drftpd.vfs.index.AdvancedSearchParams;
import org.drftpd.vfs.index.lucene.extensions.QueryTermExtensionInterface;

/**
 * @author scitz0
 * @version $Id$
 */
public class ZipscriptQueryExtension implements QueryTermExtensionInterface {

	@Override
	public void addQueryTerms(BooleanQuery query, AdvancedSearchParams params) {
		try {
			ZipscriptQueryParams queryParams = params.getExtensionData(ZipscriptQueryParams.ZIPSCRIPTQUERYPARAMS);
			if (queryParams.getMinPresent() != null || queryParams.getMaxPresent() != null) {
				Query presentQuery = NumericRangeQuery.newIntRange("present",
						queryParams.getMinPresent(), queryParams.getMaxPresent(), true, true);
				query.add(presentQuery, Occur.MUST);
			}
			if (queryParams.getMinMissing() != null || queryParams.getMaxMissing() != null) {
				Query missingQuery = NumericRangeQuery.newIntRange("missing",
						queryParams.getMinMissing(), queryParams.getMaxMissing(), true, true);
				query.add(missingQuery, Occur.MUST);
			}
			if (queryParams.getMinPercent() != null || queryParams.getMaxPercent() != null) {
				Query percentQuery = NumericRangeQuery.newIntRange("percent",
						queryParams.getMinPercent(), queryParams.getMaxPercent(), true, true);
				query.add(percentQuery, Occur.MUST);
			}
		} catch (KeyNotFoundException e) {
			// No MP3 terms to include, return without amending query
		}
	}

}
