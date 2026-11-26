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
package org.drftpd.zipscript.master.sfv;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for #591: optional strictness settings.
 */
public class ZipscriptStrictnessTest {

    private static Properties cfg(String... kv) {
        Properties p = new Properties();
        for (int i = 0; i < kv.length; i += 2) {
            p.setProperty(kv[i], kv[i + 1]);
        }
        return p;
    }

    @Test
    void secondNfoAllowedByDefault() {
        assertNull(ZipscriptStrictness.checkNfoUpload(cfg(), "b.nfo", List.of("a.nfo", "a.sfv")));
    }

    @Test
    void secondNfoDeniedWhenEnabled() {
        Properties c = cfg("nfo.deny.second", "true");
        assertNotNull(ZipscriptStrictness.checkNfoUpload(c, "b.nfo", List.of("a.nfo", "a.sfv")));
        assertNotNull(ZipscriptStrictness.checkNfoUpload(c, "b.NFO", List.of("A.Nfo")));
    }

    @Test
    void firstNfoAndOtherFilesAllowedWhenEnabled() {
        Properties c = cfg("nfo.deny.second", "true");
        assertNull(ZipscriptStrictness.checkNfoUpload(c, "a.nfo", List.of("a.sfv", "a.r00")));
        assertNull(ZipscriptStrictness.checkNfoUpload(c, "a.r01", List.of("a.nfo")));
        assertNull(ZipscriptStrictness.checkNfoUpload(c, "a.nfo", List.of()));
    }

    @Test
    void nfoInZipDirAllowedByDefault() {
        assertNull(ZipscriptStrictness.checkNfoUpload(cfg(), "a.nfo", List.of("a.zip")));
    }

    @Test
    void nfoInZipDirDeniedWhenEnabled() {
        Properties c = cfg("nfo.deny.zipdir", "true");
        assertNotNull(ZipscriptStrictness.checkNfoUpload(c, "a.nfo", List.of("a.ZIP", "b.zip")));
    }

    @Test
    void zipNfoOptionOnlyAffectsNfoInZipDirs() {
        Properties c = cfg("nfo.deny.zipdir", "true");
        assertNull(ZipscriptStrictness.checkNfoUpload(c, "a.nfo", List.of("a.sfv", "a.r00")));
        assertNull(ZipscriptStrictness.checkNfoUpload(c, "a.nfo", List.of()));
        assertNull(ZipscriptStrictness.checkNfoUpload(c, "c.zip", List.of("a.zip")));
        // second nfo option is independent
        assertNull(ZipscriptStrictness.checkNfoUpload(c, "b.nfo", List.of("a.nfo")));
    }

    @Test
    void noCheckDirsEmptyByDefault() {
        assertFalse(ZipscriptStrictness.isNoCheckDir(cfg(), "/REQUESTS/Rel"));
        assertFalse(ZipscriptStrictness.isNoCheckDir(cfg("nocheck.dirs", "  "), "/REQUESTS/Rel"));
    }

    @Test
    void noCheckDirsMatchGlobs() {
        Properties c = cfg("nocheck.dirs", "/REQUESTS/* /PRE/*/Sample/");
        assertTrue(ZipscriptStrictness.isNoCheckDir(c, "/REQUESTS/Rel"));
        assertTrue(ZipscriptStrictness.isNoCheckDir(c, "/REQUESTS/Rel/"));
        assertTrue(ZipscriptStrictness.isNoCheckDir(c, "/PRE/Group/Sample"));
        assertFalse(ZipscriptStrictness.isNoCheckDir(c, "/MP3/Rel"));
        assertFalse(ZipscriptStrictness.isNoCheckDir(c, "/PRE/Group/Rel"));
    }

    @Test
    void noCheckDirsAreCaseInsensitive() {
        Properties c = cfg("nocheck.dirs", "/Requests/*");
        assertTrue(ZipscriptStrictness.isNoCheckDir(c, "/REQUESTS/Rel"));
        assertTrue(ZipscriptStrictness.isNoCheckDir(c, "/requests/rel/"));
    }

    @Test
    void noCheckDirsIgnoreBadGlob() {
        Properties c = cfg("nocheck.dirs", "[ /MP3/*");
        assertTrue(ZipscriptStrictness.isNoCheckDir(c, "/MP3/Rel"));
    }
}
