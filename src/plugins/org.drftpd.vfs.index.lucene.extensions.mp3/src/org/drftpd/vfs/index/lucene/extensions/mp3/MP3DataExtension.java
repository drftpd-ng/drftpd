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
package org.drftpd.vfs.index.lucene.extensions.mp3;

import org.apache.commons.lang3.math.NumberUtils;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericField;
import org.drftpd.dynamicdata.KeyNotFoundException;
import org.drftpd.protocol.zipscript.mp3.common.ID3Tag;
import org.drftpd.protocol.zipscript.mp3.common.MP3Info;
import org.drftpd.vfs.event.ImmutableInodeHandle;
import org.drftpd.vfs.index.lucene.extensions.IndexDataExtensionInterface;

/**
 * @author scitz0
 * @version $Id$
 */
public class MP3DataExtension implements IndexDataExtensionInterface {

	private static final Field FIELD_GENRE = new Field("mp3genre", "", Field.Store.YES, Field.Index.ANALYZED);
	private static final Field FIELD_TITLE = new Field("mp3title", "", Field.Store.YES, Field.Index.ANALYZED);
	private static final Field FIELD_ARTIST = new Field("mp3artist", "", Field.Store.YES, Field.Index.ANALYZED);
	private static final Field FIELD_ALBUM = new Field("mp3album", "", Field.Store.YES, Field.Index.ANALYZED);
	private static final NumericField FIELD_YEAR = new NumericField("mp3year", Field.Store.YES, Boolean.TRUE);
	
	@Override
	public void initializeFields(Document doc) {
		doc.add(FIELD_GENRE);
		doc.add(FIELD_TITLE);
		doc.add(FIELD_ARTIST);
		doc.add(FIELD_ALBUM);
		doc.add(FIELD_YEAR);
	}

	@Override
	public void addData(Document doc, ImmutableInodeHandle inode) {
		ID3Tag id3Tag = null;
		try {
			MP3Info mp3Info = inode.getPluginMetaData(MP3Info.MP3INFO);
			id3Tag = mp3Info.getID3Tag();
		} catch (KeyNotFoundException e) {
			// Fields will be cleared below
		}
		if (id3Tag == null) {
			FIELD_GENRE.setValue("");
			FIELD_TITLE.setValue("");
			FIELD_ARTIST.setValue("");
			FIELD_ALBUM.setValue("");
			FIELD_YEAR.setIntValue(-1);
		} else {
			FIELD_GENRE.setValue(id3Tag.getGenre());
			FIELD_TITLE.setValue(id3Tag.getTitle());
			FIELD_ARTIST.setValue(id3Tag.getArtist());
			FIELD_ALBUM.setValue(id3Tag.getAlbum());
			FIELD_YEAR.setIntValue(NumberUtils.isDigits(id3Tag.getYear()) ?
					Integer.parseInt(id3Tag.getYear()) : -1);
		}
	}

}
