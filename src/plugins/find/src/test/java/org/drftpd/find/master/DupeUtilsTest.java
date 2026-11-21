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
    public void february29thIsADatedDirWithoutYearInTheFormat() {
        // MMdd has no year, parsed in 1970 0229 would not be a valid date
        assertTrue(DupeUtils.isDatedDir("0229", new SimpleDateFormat("MMdd")));
        assertTrue(DupeUtils.isReleaseDir("/0DAY/0229/Tool.v1.0-GRP", ODAY_DATED));
        assertTrue(DupeUtils.isDatedDir("1231", new SimpleDateFormat("MMdd")));
        assertTrue(DupeUtils.isDatedDir("29-02", new SimpleDateFormat("dd-MM")));
        assertFalse(DupeUtils.isDatedDir("0230", new SimpleDateFormat("MMdd")));
        assertFalse(DupeUtils.isDatedDir("1301", new SimpleDateFormat("MMdd")));
        // With a year the year decides
        assertTrue(DupeUtils.isDatedDir("2024/0229", new SimpleDateFormat("yyyy/MMdd")));
        assertFalse(DupeUtils.isDatedDir("2025/0229", new SimpleDateFormat("yyyy/MMdd")));
    }

    @Test
    public void firstWeekOfAYearIsADatedDir() {
        // Week 1 of 2026 starts in December 2025, yyyy-ww does not round trip for it
        for (Locale locale : List.of(Locale.US, Locale.GERMANY)) {
            SimpleDateFormat weekly = new SimpleDateFormat("yyyy-ww", locale);
            assertTrue(DupeUtils.isDatedDir("2026-01", weekly), locale.toString());
            assertTrue(DupeUtils.isDatedDir("2026-10", weekly), locale.toString());
            assertTrue(DupeUtils.isDatedDir("2025-52", weekly), locale.toString());
            assertFalse(DupeUtils.isDatedDir("2026-60", weekly), locale.toString());
            assertFalse(DupeUtils.isDatedDir("2026-00", weekly), locale.toString());
            assertFalse(DupeUtils.isDatedDir("2026-1", weekly), locale.toString());
        }
        SectionInfo weeklySection = new SectionInfo("/TV", new SimpleDateFormat("yyyy-ww", Locale.US));
        assertTrue(DupeUtils.isReleaseDir("/TV/2026-01/Show.S01E01-GRP", weeklySection));
        // Every dir the section creates is recognised, including the ones around the year boundary
        SimpleDateFormat weekly = new SimpleDateFormat("yyyy-ww", Locale.US);
        weekly.setTimeZone(TimeZone.getTimeZone("UTC"));
        for (long day = 0; day < 3 * 366; day++) {
            String dir = weekly.format(new Date(1_735_689_600_000L + day * 24 * 60 * MINUTE)); // 2025-01-01
            assertTrue(DupeUtils.isDatedDir(dir, weekly), dir);
        }
    }

    @Test
    public void everyWeekDirTheFormatProducesIsADatedDirInEveryLocale() {
        // de_DE: 1-3 January 2016, 2021 and 2027 are week 53 of the previous year and yyyy-ww produces 2027-53
        for (Locale locale : List.of(Locale.US, Locale.GERMANY, Locale.FRANCE)) {
            SimpleDateFormat weekly = new SimpleDateFormat("yyyy-ww", locale);
            weekly.setTimeZone(TimeZone.getTimeZone("UTC"));
            long start = 1_448_928_000_000L; // 2015-12-01
            for (long day = 0; day < 12 * 366 + 60; day++) { // up to early 2028
                String dir = weekly.format(new Date(start + day * 24 * 60 * MINUTE));
                assertTrue(DupeUtils.isDatedDir(dir, weekly), locale + " " + dir);
            }
        }
        SimpleDateFormat german = new SimpleDateFormat("yyyy-ww", Locale.GERMANY);
        for (String dir : List.of("2016-53", "2021-53", "2027-53")) {
            assertTrue(DupeUtils.isDatedDir(dir, german), dir);
        }
        assertTrue(DupeUtils.isReleaseDir("/TV/2027-53/Show.S01E01-GRP", new SectionInfo("/TV", german)));
        // Only names the format itself produces are accepted
        assertFalse(DupeUtils.isDatedDir("2025-53", german));
        assertFalse(DupeUtils.isDatedDir("2027-54", german));
        assertFalse(DupeUtils.isDatedDir("2027-00", german));
        assertFalse(DupeUtils.isDatedDir("2027-53", new SimpleDateFormat("yyyy-ww", Locale.US)));
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

    // --- actions ---

    @Test
    public void onlyReviewedActionsCanBeCombinedWithDupe() {
        for (String allowed : List.of("Print", "Printf", "Wipe", "Nuke", "wipe")) {
            assertTrue(DupeUtils.isDupeActionAllowed(allowed), allowed);
        }
        for (String refused : List.of("Unnuke", "SendToSlaves", "DeleteFromSlaves", "Delete", "SomeFutureAction")) {
            assertFalse(DupeUtils.isDupeActionAllowed(refused), refused);
        }
        assertFalse(DupeUtils.isDupeActionAllowed(null));
    }

    @Test
    public void actionsThatChangeSomethingNeedADupePermission() {
        assertEquals("dupewipe", DupeUtils.dupeActionPermission("Wipe"));
        // -nuke runs on dirs too, without its own permission -dupe -nuke would mass nuke with only "nuke"
        assertEquals("dupenuke", DupeUtils.dupeActionPermission("Nuke"));
        assertNull(DupeUtils.dupeActionPermission("Print"));
        assertNull(DupeUtils.dupeActionPermission("Printf"));
    }

    // --- kept copy re-check ---

    @Test
    public void keptCopyCheck() {
        FakeFs fs = new FakeFs("/A/Release-GRP", "/A/Release-GRP-PROPER");
        Copy kept = copy("/A/Release-GRP", OLD);

        assertNull(DupeUtils.checkKeptCopy(kept, List.of(), fs, NOW, MIN_AGE));
        assertNull(DupeUtils.checkKeptCopy(kept, List.of("/A/Release-GRP-PROPER", "/B/Release-GRP"), fs, NOW, MIN_AGE));
        assertNotNull(DupeUtils.checkKeptCopy(kept, List.of("/A/Release-GRP"), fs, NOW, MIN_AGE));
        assertNotNull(DupeUtils.checkKeptCopy(kept, List.of("/A/"), fs, NOW, MIN_AGE));
        assertNotNull(DupeUtils.checkKeptCopy(copy("/X/Gone-GRP", OLD), List.of(), fs, NOW, MIN_AGE));
    }

    @Test
    public void keptCopyCheckSeesActivityDeepInsideTheKeptCopy() {
        FakeFs fs = new FakeFs("/A/Release-GRP", "/A/Release-GRP/CD2");
        Copy kept = copy("/A/Release-GRP", OLD);
        assertNull(DupeUtils.checkKeptCopy(kept, List.of(), fs, NOW, MIN_AGE));

        fs.file("/A/Release-GRP/CD2/release-grp.r00", NOW - MINUTE);

        assertEquals("kept copy /A/Release-GRP was modified recently",
                DupeUtils.checkKeptCopy(kept, List.of(), fs, NOW, MIN_AGE));
        assertNull(DupeUtils.checkKeptCopy(kept, List.of(), fs, NOW, 0));
    }

    // --- activity of the whole release ---

    @Test
    public void activityIsTheNewestChangeInTheWholeRelease() {
        FakeFs fs = new FakeFs("/A/Release-GRP", "/A/Release-GRP/CD1", "/A/Release-GRP/CD2", "/A/Release-GRP/CD2/Sample");
        fs.file("/A/Release-GRP/release-grp.nfo", OLD);
        fs.file("/A/Release-GRP/CD2/Sample/sample.mkv", NOW - MINUTE);

        assertEquals(OptionalLong.of(NOW - MINUTE), DupeUtils.lastActivity(fs, "/A/Release-GRP"));
        assertEquals(OptionalLong.of(NOW - MINUTE), DupeUtils.lastActivity(fs, "/A/Release-GRP/"));
        assertEquals(OptionalLong.of(OLD), DupeUtils.lastActivity(fs, "/A/Release-GRP/CD1"));
        assertEquals(OptionalLong.empty(), DupeUtils.lastActivity(fs, "/A/Gone-GRP"));
    }

    @Test
    public void activityWalkIsBoundedAndHittingTheBoundIsActive() {
        FakeFs fs = new FakeFs("/A/Release-GRP", "/A/Release-GRP/1", "/A/Release-GRP/1/2", "/A/Release-GRP/1/2/3");
        fs.file("/A/Release-GRP/f1", OLD);
        fs.file("/A/Release-GRP/f2", OLD);

        // depth: dirs up to maxDepth below the release are walked, a deeper dir means unknown
        assertEquals(OptionalLong.of(OLD), DupeUtils.lastActivity(fs, "/A/Release-GRP", 3, 100));
        assertEquals(OptionalLong.of(DupeUtils.ACTIVITY_UNKNOWN), DupeUtils.lastActivity(fs, "/A/Release-GRP", 2, 100));
        // inodes: 5 inodes below the release
        assertEquals(OptionalLong.of(OLD), DupeUtils.lastActivity(fs, "/A/Release-GRP", 10, 5));
        assertEquals(OptionalLong.of(DupeUtils.ACTIVITY_UNKNOWN), DupeUtils.lastActivity(fs, "/A/Release-GRP", 10, 4));

        assertTrue(DupeUtils.isActive(DupeUtils.ACTIVITY_UNKNOWN, NOW, MIN_AGE));
    }

    /** A release nested deeper than the activity walk goes (ACTIVITY_MAX_DEPTH levels) */
    private static FakeFs deepFs(String... releases) {
        FakeFs fs = new FakeFs(releases);
        for (String release : releases) {
            String dir = release;
            for (int i = 1; i <= DupeUtils.ACTIVITY_MAX_DEPTH + 1; i++) {
                dir = dir + "/" + i;
                fs.touch(dir, OLD);
            }
        }
        return fs;
    }

    @Test
    public void tooLargeToCheckIsNeverActedOnWhateverTheMinAge() {
        for (long minAge : new long[]{MIN_AGE, 0}) {
            Copy kept = copy("/A/Release-GRP", OLD - 2);
            Copy dupe = copy("/B/Release-GRP", OLD - 1);
            List<DupeGroup> groups = DupeUtils.findDupes(List.of(kept, dupe), KeepPolicy.OLDEST, EXCLUDE);
            FakeFs fs = deepFs("/A/Release-GRP", "/B/Release-GRP");

            RunResult result = DupeUtils.run(groups, 10, false, NOW, minAge, fs, fs::wipe);

            assertTrue(fs.wiped.isEmpty(), "min age " + minAge);
            assertTrue(result.lines().stream().anyMatch(l -> l.startsWith("  SKIP (too large to check) /B/Release-GRP (created ")),
                    result.lines().toString());
            assertFalse(result.lines().stream().anyMatch(l -> l.contains("recently") || l.contains("(active)")),
                    result.lines().toString());
        }
    }

    @Test
    public void tooLargeKeptCopyIsReportedAndSkipsTheGroup() {
        for (long minAge : new long[]{MIN_AGE, 0}) {
            Copy kept = copy("/A/Release-GRP", OLD - 2);
            Copy dupe = copy("/B/Release-GRP", OLD - 1);
            List<DupeGroup> groups = DupeUtils.findDupes(List.of(kept, dupe), KeepPolicy.OLDEST, EXCLUDE);
            FakeFs fs = deepFs("/A/Release-GRP");
            fs.touch("/B/Release-GRP", OLD);

            RunResult result = DupeUtils.run(groups, 10, false, NOW, minAge, fs, fs::wipe);

            assertTrue(fs.wiped.isEmpty(), "min age " + minAge);
            assertTrue(result.lines().stream().anyMatch(l -> l.startsWith("  SKIP (kept copy too large to check) /B/Release-GRP")),
                    result.lines().toString());
            assertNull(DupeUtils.checkKeptCopy(copy("/B/Release-GRP", OLD), List.of(), fs, NOW, minAge));
            assertEquals("kept copy /A/Release-GRP is too large to check",
                    DupeUtils.checkKeptCopy(kept, List.of(), fs, NOW, minAge));
        }
    }

    @Test
    public void copyThatGrewTooLargeAfterTheScanIsSkippedInTheRecheck() {
        Copy kept = copy("/A/Release-GRP", OLD - 2);
        Copy dupe = copy("/B/Release-GRP", OLD - 1);
        List<DupeGroup> groups = DupeUtils.findDupes(List.of(kept, dupe), KeepPolicy.OLDEST, EXCLUDE);
        FakeFs fs = new FakeFs("/A/Release-GRP", "/B/Release-GRP");
        // The scan sees small copies, the recheck right before acting sees a too deep tree
        boolean[] grown = {false};
        DupeUtils.DupeFileSystem growing = new DupeUtils.DupeFileSystem() {
            @Override
            public OptionalLong lastModified(String path) {
                return fs.lastModified(path);
            }

            @Override
            public Collection<DupeUtils.Inode> children(String path) {
                if (path.equals("/B/Release-GRP") && grown[0]) {
                    return List.of(new DupeUtils.Inode(path + "/1", OLD, true));
                }
                if (path.startsWith("/B/Release-GRP/") && grown[0]) {
                    int depth = path.split("/").length - 3;
                    return depth > DupeUtils.ACTIVITY_MAX_DEPTH ? List.of()
                            : List.of(new DupeUtils.Inode(path + "/" + (depth + 1), OLD, true));
                }
                grown[0] |= path.equals("/B/Release-GRP"); // the scan walked it, it grows from now on
                return fs.children(path);
            }
        };

        RunResult result = DupeUtils.run(groups, 10, true, NOW, 0, growing, fs::wipe);

        assertTrue(fs.wiped.isEmpty());
        assertTrue(result.lines().contains("    SKIP (too large to check) /B/Release-GRP"), result.lines().toString());
    }

    @Test
    public void uploadIntoASubDirOfADupeIsActive() {
        Copy kept = copy("/A/Release-GRP", OLD - 2);
        // The release dir itself looks idle, the upload goes into CD2
        Copy dupe = copy("/B/Release-GRP", OLD - 1);
        List<DupeGroup> groups = DupeUtils.findDupes(List.of(kept, dupe), KeepPolicy.OLDEST, EXCLUDE);
        FakeFs fs = new FakeFs("/A/Release-GRP", "/B/Release-GRP", "/B/Release-GRP/CD2");
        fs.file("/B/Release-GRP/CD2/release-grp.r05", NOW - MINUTE);

        RunResult result = DupeUtils.run(groups, 10, false, NOW, MIN_AGE, fs, fs::wipe);

        assertTrue(fs.wiped.isEmpty());
        assertTrue(result.lines().stream().anyMatch(l -> l.startsWith("  SKIP (active) /B/Release-GRP (created ")));
    }

    @Test
    public void uploadIntoASubDirOfADupeAfterTheScanIsActive() {
        Copy kept = copy("/A/Release-GRP", OLD - 3);
        Copy first = copy("/B/Release-GRP", OLD - 2);
        Copy second = copy("/C/Release-GRP", OLD - 1);
        List<DupeGroup> groups = DupeUtils.findDupes(List.of(kept, first, second), KeepPolicy.OLDEST, EXCLUDE);
        FakeFs fs = new FakeFs("/A/Release-GRP", "/B/Release-GRP", "/C/Release-GRP", "/C/Release-GRP/Sample");
        // The upload into /C starts while /B is being acted on, after the group was listed
        RunResult result = DupeUtils.run(groups, 10, true, NOW, MIN_AGE, fs, copy -> {
            fs.file("/C/Release-GRP/Sample/sample.mkv", NOW);
            return fs.wipe(copy);
        });

        assertEquals(List.of("/B/Release-GRP"), fs.wiped);
        assertTrue(result.lines().contains("    SKIP (active) /C/Release-GRP"));
    }

    @Test
    public void groupIsSkippedWhenKeptCopyBecomesActive() {
        Copy kept = copy("/A/Release-GRP", OLD - 3);
        Copy first = copy("/B/Release-GRP", OLD - 2);
        Copy second = copy("/C/Release-GRP", OLD - 1);
        List<DupeGroup> groups = DupeUtils.findDupes(List.of(kept, first, second), KeepPolicy.OLDEST, EXCLUDE);
        FakeFs fs = new FakeFs("/A/Release-GRP", "/A/Release-GRP/CD1", "/B/Release-GRP", "/C/Release-GRP");
        // An upload into the kept copy starts while the first dupe is being acted on
        RunResult result = DupeUtils.run(groups, 10, true, NOW, MIN_AGE, fs, copy -> {
            fs.file("/A/Release-GRP/CD1/release-grp.r00", NOW);
            return fs.wipe(copy);
        });

        assertEquals(List.of("/B/Release-GRP"), fs.wiped);
        assertTrue(fs.exists("/C/Release-GRP"));
        assertTrue(result.lines().contains("  SKIP group Release-GRP: kept copy /A/Release-GRP was modified recently"));
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

        // The upload into /D starts while /B is being acted on, after the group was listed
        RunResult result = DupeUtils.run(groups, 10, false, NOW, MIN_AGE, fs, copy -> {
            fs.touch("/D/Release-GRP", NOW - MINUTE);
            return fs.wipe(copy);
        });

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
     * Like the real file system, adding a file only changes the last modified time of its own directory.
     */
    private static class FakeFs implements DupeUtils.DupeFileSystem {
        private final Map<String, Long> _dirs = new HashMap<>();
        private final Map<String, Long> _files = new HashMap<>();
        final List<String> wiped = new ArrayList<>();

        FakeFs(String... paths) {
            for (String path : paths) {
                _dirs.put(path, OLD);
            }
        }

        void touch(String path, long lastModified) {
            _dirs.put(path, lastModified);
        }

        void file(String path, long lastModified) {
            _files.put(path, lastModified);
            _dirs.put(path.substring(0, path.lastIndexOf('/')), lastModified);
        }

        boolean exists(String path) {
            return _dirs.containsKey(path);
        }

        @Override
        public OptionalLong lastModified(String path) {
            Long lastModified = _dirs.get(path);
            return lastModified == null ? OptionalLong.empty() : OptionalLong.of(lastModified);
        }

        @Override
        public Collection<DupeUtils.Inode> children(String path) {
            List<DupeUtils.Inode> children = new ArrayList<>();
            _dirs.forEach((p, lastModified) -> {
                if (p.startsWith(path + "/") && p.indexOf('/', path.length() + 1) < 0) {
                    children.add(new DupeUtils.Inode(p, lastModified, true));
                }
            });
            _files.forEach((p, lastModified) -> {
                if (p.startsWith(path + "/") && p.indexOf('/', path.length() + 1) < 0) {
                    children.add(new DupeUtils.Inode(p, lastModified, false));
                }
            });
            return children;
        }

        List<String> wipe(Copy copy) {
            wiped.add(copy.path());
            _dirs.keySet().removeIf(p -> p.equals(copy.path()) || p.startsWith(copy.path() + "/"));
            _files.keySet().removeIf(p -> p.startsWith(copy.path() + "/"));
            return List.of("    Wiped " + copy.path());
        }
    }
}
