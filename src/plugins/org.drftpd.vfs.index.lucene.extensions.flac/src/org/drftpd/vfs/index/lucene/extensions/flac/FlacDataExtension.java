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

import org.apache.commons.lang3.math.NumberUtils;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericField;
import org.drftpd.dynamicdata.KeyNotFoundException;
import org.drftpd.protocol.zipscript.flac.common.VorbisTag;
import org.drftpd.protocol.zipscript.flac.common.FlacInfo;
import org.drftpd.vfs.event.ImmutableInodeHandle;
import org.drftpd.vfs.index.lucene.extensions.IndexDataExtensionInterface;

/**
 * @author norox
 */
public class FlacDataExtension implements IndexDataExtensionInterface {

	private static final Field FIELD_GENRE = new Field("flacGenre", "", Field.Store.YES, Field.Index.ANALYZED);
	private static final Field FIELD_TITLE = new Field("flacTitle", "", Field.Store.YES, Field.Index.ANALYZED);
	private static final Field FIELD_ARTIST = new Field("flacArtist", "", Field.Store.YES, Field.Index.ANALYZED);
	private static final Field FIELD_ALBUM = new Field("flacAlbum", "", Field.Store.YES, Field.Index.ANALYZED);
	private static final NumericField FIELD_YEAR = new NumericField("flacYear", Field.Store.YES, Boolean.TRUE);
	
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
		VorbisTag vorbisTag = null;
		try {
			FlacInfo flacInfo = inode.getPluginMetaData(FlacInfo.FLACINFO);
			vorbisTag = flacInfo.getVorbisTag();
		} catch (KeyNotFoundException e) {
			// Fields will be cleared below
		}
		if (vorbisTag == null) {
			FIELD_GENRE.setValue("");
			FIELD_TITLE.setValue("");
			FIELD_ARTIST.setValue("");
			FIELD_ALBUM.setValue("");
			FIELD_YEAR.setIntValue(-1);
		} else {
			FIELD_GENRE.setValue(vorbisTag.getGenre());
			FIELD_TITLE.setValue(vorbisTag.getTitle());
			FIELD_ARTIST.setValue(vorbisTag.getArtist());
			FIELD_ALBUM.setValue(vorbisTag.getAlbum());
			FIELD_YEAR.setIntValue(NumberUtils.isDigits(vorbisTag.getYear()) ?
					Integer.parseInt(vorbisTag.getYear()) : -1);
		}
	}
}
