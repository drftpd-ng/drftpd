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
package org.drftpd.vfs.index.lucene.extensions.imdb;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericField;
import org.drftpd.dynamicdata.KeyNotFoundException;
import org.drftpd.protocol.imdb.common.IMDBInfo;
import org.drftpd.vfs.event.ImmutableInodeHandle;
import org.drftpd.vfs.index.lucene.extensions.IndexDataExtensionInterface;

/**
 * @author scitz0
 * @version $Id: MP3DataExtension.java 2491 2011-07-11 21:56:53Z scitz0 $
 */
public class IMDBDataExtension implements IndexDataExtensionInterface {

	private static final Field FIELD_TITLE = new Field("imdbtitle", "", Field.Store.YES, Field.Index.ANALYZED);
	private static final Field FIELD_DIRECTOR = new Field("imdbdirector", "", Field.Store.YES, Field.Index.ANALYZED);
	private static final Field FIELD_GENRES = new Field("imdbgenres", "", Field.Store.YES, Field.Index.ANALYZED);
	private static final NumericField FIELD_VOTES = new NumericField("imdbvotes", Field.Store.YES, Boolean.TRUE);
	private static final NumericField FIELD_RATING = new NumericField("imdbrating", Field.Store.YES, Boolean.TRUE);
	private static final NumericField FIELD_YEAR = new NumericField("imdbyear", Field.Store.YES, Boolean.TRUE);
	private static final NumericField FIELD_RUNTIME = new NumericField("imdbruntime", Field.Store.YES, Boolean.TRUE);
	
	@Override
	public void initializeFields(Document doc) {
		doc.add(FIELD_TITLE);
		doc.add(FIELD_DIRECTOR);
		doc.add(FIELD_GENRES);
		doc.add(FIELD_VOTES);
		doc.add(FIELD_RATING);
		doc.add(FIELD_YEAR);
		doc.add(FIELD_RUNTIME);
	}

	@Override
	public void addData(Document doc, ImmutableInodeHandle inode) {
		IMDBInfo imdbInfo = null;
		try {
			imdbInfo = inode.getPluginMetaData(IMDBInfo.IMDBINFO);
		} catch (KeyNotFoundException e) {
			// Fields will be cleared below
		}
		if (imdbInfo == null || !imdbInfo.getMovieFound()) {
			FIELD_TITLE.setValue("");
			FIELD_DIRECTOR.setValue("");
			FIELD_GENRES.setValue("");
			FIELD_VOTES.setIntValue(-1);
			FIELD_RATING.setIntValue(-1);
			FIELD_YEAR.setIntValue(-1);
			FIELD_RUNTIME.setIntValue(-1);
		} else {
			FIELD_TITLE.setValue(imdbInfo.getTitle());
			FIELD_DIRECTOR.setValue(imdbInfo.getDirector());
			FIELD_GENRES.setValue(imdbInfo.getGenres());
			FIELD_VOTES.setIntValue(imdbInfo.getVotes() != null ? imdbInfo.getVotes() : -1);
			FIELD_RATING.setIntValue(imdbInfo.getRating() != null ? imdbInfo.getRating() : -1);
			FIELD_YEAR.setIntValue(imdbInfo.getYear() != null ? imdbInfo.getYear() : -1);
			FIELD_RUNTIME.setIntValue(imdbInfo.getRuntime() != null ? imdbInfo.getRuntime() : -1);
		}
	}

}
