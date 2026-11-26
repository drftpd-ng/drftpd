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
package org.drftpd.zipscript.master.announce;

import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

public class UpdateThrottleTest {

    private static UpdateThrottle.Settings s(int files, int percent, int interval) {
        return new UpdateThrottle.Settings(files, percent, interval);
    }

    @Test
    void disabledByDefault() {
        UpdateThrottle.Settings off = UpdateThrottle.Settings.fromProperties(new Properties());
        assertFalse(off.isEnabled());
        assertFalse(new UpdateThrottle().shouldAnnounce(off, "/a", 5, 10, 1000));
    }

    @Test
    void parsesPropertiesAndIgnoresGarbage() {
        Properties p = new Properties();
        p.setProperty("irc.update.files", " 10 ");
        p.setProperty("irc.update.percent", "abc");
        UpdateThrottle.Settings st = UpdateThrottle.Settings.fromProperties(p);
        assertTrue(st.isEnabled());
        assertEquals(60_000L, st._intervalMillis);
        assertEquals(0, st._percent);
        assertEquals(10, st._files);
    }

    @Test
    void everyNFiles() {
        UpdateThrottle t = new UpdateThrottle();
        UpdateThrottle.Settings st = s(5, 0, 0);
        long now = 1_000_000;
        for (int i = 1; i <= 4; i++) {
            assertFalse(t.shouldAnnounce(st, "/r", i, 20, now++));
        }
        assertTrue(t.shouldAnnounce(st, "/r", 5, 20, now++));
        assertFalse(t.shouldAnnounce(st, "/r", 6, 20, now++));
        assertFalse(t.shouldAnnounce(st, "/r", 9, 20, now++));
        assertTrue(t.shouldAnnounce(st, "/r", 10, 20, now++));
    }

    @Test
    void everyNPercent() {
        UpdateThrottle t = new UpdateThrottle();
        UpdateThrottle.Settings st = s(0, 25, 0);
        assertFalse(t.shouldAnnounce(st, "/r", 4, 20, 1000));  // 20%
        assertTrue(t.shouldAnnounce(st, "/r", 5, 20, 1001));   // 25%
        assertFalse(t.shouldAnnounce(st, "/r", 9, 20, 1002));  // 45%
        assertTrue(t.shouldAnnounce(st, "/r", 10, 20, 1003));  // 50%
    }

    @Test
    void intervalThrottlesButKeepsBaseline() {
        UpdateThrottle t = new UpdateThrottle();
        UpdateThrottle.Settings st = s(2, 0, 60);
        assertFalse(t.shouldAnnounce(st, "/r", 1, 50, 1_000));
        assertTrue(t.shouldAnnounce(st, "/r", 2, 50, 2_000));
        // due by file count but inside the interval
        assertFalse(t.shouldAnnounce(st, "/r", 4, 50, 10_000));
        assertFalse(t.shouldAnnounce(st, "/r", 5, 50, 61_999));
        // interval elapsed: the next file fires, once
        assertTrue(t.shouldAnnounce(st, "/r", 6, 50, 62_000));
        assertFalse(t.shouldAnnounce(st, "/r", 7, 50, 62_001));
    }

    @Test
    void neverOnCompleteAndReleasesAreIndependent() {
        UpdateThrottle t = new UpdateThrottle();
        UpdateThrottle.Settings st = s(1, 0, 0);
        assertFalse(t.shouldAnnounce(st, "/r", 10, 10, 1000));
        assertTrue(t.shouldAnnounce(st, "/a", 1, 10, 1000));
        assertTrue(t.shouldAnnounce(st, "/b", 1, 10, 1000));
        assertFalse(t.shouldAnnounce(st, "/a", 1, 10, 1001));
        // complete resets, a rerace starts from scratch
        assertFalse(t.shouldAnnounce(st, "/a", 10, 10, 1002));
        assertTrue(t.shouldAnnounce(st, "/a", 1, 10, 1003));
        assertFalse(t.shouldAnnounce(st, "/x", 1, 0, 1004));
    }

    @Test
    void isDueDoesNotMoveBaselineUntilMarked() {
        UpdateThrottle t = new UpdateThrottle();
        UpdateThrottle.Settings st = s(5, 0, 0);
        assertTrue(t.isDue(st, "/r", 5, 20, 1000));
        // announce could not be sent: the update is still due on the next file
        assertTrue(t.isDue(st, "/r", 6, 20, 1001));
        t.markAnnounced("/r", 6, 20, 1001);
        assertFalse(t.isDue(st, "/r", 7, 20, 1002));
        assertTrue(t.isDue(st, "/r", 11, 20, 1003));
    }

    @Test
    void baselineFollowsDeletes() {
        UpdateThrottle t = new UpdateThrottle();
        UpdateThrottle.Settings st = s(5, 0, 0);
        assertTrue(t.shouldAnnounce(st, "/r", 10, 40, 1000));
        // 6 files deleted: baseline drops to what is present, next 5 files fire again
        assertFalse(t.shouldAnnounce(st, "/r", 4, 40, 1001));
        assertFalse(t.shouldAnnounce(st, "/r", 8, 40, 1002));
        assertTrue(t.shouldAnnounce(st, "/r", 9, 40, 1003));
    }
}
