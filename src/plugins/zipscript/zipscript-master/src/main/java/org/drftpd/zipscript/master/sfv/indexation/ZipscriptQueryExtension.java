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
package org.drftpd.zipscript.master.sfv.indexation;

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
public class ZipscriptQueryExtension implements QueryTermExtensionInterface {

    @Override
    public void addQueryTerms(BooleanQuery.Builder query, AdvancedSearchParams params) {
        try {
            ZipscriptQueryParams queryParams = params.getExtensionData(ZipscriptQueryParams.ZIPSCRIPTQUERYPARAMS);
            if (queryParams.getMinPresent() != null || queryParams.getMaxPresent() != null) {
                Query presentQuery = LuceneUtils.intRangeQuery("present", queryParams.getMinPresent(), queryParams.getMaxPresent());
                query.add(presentQuery, Occur.MUST);
            }
            if (queryParams.getMinMissing() != null || queryParams.getMaxMissing() != null) {
                Query missingQuery = LuceneUtils.intRangeQuery("missing", queryParams.getMinMissing(), queryParams.getMaxMissing());
                query.add(missingQuery, Occur.MUST);
            }
            if (queryParams.getMinPercent() != null || queryParams.getMaxPercent() != null) {
                Query percentQuery = LuceneUtils.intRangeQuery("percent", queryParams.getMinPercent(), queryParams.getMaxPercent());
                query.add(percentQuery, Occur.MUST);
            }
        } catch (KeyNotFoundException e) {
            // No MP3 terms to include, return without amending query
        }
    }

}
