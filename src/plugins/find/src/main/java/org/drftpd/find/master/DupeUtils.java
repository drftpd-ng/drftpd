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

import java.text.DateFormat;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Pure helper logic for the find <tt>-dupe</tt> option, kept free of the VFS so it can be unit tested.
 * <p>
 * Only release directories are candidates: directories directly inside a section base directory or,
 * for dated sections, directly inside a dated directory of the section. A candidate that contains,
 * or is contained in, another candidate is dropped as well.
 * <p>
 * A dupe is a release name (compared case-insensitively) that exists more than once. For every such
 * name one copy is kept (the first created by default) and every other copy is selected as a
 * candidate for the find actions. A name that exists only once is never returned, so the only copy of
 * a release can never be selected.
 */
public final class DupeUtils {

    /**
     * Default pattern of directory names that are never treated as dupes because they are
     * common sub directories of releases (CD1, Sample, Subs, ...), matched case-insensitively.
     */
    public static final String DEFAULT_EXCLUDE = "^((cd|dvd|disc|disk)[0-9]+|sample|samples|subs|proof|covers?|extras?)$";

    /**
     * Default for dupe.min.age: copies modified less than this many minutes ago are not acted on.
     */
    public static final long DEFAULT_MIN_AGE_MINUTES = 60;

    public enum KeepPolicy {
        /** Keep the copy that was created first */
        OLDEST,
        /** Keep the copy that was created last */
        NEWEST
    }

    /**
     * What happens to one copy of a dupe group.
     */
    public enum Status {
        /** The copy that is kept, never acted on */
        KEEP("KEEP"),
        /** A dupe the actions run on */
        DUPE("DUPE"),
        /** A dupe that was modified recently (upload in progress?), not acted on */
        SKIP_ACTIVE("SKIP (active)"),
        /** A dupe not acted on because the kept copy was modified recently and may be incomplete */
        SKIP_KEPT_ACTIVE("SKIP (kept copy active)");

        private final String _label;

        Status(String label) {
            _label = label;
        }

        public String label() {
            return _label;
        }
    }

    /**
     * One copy of a directory.
     *
     * @param path         full path of the directory (no trailing separator)
     * @param creationTime creation time in milliseconds, used to decide which copy is the oldest
     * @param lastModified last modified time in milliseconds, used to detect recent activity
     */
    public record Copy(String path, long creationTime, long lastModified) {
        public String name() {
            String p = normalize(path);
            return p.substring(p.lastIndexOf('/') + 1);
        }
    }

    /**
     * All copies of one dupe name.
     *
     * @param name   the (lower cased) dupe name
     * @param copies all copies, first created first (ties broken by path)
     * @param kept   the copy that must be kept
     */
    public record DupeGroup(String name, List<Copy> copies, Copy kept) {
        /**
         * @return all copies except the kept one, first created first
         */
        public List<Copy> toRemove() {
            List<Copy> remove = new ArrayList<>(copies);
            remove.remove(kept);
            return remove;
        }
    }

    /**
     * The section layout of a directory.
     *
     * @param basePath    the section base directory
     * @param datedFormat the date format of the dated directories inside the base directory,
     *                    null for a section without dated directories
     */
    public record SectionInfo(String basePath, DateFormat datedFormat) {
    }

    /**
     * Resolves the section a directory belongs to.
     */
    @FunctionalInterface
    public interface SectionResolver {
        /**
         * @return the section of the given directory, null when it is not in any section
         */
        SectionInfo lookup(String path);
    }

    /**
     * A file or directory inside a directory, see {@link DupeFileSystem#children}.
     *
     * @param path         full path
     * @param lastModified last modified time in milliseconds
     * @param directory    true for a directory (links are not directories, they are not followed)
     */
    public record Inode(String path, long lastModified, boolean directory) {
    }

    /**
     * Read access to the file system needed to check the activity of dupes and while acting on them.
     */
    public interface DupeFileSystem {
        /**
         * @return the current last modified time of the directory, empty when it does not exist (anymore)
         */
        OptionalLong lastModified(String path);

        /**
         * @return the files and directories directly inside the directory, empty when it does not exist (anymore)
         */
        Collection<Inode> children(String path);
    }

    /**
     * Maximum depth below a release walked by {@link #lastActivity}, deeper directories mean "active".
     */
    public static final int ACTIVITY_MAX_DEPTH = 4;

    /**
     * Maximum number of inodes below a release walked by {@link #lastActivity}, more mean "active".
     */
    public static final int ACTIVITY_MAX_INODES = 10_000;

    /**
     * Activity of a release whose walk hit a bound: unknown, treated as active (now) so it is never acted on.
     */
    public static final long ACTIVITY_UNKNOWN = Long.MAX_VALUE;

    /**
     * Runs the find actions on one dupe copy.
     */
    @FunctionalInterface
    public interface DupeActor {
        /**
         * @return the output lines of the actions
         */
        List<String> act(Copy copy);
    }

    /**
     * Output of {@link #run}.
     *
     * @param groups number of dupe groups handled (limited by the find limit)
     * @param lines  the output lines
     */
    public record RunResult(int groups, List<String> lines) {
    }

    /**
     * Actions that may be combined with -dupe (lower case action names) and the extra permission each one
     * needs on top of its own action permission, "" when none. Every other action is refused: -dupe selects
     * whole releases in bulk, so an action is only allowed once it has been reviewed for that and has its
     * own dupe permission when it changes anything. Actions added later are refused until listed here.
     */
    private static final Map<String, String> DUPE_ACTIONS = Map.of(
            "print", "",
            "printf", "",
            "wipe", "dupewipe",
            "nuke", "dupenuke");

    /**
     * @param actionName name of a find action, e.g. "Wipe"
     * @return true when the action may be combined with -dupe
     */
    public static boolean isDupeActionAllowed(String actionName) {
        return actionName != null && DUPE_ACTIONS.containsKey(actionName.toLowerCase(Locale.ROOT));
    }

    /**
     * @param actionName name of a find action, e.g. "Wipe"
     * @return the extra permission (find.conf key) needed to combine the action with -dupe, null when none
     * (printing actions, or actions that are refused anyway, see {@link #isDupeActionAllowed})
     */
    public static String dupeActionPermission(String actionName) {
        String permission = actionName == null ? null : DUPE_ACTIONS.get(actionName.toLowerCase(Locale.ROOT));
        return permission == null || permission.isEmpty() ? null : permission;
    }

    private static final Comparator<Copy> OLDEST_FIRST =
            Comparator.comparingLong(Copy::creationTime).thenComparing(Copy::path);

    private DupeUtils() {
    }

    /**
     * @return the path without trailing separator (except for the root)
     */
    static String normalize(String path) {
        if (path != null && path.length() > 1 && path.endsWith("/")) {
            return path.substring(0, path.length() - 1);
        }
        return path;
    }

    /**
     * Keeps only the directories that are releases of their section, see {@link #isReleaseDir}.
     */
    public static List<Copy> filterReleaseDirs(Collection<Copy> dirs, SectionResolver resolver) {
        List<Copy> releases = new ArrayList<>();
        for (Copy dir : dirs) {
            SectionInfo section = resolver.lookup(normalize(dir.path()));
            if (section != null && isReleaseDir(dir.path(), section)) {
                releases.add(dir);
            }
        }
        return releases;
    }

    /**
     * A release directory is a directory directly inside the section base directory or, for a dated
     * section, directly inside a dated directory of the section (which may span several levels, e.g.
     * <tt>yyyy/MMdd</tt>). The root, a section base directory, dated directories themselves and
     * anything deeper (sub directories of releases) are never release directories, neither is
     * anything in a section whose base is the root (i.e. outside of any configured section).
     */
    public static boolean isReleaseDir(String path, SectionInfo section) {
        path = normalize(path);
        String base = section == null ? null : normalize(section.basePath());
        if (path == null || path.isEmpty() || path.equals("/") || base == null || base.isEmpty() || base.equals("/")) {
            return false;
        }
        if (!path.startsWith(base + "/")) {
            return false;
        }
        String rel = path.substring(base.length() + 1);
        String[] parts = rel.split("/", -1);
        for (String part : parts) {
            if (part.isEmpty()) {
                return false;
            }
        }
        if (section.datedFormat() == null) {
            return parts.length == 1;
        }
        int datedDepth = datedDepth(section.datedFormat());
        if (parts.length != datedDepth + 1) {
            return false;
        }
        return isDatedDir(rel.substring(0, rel.lastIndexOf('/')), section.datedFormat());
    }

    /**
     * @return the number of path parts of a dated directory in this format, e.g. 2 for <tt>yyyy/MMdd</tt>
     */
    static int datedDepth(DateFormat format) {
        DateFormat f = (DateFormat) format.clone();
        return f.format(new Date(0)).split("/", -1).length;
    }

    /**
     * Year used for dated formats without a year field: a leap year, so 0229 is a valid MMdd.
     */
    private static final String LEAP_YEAR = "2000";

    /**
     * @param relPath path relative to the section base directory
     * @return true when the path is exactly a date in the given format (strict parsing, the parsed
     * date must format back to the same text)
     */
    static boolean isDatedDir(String relPath, DateFormat format) {
        if (roundTrips(relPath, format)) {
            return true;
        }
        if (!(format instanceof SimpleDateFormat simple)) {
            return false;
        }
        String pattern = simple.toPattern();
        String letters = unquotedLetters(pattern);
        String text = relPath;
        // Without a year field the date is parsed in 1970, which has no February 29th: parse it in a
        // leap year instead, so the 0229 dir of an MMdd section is recognised
        if (letters.indexOf('y') < 0 && letters.indexOf('Y') < 0) {
            pattern = pattern + " yyyy";
            text = relPath + " " + LEAP_YEAR;
            if (roundTrips(text, withPattern(simple, pattern))) {
                return true;
            }
        }
        // The first week of a year can start in the previous calendar year, so with a calendar year
        // (yyyy-ww) the dir 2026-01 does not round trip: compare using the week year (YYYY-ww) instead
        if (letters.indexOf('w') >= 0 && letters.indexOf('Y') < 0) {
            return roundTrips(text, withPattern(simple, replaceUnquoted(pattern, 'y', 'Y')));
        }
        return false;
    }

    /**
     * @return a copy of the format (same symbols, calendar and time zone) using the given pattern
     */
    private static SimpleDateFormat withPattern(SimpleDateFormat format, String pattern) {
        SimpleDateFormat f = (SimpleDateFormat) format.clone();
        f.applyPattern(pattern);
        return f;
    }

    /**
     * @return the pattern letters of a SimpleDateFormat pattern, without the quoted literal text
     */
    private static String unquotedLetters(String pattern) {
        StringBuilder letters = new StringBuilder();
        boolean quoted = false;
        for (char c : pattern.toCharArray()) {
            if (c == '\'') {
                quoted = !quoted;
            } else if (!quoted && Character.isLetter(c)) {
                letters.append(c);
            }
        }
        return letters.toString();
    }

    /**
     * @return the pattern with every unquoted pattern letter from replaced by to
     */
    private static String replaceUnquoted(String pattern, char from, char to) {
        StringBuilder result = new StringBuilder();
        boolean quoted = false;
        for (char c : pattern.toCharArray()) {
            if (c == '\'') {
                quoted = !quoted;
            }
            result.append(!quoted && c == from ? to : c);
        }
        return result.toString();
    }

    /**
     * @return true when the text is exactly a date in the given format (strict parsing, the parsed
     * date must format back to the same text)
     */
    private static boolean roundTrips(String relPath, DateFormat format) {
        DateFormat f = (DateFormat) format.clone();
        f.setLenient(false);
        ParsePosition pos = new ParsePosition(0);
        Date date = f.parse(relPath, pos);
        return date != null && pos.getErrorIndex() < 0 && pos.getIndex() == relPath.length()
                && f.format(date).equals(relPath);
    }

    /**
     * Drops every directory that contains, or is contained in, another directory of the collection,
     * so acting on one copy can never remove (part of) another one.
     */
    public static List<Copy> removeNested(Collection<Copy> dirs) {
        Set<String> paths = new HashSet<>();
        for (Copy dir : dirs) {
            paths.add(normalize(dir.path()));
        }
        Set<String> nested = new HashSet<>();
        for (String path : paths) {
            int i = path.lastIndexOf('/');
            while (i > 0) {
                String ancestor = path.substring(0, i);
                if (paths.contains(ancestor)) {
                    nested.add(path);
                    nested.add(ancestor);
                }
                i = ancestor.lastIndexOf('/');
            }
        }
        List<Copy> result = new ArrayList<>();
        for (Copy dir : dirs) {
            if (!nested.contains(normalize(dir.path()))) {
                result.add(dir);
            }
        }
        return result;
    }

    /**
     * Groups the given directories by name and returns the groups that hold more than one copy.
     *
     * @param dirs    candidate directories
     * @param policy  which copy to keep
     * @param exclude names matching this pattern are ignored (may be null)
     * @return the dupe groups sorted by name, each with its copies sorted first created first
     */
    public static List<DupeGroup> findDupes(Collection<Copy> dirs, KeepPolicy policy, Pattern exclude) {
        Map<String, List<Copy>> byName = new TreeMap<>();
        Set<String> seenPaths = new HashSet<>();
        for (Copy dir : dirs) {
            String name = dir.name();
            if (name.isEmpty() || !seenPaths.add(normalize(dir.path()))) {
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
     * @param minAgeMillis minimum age, 0 or less disables the check
     * @return true when the directory was modified less than minAgeMillis before now (or in the future)
     */
    public static boolean isActive(long lastModified, long now, long minAgeMillis) {
        return minAgeMillis > 0 && now - lastModified < minAgeMillis;
    }

    /**
     * @return what happens to the given copy of the group
     */
    public static Status status(DupeGroup group, Copy copy, long now, long minAgeMillis) {
        if (copy.equals(group.kept())) {
            return Status.KEEP;
        }
        if (isActive(copy.lastModified(), now, minAgeMillis)) {
            return Status.SKIP_ACTIVE;
        }
        if (isActive(group.kept().lastModified(), now, minAgeMillis)) {
            return Status.SKIP_KEPT_ACTIVE;
        }
        return Status.DUPE;
    }

    /**
     * Groups are handled one after the other, so before acting on a group its kept copy is checked again.
     *
     * The kept copy must not be inside a path acted on earlier, must still exist and must not have become
     * active (see {@link #lastActivity}).
     *
     * @param touched      paths acted on earlier in this run
     * @param now          current time in milliseconds
     * @param minAgeMillis a kept copy modified less than this long ago is active, 0 disables it
     * @return why the group must be skipped, null when the kept copy is safe
     */
    public static String checkKeptCopy(Copy kept, Collection<String> touched, DupeFileSystem fs,
                                       long now, long minAgeMillis) {
        String keptPath = normalize(kept.path());
        for (String path : touched) {
            String p = normalize(path);
            if (keptPath.equals(p) || keptPath.startsWith(p.equals("/") ? p : p + "/")) {
                return "kept copy " + keptPath + " is inside " + p + " which was acted on in this run";
            }
        }
        OptionalLong activity = lastActivity(fs, keptPath);
        if (activity.isEmpty()) {
            return "kept copy " + keptPath + " no longer exists";
        }
        // An upload into the kept copy started after the scan, it may be incomplete or replaced
        if (isActive(activity.getAsLong(), now, minAgeMillis)) {
            return "kept copy " + keptPath + " was modified recently";
        }
        return null;
    }

    /**
     * The newest last modified time of the directory and everything below it.
     * <p>
     * A directory's last modified time only changes when a direct child is added, so an upload into a sub
     * directory of a release (CD2, Sample, ...) would not show on the release itself. Release directories
     * are small, but the walk is bounded defensively: when it goes deeper than {@link #ACTIVITY_MAX_DEPTH}
     * or sees more than {@link #ACTIVITY_MAX_INODES} inodes it stops and returns {@link #ACTIVITY_UNKNOWN},
     * which is always active, so such a release is never acted on.
     *
     * @return the newest last modified time, empty when the directory does not exist (anymore)
     */
    public static OptionalLong lastActivity(DupeFileSystem fs, String path) {
        return lastActivity(fs, path, ACTIVITY_MAX_DEPTH, ACTIVITY_MAX_INODES);
    }

    static OptionalLong lastActivity(DupeFileSystem fs, String path, int maxDepth, int maxInodes) {
        path = normalize(path);
        OptionalLong own = fs.lastModified(path);
        if (own.isEmpty()) {
            return own;
        }
        long newest = own.getAsLong();
        int inodes = 0;
        // Breadth first: directory path and its depth below the release (the release itself is 0)
        Deque<Map.Entry<String, Integer>> todo = new ArrayDeque<>();
        todo.add(Map.entry(path, 0));
        while (!todo.isEmpty()) {
            Map.Entry<String, Integer> dir = todo.poll();
            for (Inode child : fs.children(dir.getKey())) {
                if (++inodes > maxInodes) {
                    return OptionalLong.of(ACTIVITY_UNKNOWN);
                }
                newest = Math.max(newest, child.lastModified());
                if (child.directory()) {
                    if (dir.getValue() + 1 > maxDepth) {
                        return OptionalLong.of(ACTIVITY_UNKNOWN);
                    }
                    todo.add(Map.entry(normalize(child.path()), dir.getValue() + 1));
                }
            }
        }
        return OptionalLong.of(newest);
    }

    /**
     * @return the group with the last modified time of every copy replaced by its {@link #lastActivity},
     * a copy that no longer exists keeps its last modified time (it is skipped when acting on it)
     */
    public static DupeGroup withActivity(DupeGroup group, DupeFileSystem fs) {
        List<Copy> copies = new ArrayList<>();
        Copy kept = null;
        for (Copy copy : group.copies()) {
            long activity = Math.max(copy.lastModified(), lastActivity(fs, copy.path()).orElse(copy.lastModified()));
            Copy updated = new Copy(copy.path(), copy.creationTime(), activity);
            copies.add(updated);
            if (copy.equals(group.kept())) {
                kept = updated;
            }
        }
        return new DupeGroup(group.name(), Collections.unmodifiableList(copies), kept);
    }

    /**
     * Parses dupe.min.age.
     *
     * @param minutes the configured value in minutes, null or blank for the default
     * @return the minimum age in milliseconds, 0 when disabled
     * @throws IllegalArgumentException when the value is not a number or negative
     */
    public static long parseMinAge(String minutes) {
        if (minutes == null || minutes.isBlank()) {
            return DEFAULT_MIN_AGE_MINUTES * 60_000L;
        }
        long value;
        try {
            value = Long.parseLong(minutes.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("dupe.min.age must be a number of minutes: " + minutes);
        }
        if (value < 0) {
            throw new IllegalArgumentException("dupe.min.age must not be negative: " + minutes);
        }
        return value * 60_000L;
    }

    /**
     * Lists the dupe groups and runs the actions on the copies that are not kept.
     * <p>
     * The activity of the copies is the newest last modified time of their whole tree, see
     * {@link #lastActivity}. A copy is only acted on when its status is {@link Status#DUPE}, it still
     * exists, it was not modified within minAgeMillis (checked again right before acting) and the kept
     * copy of its group still exists, was not modified within minAgeMillis (checked again too) and is not
     * inside a path acted on earlier in this run; otherwise the copy, or the whole group when the kept
     * copy is affected, is skipped and reported.
     *
     * @param limit        maximum number of groups
     * @param quiet        only report skipped copies and the lines returned by the actor
     * @param now          current time in milliseconds
     * @param minAgeMillis copies modified less than this long ago are not acted on, 0 disables it
     * @param fs           file system access to re-check the copies
     * @param actor        runs the actions on a copy, null for a dry run
     */
    public static RunResult run(List<DupeGroup> groups, int limit, boolean quiet, long now, long minAgeMillis,
                                DupeFileSystem fs, DupeActor actor) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss");
        List<String> lines = new ArrayList<>();
        Set<String> touched = new LinkedHashSet<>();
        int results = 0;
        for (DupeGroup group : groups) {
            if (results == limit) {
                break;
            }
            // Only the groups within the limit are walked
            group = withActivity(group, fs);
            if (!quiet) {
                lines.add("Dupe " + group.kept().name() + " (" + group.copies().size() + " copies):");
                for (Copy copy : group.copies()) {
                    lines.add("  " + status(group, copy, now, minAgeMillis).label() + " " + copy.path()
                            + " (created " + dateFormat.format(new Date(copy.creationTime())) + ")");
                }
            }
            if (actor != null) {
                for (Copy copy : group.toRemove()) {
                    if (status(group, copy, now, minAgeMillis) != Status.DUPE) {
                        continue;
                    }
                    String reason = checkKeptCopy(group.kept(), touched, fs, now, minAgeMillis);
                    if (reason != null) {
                        lines.add("  SKIP group " + group.kept().name() + ": " + reason);
                        break;
                    }
                    OptionalLong lastModified = lastActivity(fs, copy.path());
                    if (lastModified.isEmpty()) {
                        lines.add("    SKIP " + copy.path() + ": no longer exists");
                        continue;
                    }
                    if (isActive(lastModified.getAsLong(), now, minAgeMillis)) {
                        lines.add("    SKIP (active) " + copy.path());
                        continue;
                    }
                    touched.add(normalize(copy.path()));
                    lines.addAll(actor.act(copy));
                }
            }
            results++;
        }
        return new RunResult(results, lines);
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
