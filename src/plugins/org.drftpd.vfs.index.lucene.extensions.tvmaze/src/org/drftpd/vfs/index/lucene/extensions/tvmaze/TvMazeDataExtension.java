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

import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericField;
import org.drftpd.dynamicdata.KeyNotFoundException;
import org.drftpd.commands.tvmaze.metadata.TvMazeInfo;
import org.drftpd.vfs.event.ImmutableInodeHandle;
import org.drftpd.vfs.index.lucene.extensions.IndexDataExtensionInterface;

public class TvMazeDataExtension implements IndexDataExtensionInterface {

	private static final Field FIELD_NAME = new Field("tvmazename", "", Field.Store.YES, Field.Index.ANALYZED);
	private static final Field FIELD_GENRE = new Field("tvmazegenre", "", Field.Store.YES, Field.Index.ANALYZED);
	private static final NumericField FIELD_SEASON = new NumericField("tvmazeseason", Field.Store.YES, Boolean.TRUE);
	private static final NumericField FIELD_NUMBER = new NumericField("tvmazenumber", Field.Store.YES, Boolean.TRUE);
	private static final Field FIELD_TYPE = new Field("tvmazetype", "", Field.Store.YES, Field.Index.ANALYZED);
	private static final Field FIELD_STATUS = new Field("tvmazestatus", "", Field.Store.YES, Field.Index.ANALYZED);
	private static final Field FIELD_LANGUAGE = new Field("tvmazelanguage", "", Field.Store.YES, Field.Index.ANALYZED);
	private static final Field FIELD_COUNTRY = new Field("tvmazecountry", "", Field.Store.YES, Field.Index.ANALYZED);
	private static final Field FIELD_NETWORK = new Field("tvmazenetwork", "", Field.Store.YES, Field.Index.ANALYZED);
	
	@Override
	public void initializeFields(Document doc) {
		doc.add(FIELD_NAME);
		doc.add(FIELD_GENRE);
		doc.add(FIELD_SEASON);
		doc.add(FIELD_NUMBER);
		doc.add(FIELD_TYPE);
		doc.add(FIELD_STATUS);
		doc.add(FIELD_LANGUAGE);
		doc.add(FIELD_COUNTRY);
		doc.add(FIELD_NETWORK);
	}

	@Override
	public void addData(Document doc, ImmutableInodeHandle inode) {
		TvMazeInfo tvmazeInfo = null;
		try {
			tvmazeInfo = inode.getPluginMetaData(TvMazeInfo.TVMAZEINFO);
		} catch (KeyNotFoundException e) {
			// Fields will be cleared below
		}
		if (tvmazeInfo == null) {
			FIELD_NAME.setValue("");
			FIELD_GENRE.setValue("");
			FIELD_SEASON.setIntValue(-1);
			FIELD_NUMBER.setIntValue(-1);
			FIELD_TYPE.setValue("");
			FIELD_STATUS.setValue("");
			FIELD_LANGUAGE.setValue("");
			FIELD_COUNTRY.setValue("");
			FIELD_NETWORK.setValue("");
		} else {
			FIELD_NAME.setValue(tvmazeInfo.getName());
			FIELD_GENRE.setValue(StringUtils.join(tvmazeInfo.getGenres(), " "));
			if (tvmazeInfo.getEPList().length == 1) {
				FIELD_SEASON.setIntValue(tvmazeInfo.getEPList()[0].getSeason());
				FIELD_NUMBER.setIntValue(tvmazeInfo.getEPList()[0].getNumber());
			}
			FIELD_TYPE.setValue(tvmazeInfo.getType());
			FIELD_STATUS.setValue(tvmazeInfo.getStatus());
			FIELD_LANGUAGE.setValue(tvmazeInfo.getLanguage());
			FIELD_COUNTRY.setValue(tvmazeInfo.getCountry());
			FIELD_NETWORK.setValue(tvmazeInfo.getNetwork());
		}
	}

}
