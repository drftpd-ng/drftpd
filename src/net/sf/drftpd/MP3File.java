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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;


/**
 * @author Teflon-
 *
 */
public class MP3File extends RandomAccessFile {
    final static long TAGLENGTH = 128;
    public boolean hasID3v1Tag = false;

    /**
     * @param file
     * @param mode
     * @throws java.io.FileNotFoundException
     */
    public MP3File(File file, String mode) throws FileNotFoundException {
        super(file, mode);
        hasID3v1Tag = existsID3v1Tag();
    }

    /**
     * @param file
     * @param mode
     * @throws java.io.FileNotFoundException
     */
    public MP3File(String file, String mode) throws FileNotFoundException {
        super(file, mode);
        hasID3v1Tag = existsID3v1Tag();
    }

    /**        Determines, if an id3v1 tag exists.
     *        @return true if an id3v1 tag exists.
     */
    public boolean existsID3v1Tag() {
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
            System.out.println(e);
        }

        return hasTag;
    }

    /** Reads an ID3v1Tag
     *        @return The ID3v1Tag read from file or an empty ID3v1Tag if no tag was found.
     */
    public ID3Tag readID3v1Tag() {
        ID3Tag id3tag = new ID3Tag();

        if (hasID3v1Tag) {
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

                id3tag.setTitle(title);
                id3tag.setArtist(artist);
                id3tag.setAlbum(album);
                id3tag.setYear(year);
                id3tag.setComment(comment);
                id3tag.setTrack(track);
                id3tag.setGenre(genre);
            } catch (IOException e) {
                System.out.println(e);
            }
        }

        return id3tag;
    }

    /**        Writes the given ID3v1Tag to the file.
     *        @throws IOException
     */
    public void writeID3v1Tag(ID3Tag tag) throws IOException {
        if (hasID3v1Tag) {
            seek(length() - TAGLENGTH);
            write(tag.toByteArray());
        } else {
            setLength(this.length() + 128);
            seek(length() - TAGLENGTH);
            write(tag.toByteArray());
        }
    }
}
