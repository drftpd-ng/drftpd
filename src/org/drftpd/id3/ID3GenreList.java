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
package org.drftpd.id3;

import org.apache.log4j.Logger;


/**
 * @author Jamal
 * @version $Id$
 */
public class ID3GenreList {
    public static final String[] genres = {
            "Blues", "Classic Rock", "Country", "Dance", "Disco", "Funk",
            "Grunge", "Hip-Hop", "Jazz", "Metal", "New Age", "Oldies", "Other",
            "Pop", "R&B", "Rap", "Reggae", "Rock", "Techno", "Industrial",
            "Alternative", "Ska", "Death Metal", "Pranks", "Soundtrack",
            "Euro-Techno", "Ambient", "Trip-Hop", "Vocal", "Jazz+Funk", "Fusion",
            "Trance", "Classical", "Instrumental", "Acid", "House", "Game",
            "Sound Clip", "Gospel", "Noise", "AlternRock", "Bass", "Soul",
            "Punk", "Space", "Meditative", "Instrumental Pop",
            "Instrumental Rock", "Ethnic", "Gothic", "Darkwave",
            "Techno-Industrial", "Electronic", "Pop-Folk", "Eurodance", "Dream",
            "Southern Rock", "Comedy", "Cult", "Gangsta", "Top 40",
            "Christian Rap", "Pop/Funk", "Jungle", "Native American", "Cabaret",
            "New Wave", "Psychedelic", "Rave", "Showtunes", "Trailer", "Lo-Fi",
            "Tribal", "Acid Punk", "Acid Jazz", "Polka", "Retro", "Musical",
            "Rock & Roll", "Hard Rock", "Folk", "Folk-Rock", "National Folk",
            "Swing", "Fast Fusion", "Bebob", "Latin", "Revival", "Celtic",
            "Bluegrass", "Avantgarde", "Gothic Rock", "Progressive Rock",
            "Psychedelic Rock", "Symphonic Rock", "Slow Rock", "Big Band",
            "Chorus", "Easy Listening", "Acoustic", "Humour", "Speech",
            "Chanson", "Opera", "Chamber Music", "Sonata", "Symphony",
            "Booty Brass", "Primus", "Porn Groove", "Satire", "Slow Jam", "Club",
            "Tango", "Samba", "Folklore", "Ballad", "Power Ballad",
            "Rhytmic Soul", "Freestyle", "Duet", "Punk Rock", "Drum Solo",
            "Acapella", "Euro-House", "Dance Hall", "unknown"
        };
	private static final Logger logger = Logger.getLogger(ID3GenreList.class);

    /** Returns the Genre for given number
     *        @return Genre as String
     */
    public static String getGenre(int number) {
        try {
        	return genres[number];
        } catch(ArrayIndexOutOfBoundsException e) {
        	logger.warn("Unknown genre number: "+number);
        	return "";
        }
    }

    /** Checks, if the given genre is a valid one
     *  @return boolean
     */
    public static boolean validateGenre(String genre) {
        if (getGenreIndex(genre) != -1) {
            return true;
        }

        return false;
    }

    /** Returns the array index of this genre, or -1 if it is an invalid one.
     *  @return int array index
     */
    public static int getGenreIndex(String genre) {
        int index = -1;

        for (int i = 0; i < genres.length; i++) {
            if (genre.equals(genres[i])) {
                index = i;
            }
        }

        return index;
    }
}
