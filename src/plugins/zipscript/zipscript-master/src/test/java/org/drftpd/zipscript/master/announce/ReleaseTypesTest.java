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
package org.drftpd.zipscript.master.announce;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.ListResourceBundle;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

import static org.junit.jupiter.api.Assertions.*;

public class ReleaseTypesTest {

    private static ResourceBundle bundle() {
        return new ListResourceBundle() {
            @Override
            protected Object[][] getContents() {
                return new Object[][]{
                        {"sfv.store.complete", "generic"},
                        {"sfv.store.complete.mp3", "mp3"},
                        {"sfv.store.complete.mp3.vbr", "mp3 vbr"},
                        {"sfv.store.complete.rar", "rar"},
                        {"sfv.store.race", "race generic"}
                };
            }
        };
    }

    @AfterEach
    void clear() {
        TypeAwareBundle.clearTypes();
    }

    @Test
    void detectsTypes() {
        assertEquals(List.of("mp3.vbr", "mp3", "audio"), ReleaseTypes.detect("01.MP3", () -> "VBR"));
        assertEquals(List.of("mp3.cbr", "mp3", "audio"), ReleaseTypes.detect("01.mp3", () -> "cbr"));
        assertEquals(List.of("mp3", "audio"), ReleaseTypes.detect("01.mp3", () -> null));
        assertEquals(List.of("mp3", "audio"), ReleaseTypes.detect("01.mp3", () -> {
            throw new IllegalStateException();
        }));
        assertEquals(List.of("flac", "audio"), ReleaseTypes.detect("a.flac", null));
        assertEquals(List.of("rar"), ReleaseTypes.detect("x.rar", null));
        assertEquals(List.of("rar"), ReleaseTypes.detect("x.r07", null));
        assertEquals(List.of("rar"), ReleaseTypes.detect("x.r123", null));
        assertEquals(List.of("video"), ReleaseTypes.detect("x.mkv", null));
        assertEquals(List.of("zip"), ReleaseTypes.detect("x.zip", null));
        assertTrue(ReleaseTypes.detect("x.nfo", null).isEmpty());
        assertTrue(ReleaseTypes.detect("noext", null).isEmpty());
        assertTrue(ReleaseTypes.detect("trailing.", null).isEmpty());
        assertTrue(ReleaseTypes.detect(null, null).isEmpty());
    }

    @Test
    void encodingOnlyQueriedForMp3() {
        ReleaseTypes.detect("x.rar", () -> {
            throw new AssertionError("must not be called");
        });
    }

    @Test
    void noTypesIsIdenticalToPlainBundle() {
        ResourceBundle b = TypeAwareBundle.wrap(bundle());
        assertEquals("generic", b.getString("sfv.store.complete"));
        assertEquals("race generic", b.getString("sfv.store.race"));
        assertThrows(MissingResourceException.class, () -> b.getString("nope"));
    }

    @Test
    void mostSpecificKeyWinsThenFallsBack() {
        ResourceBundle b = TypeAwareBundle.wrap(bundle());
        TypeAwareBundle.setTypes(ReleaseTypes.detect("a.mp3", () -> "VBR"));
        assertEquals("mp3 vbr", b.getString("sfv.store.complete"));
        TypeAwareBundle.setTypes(ReleaseTypes.detect("a.mp3", () -> "CBR"));
        assertEquals("mp3", b.getString("sfv.store.complete"));
        TypeAwareBundle.setTypes(ReleaseTypes.detect("a.rar", null));
        assertEquals("rar", b.getString("sfv.store.complete"));
        TypeAwareBundle.setTypes(ReleaseTypes.detect("a.mkv", null));
        assertEquals("generic", b.getString("sfv.store.complete"));
        // key with no typed variants falls back for every type
        TypeAwareBundle.setTypes(ReleaseTypes.detect("a.mp3", () -> "VBR"));
        assertEquals("race generic", b.getString("sfv.store.race"));
        assertThrows(MissingResourceException.class, () -> b.getString("nope"));
    }

    @Test
    void wrapIsIdempotentAndSuffixScan() {
        ResourceBundle b = TypeAwareBundle.wrap(bundle());
        assertSame(b, TypeAwareBundle.wrap(b));
        assertTrue(((TypeAwareBundle) b).hasKeySuffix(".mp3.cbr", ".mp3.vbr"));
        assertFalse(((TypeAwareBundle) b).hasKeySuffix(".mp3.cbr"));
    }

    @Test
    void hasThemeKeyReportsMissingKeys() {
        ResourceBundle b = bundle();
        assertTrue(TypeAwareBundle.hasThemeKey(b, "sfv.store.race"));
        assertFalse(TypeAwareBundle.hasThemeKey(b, "sfv.store.update"));
        assertFalse(TypeAwareBundle.hasThemeKey(null, "sfv.store.update"));
    }
}
