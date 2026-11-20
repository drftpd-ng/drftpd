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
package org.drftpd.mediainfo.master;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AudioLanguageRuleTest {

    private static final Set<String> ALLOWED = AudioLanguageRule.parseAllowed("German, english");

    @Test
    public void disabledWhenNoAllowedLanguages() {
        assertFalse(AudioLanguageRule.shouldNuke(List.of("French"), Collections.emptySet(), true));
        assertFalse(AudioLanguageRule.shouldNuke(List.of("French"), null, true));
    }

    @Test
    public void allowedLanguageIsKept() {
        assertFalse(AudioLanguageRule.shouldNuke(List.of("German"), ALLOWED, false));
    }

    @Test
    public void matchIsCaseInsensitive() {
        assertFalse(AudioLanguageRule.shouldNuke(List.of("ENGLISH"), ALLOWED, false));
        assertFalse(AudioLanguageRule.shouldNuke(List.of("german"), AudioLanguageRule.parseAllowed("GERMAN"), false));
    }

    @Test
    public void disallowedLanguageIsNuked() {
        assertTrue(AudioLanguageRule.shouldNuke(List.of("French"), ALLOWED, false));
    }

    @Test
    public void oneAllowedStreamIsEnough() {
        assertFalse(AudioLanguageRule.shouldNuke(List.of("French", "German"), ALLOWED, true));
    }

    @Test
    public void allStreamsDisallowedIsNuked() {
        assertTrue(AudioLanguageRule.shouldNuke(List.of("French", "Spanish"), ALLOWED, false));
    }

    @Test
    public void unknownIsAllowedByDefault() {
        assertFalse(AudioLanguageRule.shouldNuke(Arrays.asList((String) null), ALLOWED, false));
        assertFalse(AudioLanguageRule.shouldNuke(List.of(" "), ALLOWED, false));
        assertFalse(AudioLanguageRule.shouldNuke(Collections.emptyList(), ALLOWED, false));
    }

    @Test
    public void unknownIsNukedWhenConfigured() {
        assertTrue(AudioLanguageRule.shouldNuke(Arrays.asList((String) null), ALLOWED, true));
        assertTrue(AudioLanguageRule.shouldNuke(Collections.emptyList(), ALLOWED, true));
    }

    @Test
    public void unknownStreamPlusDisallowedFollowsUnknownSetting() {
        assertFalse(AudioLanguageRule.shouldNuke(Arrays.asList("French", null), ALLOWED, false));
        assertTrue(AudioLanguageRule.shouldNuke(Arrays.asList("French", null), ALLOWED, true));
    }

    @Test
    public void isoCodesAndQualifiedNames() {
        Set<String> iso = AudioLanguageRule.parseAllowed("de;en");
        assertFalse(AudioLanguageRule.shouldNuke(List.of("de"), iso, false));
        assertFalse(AudioLanguageRule.shouldNuke(List.of("English (US)"), AudioLanguageRule.parseAllowed("english"), false));
        assertTrue(AudioLanguageRule.shouldNuke(List.of("fr"), iso, false));
    }

    @Test
    public void parseAllowedTrimsAndSkipsEmpty() {
        assertTrue(AudioLanguageRule.parseAllowed(" , ;").isEmpty());
        assertTrue(AudioLanguageRule.parseAllowed(null).isEmpty());
    }

    private static boolean allowed(String config, String mediainfo) {
        return AudioLanguageRule.isAllowed(mediainfo, AudioLanguageRule.parseAllowed(config));
    }

    @Test
    public void englishCodesAndNameAreEquivalent() {
        for (String config : List.of("en", "eng", "English")) {
            for (String seen : List.of("en", "eng", "English", "ENGLISH")) {
                assertTrue(allowed(config, seen), config + " vs " + seen);
            }
            assertFalse(allowed(config, "German"));
        }
    }

    @Test
    public void germanCodesAndNameAreEquivalent() {
        for (String config : List.of("de", "ger", "deu", "German")) {
            for (String seen : List.of("de", "ger", "deu", "German")) {
                assertTrue(allowed(config, seen), config + " vs " + seen);
            }
            assertFalse(allowed(config, "English"));
        }
    }

    @Test
    public void bibliographicCodes() {
        assertTrue(allowed("fre", "French"));
        assertTrue(allowed("dut", "nl"));
        assertTrue(allowed("chi", "Chinese"));
        assertTrue(allowed("wel", "cym"));
    }

    @Test
    public void qualifiedFormsUsePrimaryPart() {
        assertTrue(allowed("en", "English (US)"));
        assertTrue(allowed("eng", "en-US"));
        assertTrue(allowed("English", "en-GB"));
    }

    @Test
    public void qualifierTokenDoesNotMatch() {
        assertFalse(allowed("us", "English (US)"));
        assertFalse(allowed("us", "en-US"));
        assertFalse(allowed("gb", "English (GB)"));
    }

    @Test
    public void unknownValuesFallBackToExactComparison() {
        assertTrue(allowed("Klingonese", "klingonese"));
        assertFalse(allowed("Klingonese", "Klingon"));
        assertFalse(allowed("Klingonese", "English"));
        assertFalse(allowed("de", "Klingonese"));
    }

    @Test
    public void unmappableConfigValuesAreReported() {
        Set<String> cfg = AudioLanguageRule.parseAllowed("en, German, us, Klingonese, ger");
        assertEquals(List.of("us", "klingonese"), AudioLanguageRule.unmappable(cfg));
        assertTrue(AudioLanguageRule.unmappable(AudioLanguageRule.parseAllowed("en,eng,de,deu,English")).isEmpty());
        assertTrue(AudioLanguageRule.unmappable(null).isEmpty());
    }

    @Test
    public void shouldNukeUsesLanguageMapping() {
        assertFalse(AudioLanguageRule.shouldNuke(List.of("English"), AudioLanguageRule.parseAllowed("eng"), false));
        assertTrue(AudioLanguageRule.shouldNuke(List.of("English (US)"), AudioLanguageRule.parseAllowed("us"), false));
    }
}
