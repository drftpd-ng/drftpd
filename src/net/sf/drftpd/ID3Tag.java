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
package net.sf.drftpd;

import java.io.Serializable;


/**
 * @author Teflon
 *
 */
public class ID3Tag implements Serializable {
    String artist = "";
    String album = "";
    String title = "";
    String comment = "";
    String year = "";
    byte genre = 0;
    byte track = 0;

    /** Constructor to create an empty tag -> insert your own data */
    public ID3Tag() {
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getArtist() {
        return artist;
    }

    public void setArtist(String artist) {
        this.artist = artist;
    }

    public String getAlbum() {
        return album;
    }

    public void setAlbum(String album) {
        this.album = album;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public String getYear() {
        return year;
    }

    public void setYear(String year) {
        this.year = year;
    }

    public byte getTrack() {
        return track;
    }

    public void setTrack(byte track) {
        this.track = track;
    }

    public String getGenre() {
        return ID3GenreList.getGenre(genre);
    }

    public void setGenre(byte genre) {
        this.genre = genre;
    }

    /** Converts the whole tag to a byte array, so we can write it to a file or elsewhere...*/
    byte[] toByteArray() {
        byte[] tag = new byte[128];

        tag[0] = (byte) 'T';
        tag[1] = (byte) 'A';
        tag[2] = (byte) 'G';

        byte[] tempTitle = title.getBytes();
        byte[] tempArtist = artist.getBytes();
        byte[] tempAlbum = album.getBytes();
        byte[] tempYear = year.getBytes();
        byte[] tempComment = comment.getBytes();

        tag = writeToTag(tag, tempTitle, 3, 30);
        tag = writeToTag(tag, tempArtist, 33, 30);
        tag = writeToTag(tag, tempAlbum, 63, 30);
        tag = writeToTag(tag, tempYear, 93, 4);
        tag = writeToTag(tag, tempComment, 97, 28);

        tag[126] = track;
        tag[127] = genre;

        return tag;
    }

    /** Writes the array "bytes" to the array "tag" at given position "start" */
    private byte[] writeToTag(byte[] tag, byte[] bytes, int start, int length) {
        for (int i = start;
                ((i - start) < bytes.length) && (i < (start + length)); i++) {
            tag[i] = bytes[i - start];
        }

        return tag;
    }

    public String toString() {
        return "Artist=" + artist + "\n" + "Title=" + title + "\n" + "Album=" +
        album + "\n" + "Track=" + track + "\n" + "Year=" + year + "\n" +
        "Genre=" + getGenre() + "\n" + "Comment=" + comment;
    }
}
