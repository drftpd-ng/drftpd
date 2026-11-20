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
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

public class DupeUtilsTest {

    private static final Pattern EXCLUDE = DupeUtils.compileExclude(null);

    @Test
    public void singleCopyIsNeverSelected() {
        List<DupeGroup> groups = DupeUtils.findDupes(List.of(
                new Copy("/MP3/1119/Artist-Album-2020-GRP", 100),
                new Copy("/MP3/1120/Other-Album-2020-GRP", 200)), KeepPolicy.OLDEST, EXCLUDE);
        assertTrue(groups.isEmpty());
    }

    @Test
    public void keepsOldestByDefault() {
        Copy newest = new Copy("/MP3/1121/Artist-Album-2020-GRP", 300);
        Copy oldest = new Copy("/MP3/1119/Artist-Album-2020-GRP", 100);
        Copy middle = new Copy("/ARCHIVE/Artist-Album-2020-GRP", 200);
        List<DupeGroup> groups = DupeUtils.findDupes(List.of(newest, oldest, middle), KeepPolicy.OLDEST, EXCLUDE);

        assertEquals(1, groups.size());
        DupeGroup group = groups.get(0);
        assertEquals(List.of(oldest, middle, newest), group.copies());
        assertEquals(oldest, group.kept());
        assertEquals(List.of(middle, newest), group.toRemove());
    }

    @Test
    public void keepsNewestWhenAsked() {
        Copy older = new Copy("/MP3/1119/Artist-Album-2020-GRP", 100);
        Copy newer = new Copy("/MP3/1120/Artist-Album-2020-GRP", 200);
        DupeGroup group = DupeUtils.findDupes(List.of(older, newer), KeepPolicy.NEWEST, EXCLUDE).get(0);

        assertEquals(newer, group.kept());
        assertEquals(List.of(older), group.toRemove());
    }

    @Test
    public void namesAreComparedCaseInsensitively() {
        Copy upper = new Copy("/TV/Show.S01E01.720p-GRP", 100);
        Copy lower = new Copy("/ARCHIVE/show.s01e01.720p-grp", 200);
        List<DupeGroup> groups = DupeUtils.findDupes(List.of(lower, upper), KeepPolicy.OLDEST, EXCLUDE);

        assertEquals(1, groups.size());
        assertEquals(upper, groups.get(0).kept());
        assertEquals(List.of(lower), groups.get(0).toRemove());
    }

    @Test
    public void sameTimestampIsBrokenByPathSoResultIsStable() {
        Copy b = new Copy("/B/Release-GRP", 100);
        Copy a = new Copy("/A/Release-GRP", 100);
        DupeGroup group = DupeUtils.findDupes(List.of(b, a), KeepPolicy.OLDEST, EXCLUDE).get(0);

        assertEquals(a, group.kept());
        assertEquals(List.of(b), group.toRemove());
    }

    @Test
    public void samePathTwiceIsNotADupe() {
        Copy c = new Copy("/MP3/Release-GRP", 100);
        assertTrue(DupeUtils.findDupes(List.of(c, new Copy("/MP3/Release-GRP", 100)), KeepPolicy.OLDEST, EXCLUDE).isEmpty());
    }

    @Test
    public void commonSubDirectoriesAreExcludedByDefault() {
        List<DupeGroup> groups = DupeUtils.findDupes(List.of(
                new Copy("/MOVIES/Movie.A-GRP/Sample", 100),
                new Copy("/MOVIES/Movie.B-GRP/Sample", 200),
                new Copy("/MOVIES/Movie.A-GRP/CD1", 100),
                new Copy("/MOVIES/Movie.B-GRP/cd1", 200),
                new Copy("/MOVIES/Movie.A-GRP/Subs", 100),
                new Copy("/MOVIES/Movie.B-GRP/Subs", 200)), KeepPolicy.OLDEST, EXCLUDE);
        assertTrue(groups.isEmpty());
    }

    @Test
    public void excludeCanBeDisabled() {
        assertNull(DupeUtils.compileExclude("none"));
        List<DupeGroup> groups = DupeUtils.findDupes(List.of(
                new Copy("/MOVIES/Movie.A-GRP/Sample", 100),
                new Copy("/MOVIES/Movie.B-GRP/Sample", 200)), KeepPolicy.OLDEST, null);
        assertEquals(1, groups.size());
    }

    @Test
    public void groupsAreSortedByNameAndTrailingSlashIsIgnored() {
        List<DupeGroup> groups = DupeUtils.findDupes(List.of(
                new Copy("/X/Zeta-GRP/", 1),
                new Copy("/Y/Zeta-GRP", 2),
                new Copy("/X/Alpha-GRP", 3),
                new Copy("/Y/Alpha-GRP", 4)), KeepPolicy.OLDEST, EXCLUDE);

        assertEquals(2, groups.size());
        assertEquals("alpha-grp", groups.get(0).name());
        assertEquals("zeta-grp", groups.get(1).name());
    }
}
