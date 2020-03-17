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
package org.drftpd.protocol.zipscript.mp3.common;

import java.io.Serializable;

/**
 * @author djb61
 * @version $Id$
 */
@SuppressWarnings("serial")
public class ID3Tag implements Serializable {

	private String _artist = "";
    private String _album = "";
    private String _title = "";
    private String _comment = "";
    private String _year = "";
    byte _genre = -1;
    byte _track = 0;

    public ID3Tag() {
    }

    public String getTitle() {
        return _title;
    }

    public void setTitle(String title) {
        _title = title;
    }

    public String getArtist() {
        return _artist;
    }

    public void setArtist(String artist) {
        _artist = artist;
    }

    public String getAlbum() {
        return _album;
    }

    public void setAlbum(String album) {
        _album = album;
    }

    public String getComment() {
        return _comment;
    }

    public void setComment(String comment) {
        _comment = comment;
    }

    public String getYear() {
        return _year;
    }

    public void setYear(String year) {
        _year = year;
    }

    public byte getTrack() {
        return _track;
    }

    public void setTrack(byte track) {
        _track = track;
    }

    public String getGenre() {
        return ID3GenreList.getGenre(_genre);
    }

    public void setGenre(byte genre) {
        _genre = genre;
    }

}
