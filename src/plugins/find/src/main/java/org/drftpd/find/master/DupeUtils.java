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
package org.drftpd.find.master;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Pure helper logic for the find <tt>-dupe</tt> option.
 * <p>
 * A dupe is a directory name (compared case-insensitively) that exists more than once.
 * For every such name one copy is kept (oldest by default) and every other copy is
 * selected as a candidate for the find actions. A name that exists only once is never
 * returned, so the only copy of a release can never be selected.
 */
public final class DupeUtils {

    /**
     * Default pattern of directory names that are never treated as dupes because they are
     * common sub directories of releases (CD1, Sample, Subs, ...), matched case-insensitively.
     */
    public static final String DEFAULT_EXCLUDE = "^((cd|dvd|disc|disk)[0-9]+|sample|samples|subs|proof|covers?|extras?)$";

    public enum KeepPolicy {
        /** Keep the copy with the lowest lastModified (first uploaded) */
        OLDEST,
        /** Keep the copy with the highest lastModified (last uploaded) */
        NEWEST
    }

    /**
     * One copy of a directory.
     *
     * @param path         full path of the directory (no trailing separator)
     * @param lastModified last modified time in milliseconds
     */
    public record Copy(String path, long lastModified) {
        public String name() {
            String p = path.endsWith("/") && path.length() > 1 ? path.substring(0, path.length() - 1) : path;
            return p.substring(p.lastIndexOf('/') + 1);
        }
    }

    /**
     * All copies of one dupe name.
     *
     * @param name   the (lower cased) dupe name
     * @param copies all copies, oldest first (ties broken by path)
     * @param kept   the copy that must be kept
     */
    public record DupeGroup(String name, List<Copy> copies, Copy kept) {
        /**
         * @return all copies except the kept one, oldest first
         */
        public List<Copy> toRemove() {
            List<Copy> remove = new ArrayList<>(copies);
            remove.remove(kept);
            return remove;
        }
    }

    private static final Comparator<Copy> OLDEST_FIRST =
            Comparator.comparingLong(Copy::lastModified).thenComparing(Copy::path);

    private DupeUtils() {
    }

    /**
     * Groups the given directories by name and returns the groups that hold more than one copy.
     *
     * @param dirs    candidate directories
     * @param policy  which copy to keep
     * @param exclude names matching this pattern are ignored (may be null)
     * @return the dupe groups sorted by name, each with its copies sorted oldest first
     */
    public static List<DupeGroup> findDupes(Collection<Copy> dirs, KeepPolicy policy, Pattern exclude) {
        Map<String, List<Copy>> byName = new TreeMap<>();
        Set<String> seenPaths = new HashSet<>();
        for (Copy dir : dirs) {
            String name = dir.name();
            if (name.isEmpty() || !seenPaths.add(dir.path())) {
                continue;
            }
            if (exclude != null && exclude.matcher(name).find()) {
                continue;
            }
            byName.computeIfAbsent(name.toLowerCase(Locale.ROOT), k -> new ArrayList<>()).add(dir);
        }
        List<DupeGroup> groups = new ArrayList<>();
        for (Map.Entry<String, List<Copy>> entry : byName.entrySet()) {
            List<Copy> copies = entry.getValue();
            if (copies.size() < 2) {
                continue;
            }
            copies.sort(OLDEST_FIRST);
            Copy kept = policy == KeepPolicy.NEWEST ? copies.get(copies.size() - 1) : copies.get(0);
            groups.add(new DupeGroup(entry.getKey(), Collections.unmodifiableList(copies), kept));
        }
        return groups;
    }

    /**
     * @param exclude the configured exclude pattern, null or blank to use {@link #DEFAULT_EXCLUDE},
     *                "none" to disable exclusion
     * @return the compiled case-insensitive pattern or null when exclusion is disabled
     */
    public static Pattern compileExclude(String exclude) {
        if (exclude == null || exclude.isBlank()) {
            exclude = DEFAULT_EXCLUDE;
        }
        if (exclude.trim().equalsIgnoreCase("none")) {
            return null;
        }
        return Pattern.compile(exclude.trim(), Pattern.CASE_INSENSITIVE);
    }
}
