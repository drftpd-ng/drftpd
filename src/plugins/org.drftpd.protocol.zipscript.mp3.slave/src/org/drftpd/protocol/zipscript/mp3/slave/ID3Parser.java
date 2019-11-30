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
package org.drftpd.protocol.zipscript.mp3.slave;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;

import org.drftpd.protocol.zipscript.mp3.common.ID3Tag;

/**
 * @author djb61
 * @author teflon
 * @version $Id$
 */
public class ID3Parser extends RandomAccessFile {

	private static final long TAGLENGTH = 128;

	private ID3Tag _id3tag;

	/**
	 * @param file
	 * @param mode
	 * @throws java.io.FileNotFoundException
	 */
	public ID3Parser(File file, String mode) throws FileNotFoundException {
		super(file, mode);
		processFile();
	}

	/**
	 * @param file
	 * @param mode
	 * @throws java.io.FileNotFoundException
	 */
	public ID3Parser(String file, String mode) throws FileNotFoundException {
		super(file, mode);
		processFile();
	}

	private void processFile() {
		_id3tag = null;
		if (existsID3v1Tag()) {
			_id3tag = new ID3Tag();
			_id3tag = readID3v1Tag(_id3tag);
		}
		try {
			this.close();
		} catch (IOException e) {
			// Do nothing, probably file already closed
		}
	}

	public ID3Tag getID3Tag() {
		return _id3tag;
	}

	/**        Determines, if an id3v1 tag exists.
	 *        @return true if an id3v1 tag exists.
	 */
	private boolean existsID3v1Tag() {
		boolean hasTag = false;

		try {
			seek(length() - TAGLENGTH);

			byte[] tag = new byte[128];
			read(tag);

			String tagString = new String(tag);

			if (tagString.substring(0, 3).equals("TAG")) {
				hasTag = true;
			}
		} catch (IOException e) {
			// No need to do anything here, will just result in false being returned
		}

		return hasTag;
	}

	/** Reads an ID3v1Tag
	 *        @return The ID3v1Tag read from file or an empty ID3v1Tag if no tag was found.
	 */
	private ID3Tag readID3v1Tag(ID3Tag id3tag) {

		try {
			seek(length() - TAGLENGTH);

			byte[] tag = new byte[128];
			read(tag);

			String tagString = new String(tag);

			String title = tagString.substring(3, 33);
			String artist = tagString.substring(33, 63);
			String album = tagString.substring(63, 93);
			String year = tagString.substring(93, 97);
			String comment = tagString.substring(97, 125);
			byte track = tag[126];
			byte genre = tag[127];

			id3tag.setTitle(title.trim());
			id3tag.setArtist(artist.trim());
			id3tag.setAlbum(album.trim());
			id3tag.setYear(year.trim());
			id3tag.setComment(comment.trim());
			id3tag.setTrack(track);
			id3tag.setGenre(genre);

		} catch (IOException e) {
			// Just let an empty tag be returned
		} catch (StringIndexOutOfBoundsException e) {
			// Just let an empty/incomplete tag be returned
		}

		return id3tag;
	}

}
