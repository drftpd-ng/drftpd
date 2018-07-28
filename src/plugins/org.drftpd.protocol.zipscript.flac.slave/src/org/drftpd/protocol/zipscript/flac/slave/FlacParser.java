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
package org.drftpd.protocol.zipscript.flac.slave;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import org.jflac.FLACDecoder;
import org.jflac.metadata.Metadata;
import org.jflac.metadata.StreamInfo;
import org.jflac.metadata.VorbisComment;

import org.drftpd.protocol.zipscript.flac.common.FlacInfo;
import org.drftpd.protocol.zipscript.flac.common.VorbisTag;

/**
 * @author norox
 */
public class FlacParser {

	private File _flacfile;

	public FlacParser(File flacfile) {
		_flacfile = flacfile;
	}
	
	private String getVorbisTagByName(VorbisComment vorbisComment, String key) {
		try {
			return vorbisComment.getCommentByName(key)[0];
		} catch(ArrayIndexOutOfBoundsException e) {
			return "";
		}
	}
	
	public FlacInfo getFlacInfo() throws IOException {
		FileInputStream in = null;
		FLACDecoder decoder = null;
		Metadata metadata[] = null;
		try {
			in = new FileInputStream(_flacfile);
			decoder = new FLACDecoder(in);
			metadata = decoder.readMetadata();
		} finally {
			if (in != null) {
				in.close();
			}
		}
		
		if (decoder == null || metadata == null) {
			throw new IOException(_flacfile.getName() + " is not a valid FLAC file");
		}
		
		FlacInfo flacinfo  = new FlacInfo();
		VorbisTag vorbistag = new VorbisTag();
		for (Metadata meta : metadata) {
			if (meta instanceof StreamInfo) {
				StreamInfo streamInfo = (StreamInfo)meta;
				flacinfo.setSamplerate(streamInfo.getSampleRate());
				flacinfo.setChannels(streamInfo.getChannels());
				flacinfo.setRuntime((float)streamInfo.getTotalSamples() / (float)streamInfo.getSampleRate());
			} else if (meta instanceof VorbisComment) {
				VorbisComment vorbisComment = (VorbisComment)meta;
				vorbistag.setTitle(getVorbisTagByName(vorbisComment, "Title"));
				vorbistag.setArtist(getVorbisTagByName(vorbisComment, "Artist"));
				vorbistag.setAlbum(getVorbisTagByName(vorbisComment, "Album"));
				vorbistag.setYear(getVorbisTagByName(vorbisComment, "Date"));
				String s_track = getVorbisTagByName(vorbisComment, "Track");
				int i_track = 0;
				if (!s_track.equals("")) {
					i_track = Integer.parseInt(s_track);
				}
				vorbistag.setTrack(i_track);
				vorbistag.setGenre(getVorbisTagByName(vorbisComment, "Genre"));
			}
		}
		flacinfo.setVorbisTag(vorbistag);

		return flacinfo;
	}
}
