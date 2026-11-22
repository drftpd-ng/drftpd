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
package org.drftpd.master.slaveselection.filter;

import org.drftpd.master.slavemanagement.DummyRemoteSlave;
import org.drftpd.master.slavemanagement.RemoteSlave;
import org.drftpd.master.vfs.DirectoryHandle;
import org.drftpd.slave.exceptions.ObjectNotFoundException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class SlavetopFilterTest {
    private final RemoteSlave[] rslaves = {
            new DummyRemoteSlave("slave1"),
            new DummyRemoteSlave("slave2"),
            new DummyRemoteSlave("slave3")
    };

    private long score(ScoreChart sc, int i) throws ObjectNotFoundException {
        return sc.getScoreForSlave(rslaves[i]).getScore();
    }

    @Test
    public void testTopSlavesGetBonus() throws Exception {
        ScoreChart sc = new ScoreChart(Arrays.asList(rslaves));
        Map<String, Integer> counts = new HashMap<>();
        counts.put("slave1", 5);
        counts.put("slave2", 9);
        counts.put("slave3", 1);
        SlavetopFilter.assignTopSlaves(sc, counts, 2, 100);
        assertEquals(100, score(sc, 0));
        assertEquals(100, score(sc, 1));
        assertEquals(0, score(sc, 2));
    }

    @Test
    public void testSlaveWithNoFilesGetsNoBonus() throws Exception {
        ScoreChart sc = new ScoreChart(Arrays.asList(rslaves));
        Map<String, Integer> counts = new HashMap<>();
        counts.put("slave2", 3);
        SlavetopFilter.assignTopSlaves(sc, counts, 1, 50);
        assertEquals(0, score(sc, 0));
        assertEquals(50, score(sc, 1));
        assertEquals(0, score(sc, 2));
    }

    @Test
    public void testSlaveMissingFromChartIsIgnored() throws Exception {
        ScoreChart sc = new ScoreChart(Arrays.asList(rslaves[0], rslaves[2]));
        Map<String, Integer> counts = new HashMap<>();
        counts.put("slave2", 100); // not in the chart, must not take a top slot
        counts.put("slave1", 2);
        counts.put("slave3", 1);
        SlavetopFilter.assignTopSlaves(sc, counts, 1, 10);
        assertEquals(10, score(sc, 0));
        assertEquals(0, score(sc, 2));
        try {
            sc.getScoreForSlave(rslaves[1]);
            Assertions.fail("slave2 should not be in the chart");
        } catch (ObjectNotFoundException success) {
            // expected
        }
    }

    @Test
    public void testTiesGetBonusUpToTopSlaves() throws Exception {
        ScoreChart sc = new ScoreChart(Arrays.asList(rslaves));
        Map<String, Integer> counts = new HashMap<>();
        counts.put("slave1", 4);
        counts.put("slave2", 4);
        counts.put("slave3", 4);
        SlavetopFilter.assignTopSlaves(sc, counts, 2, 100);
        // which of the tied slaves wins is up to the sort, but exactly two get the bonus
        long total = score(sc, 0) + score(sc, 1) + score(sc, 2);
        assertEquals(200, total);
    }

    @Test
    public void testTieBelowTopIsBroken() throws Exception {
        ScoreChart sc = new ScoreChart(Arrays.asList(rslaves));
        Map<String, Integer> counts = new HashMap<>();
        counts.put("slave1", 10);
        counts.put("slave2", 4);
        counts.put("slave3", 4);
        SlavetopFilter.assignTopSlaves(sc, counts, 2, 100);
        assertEquals(100, score(sc, 0));
        assertEquals(100, score(sc, 1) + score(sc, 2));
    }

    @Test
    public void testAssignZeroRemovesOthers() throws Exception {
        ScoreChart sc = new ScoreChart(Arrays.asList(rslaves));
        Map<String, Integer> counts = new HashMap<>();
        counts.put("slave1", 7);
        counts.put("slave2", 3);
        SlavetopFilter.assignTopSlaves(sc, counts, 1, 0);
        assertEquals(1, sc.getSlaveScores().size());
        assertEquals(rslaves[0], sc.getSlaveScores().get(0).getRSlave());
    }

    @Test
    public void testFindReleaseDirInsideSection() {
        DirectoryHandle base = new DirectoryHandle("/MP3");
        assertEquals(new DirectoryHandle("/MP3/Release"),
                SlavetopFilter.findReleaseDir(new DirectoryHandle("/MP3/Release"), base));
        assertEquals(new DirectoryHandle("/MP3/Release"),
                SlavetopFilter.findReleaseDir(new DirectoryHandle("/MP3/Release/CD1/Sub"), base));
    }

    @Test
    public void testFindReleaseDirOutsideSectionDoesNotCrash() {
        DirectoryHandle base = new DirectoryHandle("/MP3");
        assertNull(SlavetopFilter.findReleaseDir(new DirectoryHandle("/"), base));
        assertNull(SlavetopFilter.findReleaseDir(new DirectoryHandle("/MP3"), base));
        assertNull(SlavetopFilter.findReleaseDir(new DirectoryHandle("/somedir/x"), base));
    }

    @Test
    public void testFindReleaseDirWithRootAsBase() {
        // section-less paths use the root as base directory
        DirectoryHandle root = new DirectoryHandle("/");
        assertEquals(new DirectoryHandle("/somedir"),
                SlavetopFilter.findReleaseDir(new DirectoryHandle("/somedir/x"), root));
        assertNull(SlavetopFilter.findReleaseDir(new DirectoryHandle("/"), root));
    }
}
