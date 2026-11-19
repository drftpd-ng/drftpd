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
package org.drftpd.imdb.master;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class IMDBParserTest {
    @Test
    void titleUrlUsesConfiguredBase() {
        assertEquals("https://mirror.example/title/tt0133093",
                IMDBParser.toTitleUrl("https://mirror.example", "https://imdb.com/title/tt0133093"));
    }

    @Test
    void titleUrlAcceptsRelativePath() {
        assertEquals("https://imdb.com/title/tt0133093",
                IMDBParser.toTitleUrl("https://imdb.com", "/title/tt0133093"));
    }

    @Test
    void titleUrlPassesThroughOtherInput() {
        assertNull(IMDBParser.toTitleUrl("https://imdb.com", null));
        assertEquals("N|A", IMDBParser.toTitleUrl("https://imdb.com", "N|A"));
    }

    @Test
    void titleUrlHasSingleSlashWithTrailingSlashesInBase() {
        assertEquals("https://imdb.com/title/tt0133093",
                IMDBParser.toTitleUrl("https://imdb.com/", "/title/tt0133093"));
        assertEquals("https://imdb.com/title/tt0133093",
                IMDBParser.toTitleUrl("https://imdb.com///", "https://imdb.com/title/tt0133093"));
    }

    @Test
    void titleUrlHasSingleSlashWithoutTrailingSlashInBase() {
        assertEquals("https://imdb.com/title/tt0133093",
                IMDBParser.toTitleUrl("https://imdb.com", "title/tt0133093"));
    }
}
