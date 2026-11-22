/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * DrFTPD is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with DrFTPD; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.drftpd.zipscript.master.audio;

import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class AudioRulesTest {

    private static Properties cfg(String... kv) {
        Properties p = new Properties();
        for (int i = 0; i < kv.length; i += 2) {
            p.setProperty(kv[i], kv[i + 1]);
        }
        return p;
    }

    @Test
    void disabledByDefault() {
        assertNull(AudioRules.load(cfg("MP3.audio.banned.genres", "Rap"), "MP3"));
        assertNull(AudioRules.load(cfg("MP3.audio.enabled", "false"), "MP3"));
        assertNull(AudioRules.load(cfg("MP3.audio.enabled", "true"), ""));
    }

    @Test
    void perSectionLookup() {
        Properties p = cfg("MP3.audio.enabled", "true", "MP3.audio.banned.genres", "Rap");
        assertNotNull(AudioRules.load(p, "MP3"));
        assertNull(AudioRules.load(p, "FLAC"));
        assertNotNull(AudioRules.load(cfg("mp3.audio.enabled", "true"), "MP3"));
    }

    @Test
    void bannedGenres() {
        AudioRules r = AudioRules.load(cfg("S.audio.enabled", "true",
                "S.audio.banned.genres", "Rap, Hip-Hop"), "S");
        assertFalse(r.evaluate("rap", "2000", 192, false).isEmpty());
        assertFalse(r.evaluate("Hip-Hop", "2000", 192, false).isEmpty());
        assertTrue(r.evaluate("Rock", "2000", 192, false).isEmpty());
        assertTrue(r.evaluate("", "", 0, false).isEmpty());
    }

    @Test
    void allowedGenres() {
        AudioRules r = AudioRules.load(cfg("S.audio.enabled", "true",
                "S.audio.allowed.genres", "Rock,Pop"), "S");
        assertTrue(r.evaluate("ROCK", "", 0, false).isEmpty());
        assertFalse(r.evaluate("Rap", "", 0, false).isEmpty());
        assertFalse(r.evaluate(null, "", 0, false).isEmpty());
    }

    @Test
    void allowedYears() {
        AudioRules r = AudioRules.load(cfg("S.audio.enabled", "true",
                "S.audio.allowed.years", "1990-1999, 2005, bogus"), "S");
        assertTrue(r.evaluate("", "1995", 0, false).isEmpty());
        assertTrue(r.evaluate("", "1990", 0, false).isEmpty());
        assertTrue(r.evaluate("", "1999", 0, false).isEmpty());
        assertTrue(r.evaluate("", "2005", 0, false).isEmpty());
        assertTrue(r.evaluate("", "2005-03-01", 0, false).isEmpty());
        assertFalse(r.evaluate("", "2000", 0, false).isEmpty());
        assertFalse(r.evaluate("", "", 0, false).isEmpty());
        assertFalse(r.evaluate("", "abcd", 0, false).isEmpty());
    }

    @Test
    void allowedBitratesCbrOnly() {
        AudioRules r = AudioRules.load(cfg("S.audio.enabled", "true",
                "S.audio.allowed.bitrates", "192, 320"), "S");
        assertTrue(r.evaluate("", "", 320, false).isEmpty());
        assertFalse(r.evaluate("", "", 128, false).isEmpty());
        assertTrue(r.evaluate("", "", 128, true).isEmpty(), "VBR is not checked");
        assertTrue(r.evaluate("", "", 0, false).isEmpty(), "unknown bitrate is not checked");
    }

    @Test
    void warnVersusDelete() {
        assertFalse(AudioRules.load(cfg("S.audio.enabled", "true"), "S").isDelete());
        assertFalse(AudioRules.load(cfg("S.audio.enabled", "true", "S.audio.action", "warn"), "S").isDelete());
        assertFalse(AudioRules.load(cfg("S.audio.enabled", "true", "S.audio.action", "nonsense"), "S").isDelete());
        assertTrue(AudioRules.load(cfg("S.audio.enabled", "true", "S.audio.action", "DELETE"), "S").isDelete());
    }

    @Test
    void multipleViolationsReported() {
        AudioRules r = AudioRules.load(cfg("S.audio.enabled", "true", "S.audio.banned.genres", "Rap",
                "S.audio.allowed.years", "2000-2010", "S.audio.allowed.bitrates", "320"), "S");
        assertEquals(3, r.evaluate("Rap", "1990", 128, false).size());
    }

    @Test
    void releasePathIsSafe() {
        assertEquals("/MP3/Rel-GRP", AudioRules.releasePath("/MP3", "/MP3/Rel-GRP/01.mp3"));
        assertEquals("/MP3/Rel-GRP", AudioRules.releasePath("/MP3/", "/MP3/Rel-GRP/CD1/01.mp3"));
        assertNull(AudioRules.releasePath("/MP3", "/MP3/01.mp3"), "file directly in section root");
        assertNull(AudioRules.releasePath("/MP3", "/MP3X/Rel/01.mp3"), "sibling with common prefix");
        assertNull(AudioRules.releasePath("/MP3", "/OTHER/Rel/01.mp3"));
        assertNull(AudioRules.releasePath("/MP3", "/MP3/../OTHER/Rel/01.mp3"));
        assertNull(AudioRules.releasePath("/MP3", null));
    }
}
