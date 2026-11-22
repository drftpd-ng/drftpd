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
package org.drftpd.links.master.types.nosample;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

public class NoSampleTest {

    private static final Pattern DEFAULT = Pattern.compile(NoSample.DEFAULT_SAMPLE_NAME);

    @Test
    void defaultMatchesSampleCaseInsensitively() {
        assertTrue(NoSample.isSampleDirName("Sample", DEFAULT));
        assertTrue(NoSample.isSampleDirName("sample", DEFAULT));
        assertTrue(NoSample.isSampleDirName("SAMPLE", DEFAULT));
        assertFalse(NoSample.isSampleDirName("Samples", DEFAULT));
        assertFalse(NoSample.isSampleDirName("Proof", DEFAULT));
    }

    @Test
    void customPatternIsHonoured() {
        Pattern p = Pattern.compile("(?i)(sample|samples|proof)");
        assertTrue(NoSample.isSampleDirName("Samples", p));
        assertTrue(NoSample.isSampleDirName("PROOF", p));
        assertFalse(NoSample.isSampleDirName("Subs", p));
    }

    @Test
    void hasSampleDir() {
        assertTrue(NoSample.hasSampleDir(List.of("CD1", "Sample"), DEFAULT));
        assertFalse(NoSample.hasSampleDir(List.of("Subs", "CD1"), DEFAULT));
        assertFalse(NoSample.hasSampleDir(List.of(), DEFAULT));
    }

    @Test
    void wantsLinkWhenFilesAndNoSample() {
        assertTrue(NoSample.wantsLink("Rel-GRP", List.of(), 3, DEFAULT));
        assertTrue(NoSample.wantsLink("Rel-GRP", List.of("Subs"), 1, DEFAULT));
    }

    @Test
    void noLinkWhenSampleExistsOrNoFilesOrIsSampleDir() {
        assertFalse(NoSample.wantsLink("Rel-GRP", List.of("sample"), 3, DEFAULT));
        assertFalse(NoSample.wantsLink("Rel-GRP", List.of(), 0, DEFAULT));
        assertFalse(NoSample.wantsLink("Sample", List.of(), 2, DEFAULT));
    }
}
