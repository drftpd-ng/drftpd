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

import org.drftpd.find.master.DupeUtils.Copy;
import org.drftpd.find.master.DupeUtils.DupeGroup;
import org.drftpd.find.master.DupeUtils.KeepPolicy;
import org.drftpd.find.master.DupeUtils.RunResult;
import org.drftpd.find.master.DupeUtils.SectionInfo;
import org.drftpd.find.master.DupeUtils.SectionResolver;
import org.drftpd.find.master.DupeUtils.Status;
import org.junit.jupiter.api.Test;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

public class DupeUtilsTest {

    private static final Pattern EXCLUDE = DupeUtils.compileExclude(null);

    private static final long MINUTE = 60_000L;
    private static final long NOW = 1_000_000_000_000L;
    private static final long MIN_AGE = 60 * MINUTE;
    /** Well outside of the active window */
    private static final long OLD = NOW - 24 * 60 * MINUTE;

    private static final SectionInfo MP3_DATED = new SectionInfo("/MP3", new SimpleDateFormat("yyyy/MMdd"));
    private static final SectionInfo ODAY_DATED = new SectionInfo("/0DAY", new SimpleDateFormat("MMdd"));
    private static final SectionInfo MOVIES = new SectionInfo("/MOVIES", null);
    private static final SectionInfo ARCHIVE = new SectionInfo("/ARCHIVE", null);
    private static final SectionInfo ROOT = new SectionInfo("/", null);

    /** Longest base directory prefix wins, like the section manager, the root otherwise */
    private static final SectionResolver RESOLVER = path -> {
        SectionInfo match = ROOT;
        for (SectionInfo section : List.of(MP3_DATED, ODAY_DATED, MOVIES, ARCHIVE)) {
            if ((path.equals(section.basePath()) || path.startsWith(section.basePath() + "/"))
                    && section.basePath().length() > match.basePath().length()) {
                match = section;
            }
        }
        return match;
    };

    private static Copy copy(String path, long created) {
        return new Copy(path, created, created);
    }

    // --- grouping and keep policy ---

    @Test
    public void singleCopyIsNeverSelected() {
        List<DupeGroup> groups = DupeUtils.findDupes(List.of(
                copy("/MP3/1119/Artist-Album-2020-GRP", 100),
                copy("/MP3/1120/Other-Album-2020-GRP", 200)), KeepPolicy.OLDEST, EXCLUDE);
        assertTrue(groups.isEmpty());
    }

    @Test
    public void keepsOldestByDefault() {
        Copy newest = copy("/MP3/1121/Artist-Album-2020-GRP", 300);
        Copy oldest = copy("/MP3/1119/Artist-Album-2020-GRP", 100);
        Copy middle = copy("/ARCHIVE/Artist-Album-2020-GRP", 200);
        List<DupeGroup> groups = DupeUtils.findDupes(List.of(newest, oldest, middle), KeepPolicy.OLDEST, EXCLUDE);

        assertEquals(1, groups.size());
        DupeGroup group = groups.get(0);
        assertEquals(List.of(oldest, middle, newest), group.copies());
        assertEquals(oldest, group.kept());
        assertEquals(List.of(middle, newest), group.toRemove());
    }

    @Test
    public void keepsNewestWhenAsked() {
        Copy older = copy("/MP3/1119/Artist-Album-2020-GRP", 100);
        Copy newer = copy("/MP3/1120/Artist-Album-2020-GRP", 200);
        DupeGroup group = DupeUtils.findDupes(List.of(older, newer), KeepPolicy.NEWEST, EXCLUDE).get(0);

        assertEquals(newer, group.kept());
        assertEquals(List.of(older), group.toRemove());
    }

    @Test
    public void oldestIsFirstCreatedNotLeastRecentlyModified() {
        // Created first, but something was uploaded into it later
        Copy firstCreated = new Copy("/MP3/1119/Release-GRP", 100, 900);
        Copy secondCreated = new Copy("/MP3/1120/Release-GRP", 200, 300);

        DupeGroup oldest = DupeUtils.findDupes(List.of(secondCreated, firstCreated), KeepPolicy.OLDEST, EXCLUDE).get(0);
        assertEquals(firstCreated, oldest.kept());
        assertEquals(List.of(firstCreated, secondCreated), oldest.copies());

        DupeGroup newest = DupeUtils.findDupes(List.of(secondCreated, firstCreated), KeepPolicy.NEWEST, EXCLUDE).get(0);
        assertEquals(secondCreated, newest.kept());
    }

    @Test
    public void namesAreComparedCaseInsensitively() {
        Copy upper = copy("/TV/Show.S01E01.720p-GRP", 100);
        Copy lower = copy("/ARCHIVE/show.s01e01.720p-grp", 200);
        List<DupeGroup> groups = DupeUtils.findDupes(List.of(lower, upper), KeepPolicy.OLDEST, EXCLUDE);

        assertEquals(1, groups.size());
        assertEquals(upper, groups.get(0).kept());
        assertEquals(List.of(lower), groups.get(0).toRemove());
    }

    @Test
    public void sameCreationTimeIsBrokenByPathSoResultIsStable() {
        Copy b = copy("/B/Release-GRP", 100);
        Copy a = copy("/A/Release-GRP", 100);
        DupeGroup group = DupeUtils.findDupes(List.of(b, a), KeepPolicy.OLDEST, EXCLUDE).get(0);

        assertEquals(a, group.kept());
        assertEquals(List.of(b), group.toRemove());
    }

    @Test
    public void samePathTwiceIsNotADupe() {
        Copy c = copy("/MP3/Release-GRP", 100);
        assertTrue(DupeUtils.findDupes(List.of(c, copy("/MP3/Release-GRP/", 100)), KeepPolicy.OLDEST, EXCLUDE).isEmpty());
    }

    @Test
    public void commonSubDirectoriesAreExcludedByDefault() {
        List<DupeGroup> groups = DupeUtils.findDupes(List.of(
                copy("/MOVIES/Movie.A-GRP/Sample", 100),
                copy("/MOVIES/Movie.B-GRP/Sample", 200),
                copy("/MOVIES/Movie.A-GRP/CD1", 100),
                copy("/MOVIES/Movie.B-GRP/cd1", 200),
                copy("/MOVIES/Movie.A-GRP/Subs", 100),
                copy("/MOVIES/Movie.B-GRP/Subs", 200)), KeepPolicy.OLDEST, EXCLUDE);
        assertTrue(groups.isEmpty());
    }

    @Test
    public void excludeCanBeDisabled() {
        assertNull(DupeUtils.compileExclude("none"));
        List<DupeGroup> groups = DupeUtils.findDupes(List.of(
                copy("/MOVIES/Movie.A-GRP/Sample", 100),
                copy("/MOVIES/Movie.B-GRP/Sample", 200)), KeepPolicy.OLDEST, null);
        assertEquals(1, groups.size());
    }

    @Test
    public void groupsAreSortedByNameAndTrailingSlashIsIgnored() {
        List<DupeGroup> groups = DupeUtils.findDupes(List.of(
                copy("/X/Zeta-GRP/", 1),
                copy("/Y/Zeta-GRP", 2),
                copy("/X/Alpha-GRP", 3),
                copy("/Y/Alpha-GRP", 4)), KeepPolicy.OLDEST, EXCLUDE);

        assertEquals(2, groups.size());
        assertEquals("alpha-grp", groups.get(0).name());
        assertEquals("zeta-grp", groups.get(1).name());
    }

    // --- release depth ---

    @Test
    public void plainSectionReleaseIsDirectChildOfBase() {
        assertTrue(DupeUtils.isReleaseDir("/MOVIES/Movie.A-GRP", MOVIES));
        assertTrue(DupeUtils.isReleaseDir("/MOVIES/Movie.A-GRP/", MOVIES));
        assertFalse(DupeUtils.isReleaseDir("/MOVIES", MOVIES));
        assertFalse(DupeUtils.isReleaseDir("/MOVIES/", MOVIES));
        assertFalse(DupeUtils.isReleaseDir("/MOVIES/Movie.A-GRP/VIDEO_TS", MOVIES));
        assertFalse(DupeUtils.isReleaseDir("/", MOVIES));
        // Only a path prefix, not inside the base directory
        assertFalse(DupeUtils.isReleaseDir("/MOVIES-OLD/Movie.A-GRP", MOVIES));
    }

    @Test
    public void nothingIsAReleaseOutsideAConfiguredSection() {
        assertFalse(DupeUtils.isReleaseDir("/MP3", ROOT));
        assertFalse(DupeUtils.isReleaseDir("/Release-GRP", ROOT));
        assertFalse(DupeUtils.isReleaseDir("/", ROOT));
        assertFalse(DupeUtils.isReleaseDir("/MOVIES/Movie.A-GRP", null));
    }

    @Test
    public void datedSectionReleaseIsDirectChildOfDatedDir() {
        assertTrue(DupeUtils.isReleaseDir("/0DAY/1120/Tool.v1.0-GRP", ODAY_DATED));
        // the dated dir itself and the base
        assertFalse(DupeUtils.isReleaseDir("/0DAY/1120", ODAY_DATED));
        assertFalse(DupeUtils.isReleaseDir("/0DAY", ODAY_DATED));
        // not in a dated dir
        assertFalse(DupeUtils.isReleaseDir("/0DAY/Tool.v1.0-GRP", ODAY_DATED));
        assertFalse(DupeUtils.isReleaseDir("/0DAY/_ARCHIVE/Tool.v1.0-GRP", ODAY_DATED));
        assertFalse(DupeUtils.isReleaseDir("/0DAY/1340/Tool.v1.0-GRP", ODAY_DATED));
        assertFalse(DupeUtils.isReleaseDir("/0DAY/112/Tool.v1.0-GRP", ODAY_DATED));
        // sub dir of a release
        assertFalse(DupeUtils.isReleaseDir("/0DAY/1120/Tool.v1.0-GRP/Keygen", ODAY_DATED));
    }

    @Test
    public void datedDirsCanSpanSeveralLevels() {
        assertTrue(DupeUtils.isReleaseDir("/MP3/2025/1120/Artist-Album-2025-GRP", MP3_DATED));
        assertFalse(DupeUtils.isReleaseDir("/MP3/2025/1120", MP3_DATED));
        assertFalse(DupeUtils.isReleaseDir("/MP3/2025", MP3_DATED));
        assertFalse(DupeUtils.isReleaseDir("/MP3/2025/Artist-Album-2025-GRP", MP3_DATED));
        assertFalse(DupeUtils.isReleaseDir("/MP3/2025/1120/Artist-Album-2025-GRP/CD1", MP3_DATED));
    }

    @Test
    public void onlyReleasesAreComparedWhenStartingFromRoot() {
        // The examples of the review: whole sections, dated dirs and sub dirs of different releases
        List<Copy> found = List.of(
                copy("/MP3", 1),
                copy("/ARCHIVE/MP3", 2),
                copy("/MP3/2024/1120", 3),
                copy("/MP3/2025/1120", 4),
                copy("/MOVIES/Movie.A-GRP/VIDEO_TS", 5),
                copy("/MOVIES/Movie.B-GRP/VIDEO_TS", 6),
                copy("/MOVIES/Movie.A-GRP/GRP", 7),
                copy("/MOVIES/Movie.B-GRP/GRP", 8),
                copy("/MP3/2024/1120/Artist-Album-2024-GRP", 9),
                copy("/ARCHIVE/Artist-Album-2024-GRP", 10));

        List<Copy> releases = DupeUtils.removeNested(DupeUtils.filterReleaseDirs(found, RESOLVER));
        List<DupeGroup> groups = DupeUtils.findDupes(releases, KeepPolicy.OLDEST, EXCLUDE);

        assertEquals(1, groups.size());
        assertEquals("artist-album-2024-grp", groups.get(0).name());
        assertEquals("/MP3/2024/1120/Artist-Album-2024-GRP", groups.get(0).kept().path());
        assertEquals(List.of(copy("/ARCHIVE/Artist-Album-2024-GRP", 10)), groups.get(0).toRemove());
    }

    // --- containment ---

    @Test
    public void candidatesContainingEachOtherAreDropped() {
        Copy outer = copy("/ARCHIVE/Release-GRP", 1);
        Copy inner = copy("/ARCHIVE/Release-GRP/old/Release-GRP", 2);
        Copy sibling = copy("/ARCHIVE/Release-GRP-PROPER", 3);
        Copy other = copy("/MOVIES/Release-GRP", 4);

        assertEquals(List.of(sibling, other), DupeUtils.removeNested(List.of(outer, inner, sibling, other)));
    }

    @Test
    public void containmentIgnoresTrailingSlash() {
        Copy outer = copy("/A/Release-GRP/", 1);
        Copy inner = copy("/A/Release-GRP/Release-GRP", 2);
        assertTrue(DupeUtils.removeNested(List.of(outer, inner)).isEmpty());
    }

    // --- active window ---

    @Test
    public void activeWindow() {
        assertTrue(DupeUtils.isActive(NOW - MINUTE, NOW, MIN_AGE));
        assertTrue(DupeUtils.isActive(NOW + MINUTE, NOW, MIN_AGE));
        assertFalse(DupeUtils.isActive(NOW - MIN_AGE, NOW, MIN_AGE));
        assertFalse(DupeUtils.isActive(OLD, NOW, MIN_AGE));
        // 0 disables the check
        assertFalse(DupeUtils.isActive(NOW, NOW, 0));
    }

    @Test
    public void recentlyModifiedCopiesAreSkipped() {
        Copy kept = new Copy("/A/Release-GRP", OLD - 10, OLD);
        Copy active = new Copy("/B/Release-GRP", OLD - 5, NOW - MINUTE);
        Copy dupe = new Copy("/C/Release-GRP", OLD - 1, OLD);
        DupeGroup group = DupeUtils.findDupes(List.of(kept, active, dupe), KeepPolicy.OLDEST, EXCLUDE).get(0);

        assertEquals(Status.KEEP, DupeUtils.status(group, kept, NOW, MIN_AGE));
        assertEquals(Status.SKIP_ACTIVE, DupeUtils.status(group, active, NOW, MIN_AGE));
        assertEquals(Status.DUPE, DupeUtils.status(group, dupe, NOW, MIN_AGE));
        assertEquals(Status.DUPE, DupeUtils.status(group, active, NOW, 0));
    }

    @Test
    public void nothingIsActedOnWhileKeptCopyIsActive() {
        // With "newest" the kept copy may be an upload in progress, the complete older copy must stay
        Copy older = new Copy("/A/Release-GRP", OLD, OLD);
        Copy uploading = new Copy("/B/Release-GRP", NOW - 2 * MINUTE, NOW - MINUTE);
        DupeGroup group = DupeUtils.findDupes(List.of(older, uploading), KeepPolicy.NEWEST, EXCLUDE).get(0);

        assertEquals(uploading, group.kept());
        assertEquals(Status.SKIP_KEPT_ACTIVE, DupeUtils.status(group, older, NOW, MIN_AGE));
    }

    @Test
    public void minAgeParsing() {
        assertEquals(60 * MINUTE, DupeUtils.parseMinAge(null));
        assertEquals(60 * MINUTE, DupeUtils.parseMinAge(" "));
        assertEquals(15 * MINUTE, DupeUtils.parseMinAge(" 15 "));
        assertEquals(0, DupeUtils.parseMinAge("0"));
        assertThrows(IllegalArgumentException.class, () -> DupeUtils.parseMinAge("-1"));
        assertThrows(IllegalArgumentException.class, () -> DupeUtils.parseMinAge("1h"));
    }

    // --- kept copy re-check ---

    @Test
    public void keptCopyCheck() {
        FakeFs fs = new FakeFs("/A/Release-GRP", "/A/Release-GRP-PROPER");
        Copy kept = copy("/A/Release-GRP", OLD);

        assertNull(DupeUtils.checkKeptCopy(kept, List.of(), fs));
        assertNull(DupeUtils.checkKeptCopy(kept, List.of("/A/Release-GRP-PROPER", "/B/Release-GRP"), fs));
        assertNotNull(DupeUtils.checkKeptCopy(kept, List.of("/A/Release-GRP"), fs));
        assertNotNull(DupeUtils.checkKeptCopy(kept, List.of("/A/"), fs));
        assertNotNull(DupeUtils.checkKeptCopy(copy("/X/Gone-GRP", OLD), List.of(), fs));
    }

    // --- run ---

    @Test
    public void dryRunOnlyLists() {
        Copy kept = copy("/A/Release-GRP", OLD - 1);
        Copy dupe = copy("/B/Release-GRP", OLD);
        List<DupeGroup> groups = DupeUtils.findDupes(List.of(kept, dupe), KeepPolicy.OLDEST, EXCLUDE);

        RunResult result = DupeUtils.run(groups, 10, false, NOW, MIN_AGE, new FakeFs("/A/Release-GRP", "/B/Release-GRP"), null);

        assertEquals(1, result.groups());
        assertTrue(result.lines().stream().anyMatch(l -> l.startsWith("  KEEP /A/Release-GRP")));
        assertTrue(result.lines().stream().anyMatch(l -> l.startsWith("  DUPE /B/Release-GRP")));
    }

    @Test
    public void actsOnlyOnDupesThatAreNotActive() {
        Copy kept = copy("/A/Release-GRP", OLD - 3);
        Copy dupe = copy("/B/Release-GRP", OLD - 2);
        Copy active = new Copy("/C/Release-GRP", OLD - 1, NOW - MINUTE);
        // Was idle when found but an upload started before it is acted on
        Copy becameActive = copy("/D/Release-GRP", OLD);
        List<DupeGroup> groups = DupeUtils.findDupes(List.of(kept, dupe, active, becameActive), KeepPolicy.OLDEST, EXCLUDE);
        FakeFs fs = new FakeFs("/A/Release-GRP", "/B/Release-GRP", "/C/Release-GRP", "/D/Release-GRP");
        fs.touch("/C/Release-GRP", NOW - MINUTE);
        fs.touch("/D/Release-GRP", NOW - MINUTE);

        RunResult result = DupeUtils.run(groups, 10, false, NOW, MIN_AGE, fs, fs::wipe);

        assertEquals(List.of("/B/Release-GRP"), fs.wiped);
        assertTrue(result.lines().stream().anyMatch(l -> l.startsWith("  SKIP (active) /C/Release-GRP (created ")));
        assertTrue(result.lines().contains("    SKIP (active) /D/Release-GRP"));
    }

    @Test
    public void groupIsSkippedWhenKeptCopyWasRemovedByAnEarlierGroup() {
        // Built by hand: the release depth and containment filters normally prevent this, the re-check
        // is the last line of defence.
        Copy keptA = copy("/S1/Release-GRP", OLD - 10);
        Copy dupeA = copy("/S2/Release-GRP", OLD - 9);
        DupeGroup groupA = new DupeGroup("release-grp", List.of(keptA, dupeA), keptA);
        Copy keptB = copy("/S2/Release-GRP/Sub-GRP", OLD - 8);
        Copy dupeB = copy("/S3/Sub-GRP", OLD - 7);
        DupeGroup groupB = new DupeGroup("sub-grp", List.of(keptB, dupeB), keptB);
        FakeFs fs = new FakeFs(keptA.path(), dupeA.path(), keptB.path(), dupeB.path());

        RunResult result = DupeUtils.run(List.of(groupA, groupB), 10, true, NOW, MIN_AGE, fs, fs::wipe);

        assertEquals(List.of("/S2/Release-GRP"), fs.wiped);
        assertTrue(fs.exists("/S3/Sub-GRP"));
        assertTrue(result.lines().stream().anyMatch(l -> l.startsWith("  SKIP group Sub-GRP: kept copy /S2/Release-GRP/Sub-GRP")));
        assertEquals(2, result.groups());
    }

    @Test
    public void groupIsSkippedWhenKeptCopyIsGone() {
        Copy kept = copy("/A/Release-GRP", OLD - 1);
        Copy dupe = copy("/B/Release-GRP", OLD);
        List<DupeGroup> groups = DupeUtils.findDupes(List.of(kept, dupe), KeepPolicy.OLDEST, EXCLUDE);
        FakeFs fs = new FakeFs("/B/Release-GRP");

        RunResult result = DupeUtils.run(groups, 10, true, NOW, MIN_AGE, fs, fs::wipe);

        assertTrue(fs.wiped.isEmpty());
        assertEquals(List.of("  SKIP group Release-GRP: kept copy /A/Release-GRP no longer exists"), result.lines());
    }

    @Test
    public void limitCountsGroups() {
        List<DupeGroup> groups = DupeUtils.findDupes(List.of(
                copy("/A/One-GRP", OLD - 1), copy("/B/One-GRP", OLD),
                copy("/A/Two-GRP", OLD - 1), copy("/B/Two-GRP", OLD)), KeepPolicy.OLDEST, EXCLUDE);
        FakeFs fs = new FakeFs("/A/One-GRP", "/B/One-GRP", "/A/Two-GRP", "/B/Two-GRP");

        RunResult result = DupeUtils.run(groups, 1, true, NOW, MIN_AGE, fs, fs::wipe);

        assertEquals(1, result.groups());
        assertEquals(List.of("/B/One-GRP"), fs.wiped);
    }

    /**
     * In memory directory tree, wiping a directory removes everything below it.
     */
    private static class FakeFs implements DupeUtils.DupeFileSystem {
        private final Map<String, Long> _dirs = new HashMap<>();
        final List<String> wiped = new ArrayList<>();

        FakeFs(String... paths) {
            for (String path : paths) {
                _dirs.put(path, OLD);
            }
        }

        void touch(String path, long lastModified) {
            _dirs.put(path, lastModified);
        }

        boolean exists(String path) {
            return _dirs.containsKey(path);
        }

        @Override
        public OptionalLong lastModified(String path) {
            Long lastModified = _dirs.get(path);
            return lastModified == null ? OptionalLong.empty() : OptionalLong.of(lastModified);
        }

        List<String> wipe(Copy copy) {
            wiped.add(copy.path());
            _dirs.keySet().removeIf(p -> p.equals(copy.path()) || p.startsWith(copy.path() + "/"));
            return List.of("    Wiped " + copy.path());
        }
    }
}
