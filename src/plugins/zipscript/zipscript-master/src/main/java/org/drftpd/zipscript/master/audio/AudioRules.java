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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/**
 * Per section audio rules (banned/allowed genres, allowed years, allowed CBR bitrates).
 * Pure rule evaluation, no VFS access, so it can be unit tested.
 * <p>
 * Keys in zipscript.conf, where SECTION is the section name:
 * <pre>
 * SECTION.audio.enabled=true|false          (default false)
 * SECTION.audio.action=warn|delete          (default warn)
 * SECTION.audio.banned.genres=a, b
 * SECTION.audio.allowed.genres=a, b
 * SECTION.audio.allowed.years=1990-1999, 2005
 * SECTION.audio.allowed.bitrates=128, 192, 320   (kbit/s, CBR files only)
 * </pre>
 */
public class AudioRules {

    private final boolean _delete;
    private final List<String> _bannedGenres;
    private final List<String> _allowedGenres;
    private final List<int[]> _allowedYears;
    private final List<Integer> _allowedBitrates;

    AudioRules(boolean delete, List<String> bannedGenres, List<String> allowedGenres,
               List<int[]> allowedYears, List<Integer> allowedBitrates) {
        _delete = delete;
        _bannedGenres = bannedGenres;
        _allowedGenres = allowedGenres;
        _allowedYears = allowedYears;
        _allowedBitrates = allowedBitrates;
    }

    /**
     * @return the rules of the given section, or null if the section has no enabled rules.
     */
    public static AudioRules load(Properties cfg, String section) {
        if (section == null || section.isEmpty()) {
            return null;
        }
        String prefix = section + ".audio.";
        if (cfg.getProperty(prefix + "enabled") == null) {
            // also accept a lowercase section name
            prefix = section.toLowerCase(Locale.ROOT) + ".audio.";
        }
        if (!cfg.getProperty(prefix + "enabled", "false").trim().equalsIgnoreCase("true")) {
            return null;
        }
        boolean delete = cfg.getProperty(prefix + "action", "warn").trim().equalsIgnoreCase("delete");
        List<int[]> years = new ArrayList<>();
        for (String s : split(cfg.getProperty(prefix + "allowed.years"))) {
            int[] range = parseRange(s);
            if (range != null) {
                years.add(range);
            }
        }
        List<Integer> bitrates = new ArrayList<>();
        for (String s : split(cfg.getProperty(prefix + "allowed.bitrates"))) {
            try {
                bitrates.add(Integer.parseInt(s));
            } catch (NumberFormatException e) {
                // ignore invalid entry
            }
        }
        List<String> banned = new ArrayList<>();
        for (String s : split(cfg.getProperty(prefix + "banned.genres"))) {
            banned.add(s.toLowerCase(Locale.ROOT));
        }
        List<String> allowed = new ArrayList<>();
        for (String s : split(cfg.getProperty(prefix + "allowed.genres"))) {
            allowed.add(s.toLowerCase(Locale.ROOT));
        }
        return new AudioRules(delete, banned, allowed, years, bitrates);
    }

    private static List<String> split(String value) {
        List<String> out = new ArrayList<>();
        if (value != null) {
            for (String s : value.split(",")) {
                s = s.trim();
                if (!s.isEmpty()) {
                    out.add(s);
                }
            }
        }
        return out;
    }

    private static int[] parseRange(String s) {
        try {
            int dash = s.indexOf('-');
            if (dash < 0) {
                int y = Integer.parseInt(s);
                return new int[]{y, y};
            }
            int from = Integer.parseInt(s.substring(0, dash).trim());
            int to = Integer.parseInt(s.substring(dash + 1).trim());
            return new int[]{Math.min(from, to), Math.max(from, to)};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * @return true if a violation removes the whole release, false if it only warns
     */
    public boolean isDelete() {
        return _delete;
    }

    /**
     * Evaluates one audio file.
     *
     * @param genre       tag genre, may be null or empty if unknown
     * @param year        tag year, may be null or empty if unknown
     * @param bitrateKbps bitrate in kbit/s, or a value of 0 or less if not applicable (not checked)
     * @param vbr         true for variable bitrate (bitrates are only checked for CBR files)
     * @return the list of violations, empty if the file is accepted
     */
    public List<String> evaluate(String genre, String year, int bitrateKbps, boolean vbr) {
        List<String> reasons = new ArrayList<>();
        String g = genre == null ? "" : genre.trim();
        String gl = g.toLowerCase(Locale.ROOT);
        if (!g.isEmpty() && _bannedGenres.contains(gl)) {
            reasons.add("genre '" + g + "' is banned");
        }
        if (!_allowedGenres.isEmpty() && !_allowedGenres.contains(gl)) {
            reasons.add("genre '" + (g.isEmpty() ? "unknown" : g) + "' is not allowed");
        }
        if (!_allowedYears.isEmpty()) {
            Integer y = parseYear(year);
            boolean ok = false;
            if (y != null) {
                for (int[] r : _allowedYears) {
                    if (y >= r[0] && y <= r[1]) {
                        ok = true;
                        break;
                    }
                }
            }
            if (!ok) {
                reasons.add("year '" + (y == null ? "unknown" : y) + "' is not allowed");
            }
        }
        if (!_allowedBitrates.isEmpty() && !vbr && bitrateKbps > 0 && !_allowedBitrates.contains(bitrateKbps)) {
            reasons.add("CBR bitrate " + bitrateKbps + " kbit/s is not allowed");
        }
        return reasons;
    }

    private static Integer parseYear(String year) {
        if (year == null) {
            return null;
        }
        String y = year.trim();
        if (y.length() >= 4) {
            y = y.substring(0, 4);
        }
        try {
            return Integer.valueOf(y);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Returns the path of the release directory for a file: the directory directly below
     * the section root which contains the file (directly or in a subdir such as CD1).
     *
     * @param sectionRoot the section's current directory path
     * @param filePath    path of the uploaded file
     * @return the release dir path, or null when the file is not inside a directory below
     * the section root (nothing may then be deleted)
     */
    public static String releasePath(String sectionRoot, String filePath) {
        if (sectionRoot == null || filePath == null) {
            return null;
        }
        String root = sectionRoot.endsWith("/") ? sectionRoot : sectionRoot + "/";
        if (!filePath.startsWith(root) || filePath.contains("/../") || filePath.contains("/./")) {
            return null;
        }
        String rest = filePath.substring(root.length());
        int slash = rest.indexOf('/');
        if (slash <= 0) {
            return null;
        }
        return root + rest.substring(0, slash);
    }
}
