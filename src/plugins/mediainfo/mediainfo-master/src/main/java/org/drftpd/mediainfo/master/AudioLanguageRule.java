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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Set;

/**
 * Decides whether a release must be nuked because none of the audio streams of
 * its sample has an allowed language. Pure logic, no VFS or config access.
 */
public final class AudioLanguageRule {

    private AudioLanguageRule() {
    }

    /**
     * Splits a comma/semicolon separated language list into a lowercase set.
     */
    public static Set<String> parseAllowed(String value) {
        Set<String> allowed = new LinkedHashSet<>();
        if (value != null) {
            for (String lang : value.split("[,;]")) {
                lang = lang.trim().toLowerCase(Locale.ROOT);
                if (!lang.isEmpty()) {
                    allowed.add(lang);
                }
            }
        }
        return allowed;
    }

    /**
     * Deprecated ISO 639-1 codes that older JDKs still report from Locale.getISOLanguages()
     * and that may be used in configs. They are normalised to their current codes.
     */
    private static final Map<String, String> LEGACY_CODES = Map.of("iw", "he", "in", "id", "ji", "yi");

    private static final Map<String, String> LOOKUP = buildLookup();

    private static Map<String, String> buildLookup() {
        Map<String, String> map = new HashMap<>();
        String[][] bibliographic = {
                {"ger", "de"}, {"fre", "fr"}, {"dut", "nl"}, {"chi", "zh"}, {"cze", "cs"},
                {"gre", "el"}, {"per", "fa"}, {"rum", "ro"}, {"slo", "sk"}, {"alb", "sq"},
                {"arm", "hy"}, {"baq", "eu"}, {"bur", "my"}, {"geo", "ka"}, {"ice", "is"},
                {"mac", "mk"}, {"mao", "mi"}, {"may", "ms"}, {"tib", "bo"}, {"wel", "cy"}
        };
        for (String[] entry : bibliographic) {
            map.put(entry[0], entry[1]);
        }
        for (String iso : Locale.getISOLanguages()) {
            Locale locale = Locale.forLanguageTag(iso);
            String code = LEGACY_CODES.getOrDefault(iso, iso);
            map.put(iso, code);
            try {
                map.putIfAbsent(locale.getISO3Language().toLowerCase(Locale.ROOT), code);
            } catch (MissingResourceException ignored) {
                // no ISO 639-2 code for this language
            }
            String name = locale.getDisplayLanguage(Locale.ENGLISH).toLowerCase(Locale.ROOT);
            if (!name.isEmpty() && !name.equals(code)) {
                map.putIfAbsent(name, code);
            }
        }
        for (Map.Entry<String, String> legacy : LEGACY_CODES.entrySet()) {
            map.put(legacy.getKey(), legacy.getValue());
        }
        return map;
    }

    /**
     * Maps an English language name, ISO 639-1 or ISO 639-2 (T or B) code to its ISO 639-1
     * code. Qualified forms such as "English (US)" or "en-US" are reduced to their primary
     * part first. Returns null if the value maps to no known language.
     */
    public static String canonical(String value) {
        if (value == null) {
            return null;
        }
        String lang = value.trim().toLowerCase(Locale.ROOT);
        String code = LOOKUP.get(lang);
        if (code == null) {
            int end = lang.length();
            for (int i = 0; i < lang.length(); i++) {
                char c = lang.charAt(i);
                if (c == '(' || c == '-' || c == '_') {
                    end = i;
                    break;
                }
            }
            code = LOOKUP.get(lang.substring(0, end).trim());
        }
        return code;
    }

    /**
     * Returns the configured allowed values that map to no known language; they can only
     * match mediainfo's text exactly (case-insensitively).
     */
    public static List<String> unmappable(Set<String> allowed) {
        List<String> result = new ArrayList<>();
        if (allowed != null) {
            for (String value : allowed) {
                if (canonical(value) == null) {
                    result.add(value);
                }
            }
        }
        return result;
    }

    /**
     * True if the language reported by mediainfo (e.g. "German", "English (US)", "de")
     * is one of the allowed languages. Both sides are normalised to ISO 639-1 so names and
     * ISO 639-1/639-2 codes are interchangeable; qualified forms only count by their primary
     * part, never by arbitrary tokens. Values mapping to no language fall back to an exact
     * case-insensitive comparison.
     */
    public static boolean isAllowed(String language, Set<String> allowed) {
        String lang = language.trim().toLowerCase(Locale.ROOT);
        if (allowed.contains(lang)) {
            return true;
        }
        String code = canonical(lang);
        if (code == null) {
            return false;
        }
        for (String value : allowed) {
            if (code.equals(canonical(value))) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param languages  one entry per audio stream, null or blank when mediainfo reported no language
     * @param allowed    lowercase allowed languages; empty means the rule is disabled
     * @param nukeUnknown whether a release whose languages cannot be determined is nuked
     * @return true if the release should be nuked
     */
    public static boolean shouldNuke(List<String> languages, Set<String> allowed, boolean nukeUnknown) {
        if (allowed == null || allowed.isEmpty()) {
            return false;
        }
        boolean unknown = languages == null || languages.isEmpty();
        if (languages != null) {
            for (String language : languages) {
                if (language == null || language.trim().isEmpty()) {
                    unknown = true;
                } else if (isAllowed(language, allowed)) {
                    return false;
                }
            }
        }
        return !unknown || nukeUnknown;
    }
}
