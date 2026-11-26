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
import org.drftpd.slave.exceptions.ObjectNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

public class MonthlytrafficFilterTest {
    private static final long MB = 1000L * 1000L;

    @BeforeEach
    public void reset() throws Exception {
        MonthlytrafficFilter.resetNotifications();
    }

    private static class Recording extends MonthlytrafficFilter {
        final List<String> events = new ArrayList<>();

        Recording(Properties p) {
            super(1, p);
        }

        @Override
        void announce(RemoteSlave slave, int level, String message) {
            events.add(slave.getName() + ":" + level);
        }
    }

    private static RemoteSlave slave(String name, long up, long down) {
        RemoteSlave s = new DummyRemoteSlave(name);
        s.setUploadedBytesMonth(up);
        s.setDownloadedBytesMonth(down);
        return s;
    }

    private static boolean has(ScoreChart sc, RemoteSlave s) {
        try {
            sc.getScoreForSlave(s);
            return true;
        } catch (ObjectNotFoundException e) {
            return false;
        }
    }

    private static Properties props(String... kv) {
        Properties p = new Properties();
        for (int i = 0; i < kv.length; i += 2) {
            p.put("1." + kv[i], kv[i + 1]);
        }
        return p;
    }

    private static ScoreChart run(Filter f, RemoteSlave... slaves) throws Exception {
        ScoreChart sc = new ScoreChart(Arrays.asList(slaves));
        f.process(sc, null, null, 'u', null, null);
        return sc;
    }

    @Test
    public void underWarnUntouched() throws Exception {
        RemoteSlave s = slave("s1", 10 * MB, 0);
        ScoreChart sc = run(new Recording(props("limit", "100MB", "warn", "80%")), s);
        assertEquals(0, sc.getScoreForSlave(s).getScore());
    }

    @Test
    public void betweenWarnAndLimitPenalty() throws Exception {
        RemoteSlave s = slave("s1", 90 * MB, 0);
        Recording f = new Recording(props("limit", "100MB", "warn", "80%", "multiplier", "2"));
        ScoreChart sc = run(f, s);
        assertEquals(-2 * 10 * MB, sc.getScoreForSlave(s).getScore());
        assertEquals(List.of("s1:1"), f.events);
    }

    @Test
    public void warnInBytes() throws Exception {
        RemoteSlave s = slave("s1", 60 * MB, 0);
        ScoreChart sc = run(new Recording(props("limit", "100MB", "warn", "50MB")), s);
        assertEquals(-10 * MB, sc.getScoreForSlave(s).getScore());
    }

    @Test
    public void multiplierRemoveRemovesAtWarn() throws Exception {
        RemoteSlave s = slave("s1", 90 * MB, 0);
        ScoreChart sc = run(new Recording(props("limit", "100MB", "warn", "80%", "multiplier", "remove")), s);
        assertFalse(has(sc, s));
    }

    @Test
    public void atAndOverLimitRemoved() throws Exception {
        RemoteSlave at = slave("at", 100 * MB, 0);
        RemoteSlave over = slave("over", 150 * MB, 0);
        RemoteSlave ok = slave("ok", 1 * MB, 0);
        Recording f = new Recording(props("limit", "100MB", "warn", "80%"));
        ScoreChart sc = run(f, at, over, ok);
        assertFalse(has(sc, at));
        assertFalse(has(sc, over));
        assertTrue(has(sc, ok));
        assertEquals(List.of("at:2", "over:2"), f.events);
    }

    @Test
    public void noWarnMeansOnlyHardLimit() throws Exception {
        RemoteSlave s = slave("s1", 99 * MB, 0);
        ScoreChart sc = run(new Recording(props("limit", "100MB")), s);
        assertEquals(0, sc.getScoreForSlave(s).getScore());
    }

    @Test
    public void directionUp() throws Exception {
        RemoteSlave s = slave("s1", 10 * MB, 500 * MB);
        ScoreChart sc = run(new Recording(props("limit", "100MB", "direction", "up")), s);
        assertTrue(has(sc, s));
    }

    @Test
    public void directionDown() throws Exception {
        RemoteSlave s = slave("s1", 10 * MB, 500 * MB);
        ScoreChart sc = run(new Recording(props("limit", "100MB", "direction", "down")), s);
        assertFalse(has(sc, s));
    }

    @Test
    public void directionBothIsSum() throws Exception {
        RemoteSlave s = slave("s1", 60 * MB, 40 * MB);
        assertFalse(has(run(new Recording(props("limit", "100MB", "direction", "both")), s), s));
        RemoteSlave t = slave("t1", 60 * MB, 39 * MB);
        assertTrue(has(run(new Recording(props("limit", "100MB")), t), t));
    }

    @Test
    public void perSlaveLimitOverridesDefaultAndNoLimitUntouched() throws Exception {
        RemoteSlave a = slave("a", 50 * MB, 0);
        RemoteSlave b = slave("b", 50 * MB, 0);
        Recording f = new Recording(props("limit", "100MB", "limit.a", "40MB"));
        ScoreChart sc = run(f, a, b);
        assertFalse(has(sc, a));
        assertTrue(has(sc, b));

        // only a per-slave limit: other slaves are not limited at all
        RemoteSlave c = slave("c", 5000 * MB, 0);
        RemoteSlave d = slave("d", 50 * MB, 0);
        ScoreChart sc2 = run(new Recording(props("limit.d", "40MB")), c, d);
        assertTrue(has(sc2, c));
        assertFalse(has(sc2, d));
    }

    @Test
    public void announcedOnlyOnFirstCrossing() throws Exception {
        RemoteSlave s = slave("s1", 85 * MB, 0);
        Recording f = new Recording(props("limit", "100MB", "warn", "80%"));
        run(f, s);
        run(f, s);
        s.setUploadedBytesMonth(100 * MB);
        run(f, s);
        run(f, s);
        assertEquals(List.of("s1:1", "s1:2"), f.events);

        // new month: counters reset, crossing again announces again
        s.setUploadedBytesMonth(0);
        run(f, s);
        s.setUploadedBytesMonth(90 * MB);
        run(f, s);
        assertEquals(List.of("s1:1", "s1:2", "s1:1"), f.events);
    }

    @Test
    public void invalidConfigRejected() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> new MonthlytrafficFilter(1, props("limit", "abc")));
        assertThrows(IllegalArgumentException.class, () -> new MonthlytrafficFilter(1, props("limit", "1GB", "warn", "150%")));
        assertThrows(IllegalArgumentException.class, () -> new MonthlytrafficFilter(1, props("limit", "1GB", "direction", "sideways")));
        assertThrows(IllegalArgumentException.class, () -> new MonthlytrafficFilter(1, props("limit", "0")));
    }
}
