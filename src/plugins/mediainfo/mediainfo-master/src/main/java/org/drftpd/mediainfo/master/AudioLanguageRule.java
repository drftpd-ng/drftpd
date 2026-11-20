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

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
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
     * True if the language reported by mediainfo (e.g. "German", "English (US)", "de")
     * matches one of the allowed entries, case-insensitively. Allowed entries can be
     * language names or ISO codes, whichever mediainfo reports.
     */
    public static boolean isAllowed(String language, Set<String> allowed) {
        String lang = language.trim().toLowerCase(Locale.ROOT);
        if (allowed.contains(lang)) {
            return true;
        }
        for (String token : lang.split("[^a-z]+")) {
            if (!token.isEmpty() && allowed.contains(token)) {
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
