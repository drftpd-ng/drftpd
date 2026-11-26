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
package org.drftpd.zipscript.master.sfv.banned;

import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BannedFilenamesTest {

    private static BannedFilenames of(String... kv) {
        Properties p = new Properties();
        for (int i = 0; i < kv.length; i += 2) {
            p.setProperty(kv[i], kv[i + 1]);
        }
        return BannedFilenames.fromProperties(p);
    }

    @Test
    public void emptyConfigIsOff() {
        BannedFilenames b = of();
        assertTrue(b.isEmpty());
        assertFalse(b.isBanned("/MP3", "anything.url"));
        assertTrue(of("banned.filenames", " , ,").isEmpty());
    }

    @Test
    public void globsAreCaseInsensitive() {
        BannedFilenames b = of("banned.filenames", "*.url, *.LNK ,readme?.txt");
        assertTrue(b.isBanned("/X", "Visit Us.URL"));
        assertTrue(b.isBanned("/X", "a.lnk"));
        assertTrue(b.isBanned("/X", "README1.txt"));
        assertFalse(b.isBanned("/X", "readme12.txt"));
        assertFalse(b.isBanned("/X", "file.urls"));
        assertFalse(b.isBanned("/X", "file.url.nfo"));
    }

    @Test
    public void wholeNameMustMatch() {
        BannedFilenames b = of("banned.filenames", "ad");
        assertTrue(b.isBanned("/X", "AD"));
        assertFalse(b.isBanned("/X", "ad.nfo"));
        assertFalse(b.isBanned("/X", "bad"));
    }

    @Test
    public void dotfilesAndStarMatchLeadingDot() {
        BannedFilenames b = of("banned.filenames", ".*,thumbs.db");
        assertTrue(b.isBanned("/X", ".DS_Store"));
        assertTrue(b.isBanned("/X", ".hidden"));
        assertTrue(b.isBanned("/X", "Thumbs.db"));
        assertFalse(b.isBanned("/X", "a.hidden"));
        assertTrue(of("banned.filenames", "*.url").isBanned("/X", ".url"));
    }

    @Test
    public void regexMetaCharsAreLiteral() {
        BannedFilenames b = of("banned.filenames", "a+b(1).txt,[ab]x.nfo");
        assertTrue(b.isBanned("/X", "a+b(1).txt"));
        assertFalse(b.isBanned("/X", "aab(1)xtxt"));
        assertTrue(b.isBanned("/X", "bx.nfo"));
        assertFalse(b.isBanned("/X", "cx.nfo"));
    }

    @Test
    public void unusualInput() {
        BannedFilenames b = of("banned.filenames", "*.url,[unclosed");
        assertFalse(b.isBanned("/X", ""));
        assertFalse(b.isBanned("/X", null));
        assertTrue(b.isBanned("/X", "café É.URL"));
    }

    @Test
    public void overrideReplacesGlobalListForMatchingDirs() {
        BannedFilenames b = of("banned.filenames", "*.url,*.nfo",
                "banned.filenames.override.1.path", "/PRE/*",
                "banned.filenames.override.1.patterns", "*.exe",
                "banned.filenames.override.2.path", "/REQUESTS/*",
                "banned.filenames.override.2.patterns", "");
        assertTrue(b.isBanned("/MP3/Rel", "a.NFO"));
        assertFalse(b.isBanned("/PRE/Grp/Rel", "a.nfo"));
        assertTrue(b.isBanned("/pre/Grp/Rel", "a.exe"));
        assertFalse(b.isBanned("/REQUESTS/x", "a.url"));
    }

    @Test
    public void overrideNumberingStopsAtGap() {
        BannedFilenames b = of("banned.filenames", "*.url",
                "banned.filenames.override.2.path", "/PRE/*",
                "banned.filenames.override.2.patterns", "");
        assertTrue(b.isBanned("/PRE/x", "a.url"));
    }
}
