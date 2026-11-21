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
package org.drftpd.speedtestnet.master;

import org.junit.jupiter.api.Test;

import static org.drftpd.speedtestnet.master.SpeedTestSelection.Source.FULL_LIST;
import static org.drftpd.speedtestnet.master.SpeedTestSelection.Source.SLAVE_NEAREST;
import static org.drftpd.speedtestnet.master.SpeedTestSelection.decide;
import static org.junit.jupiter.api.Assertions.*;

class SpeedTestSelectionTest {

    @Test
    void globalModeParsing() {
        assertTrue(SpeedTestSelection.isSlaveMode("slave"));
        assertTrue(SpeedTestSelection.isSlaveMode(" SLAVE "));
        assertFalse(SpeedTestSelection.isSlaveMode("geo"));
        assertFalse(SpeedTestSelection.isSlaveMode(""));
        assertFalse(SpeedTestSelection.isSlaveMode(null));
        assertFalse(SpeedTestSelection.isSlaveMode("bogus"));
    }

    @Test
    void defaultIsGeo() {
        assertEquals(FULL_LIST, decide(false, null, 0));
        assertEquals(FULL_LIST, decide(false, "", 0));
    }

    @Test
    void globalSlaveModeAppliesToAllSlaves() {
        assertEquals(SLAVE_NEAREST, decide(true, null, 0));
        assertEquals(SLAVE_NEAREST, decide(true, "", 0));
        assertEquals(SLAVE_NEAREST, decide(true, "garbage", 0));
    }

    @Test
    void perSlaveSettingOptsIn() {
        assertEquals(SLAVE_NEAREST, decide(false, "slave", 0));
        assertEquals(SLAVE_NEAREST, decide(false, " Slave ", 0));
    }

    @Test
    void perSlaveSettingOptsOut() {
        assertEquals(FULL_LIST, decide(true, "geo", 0));
    }

    @Test
    void unknownSettingDetection() {
        assertTrue(SpeedTestSelection.isKnownSetting(null));
        assertTrue(SpeedTestSelection.isKnownSetting(""));
        assertTrue(SpeedTestSelection.isKnownSetting("  "));
        assertTrue(SpeedTestSelection.isKnownSetting("slave"));
        assertTrue(SpeedTestSelection.isKnownSetting(" GEO "));
        assertFalse(SpeedTestSelection.isKnownSetting("slaves"));
        assertFalse(SpeedTestSelection.isKnownSetting("bogus"));
    }

    @Test
    void unknownSettingFollowsGlobal() {
        assertEquals(FULL_LIST, decide(false, "slaves", 0));
        assertEquals(SLAVE_NEAREST, decide(true, "slaves", 0));
    }

    @Test
    void perSlaveDecisionWithGlobalGeoOnlyNeedsFullListForGeoSlaves() {
        // Global geo: only slaves that resolve to FULL_LIST need the downloaded list
        assertEquals(SLAVE_NEAREST, decide(false, "slave", 0));
        assertEquals(FULL_LIST, decide(false, null, 0));
        assertEquals(FULL_LIST, decide(false, "geo", 0));
    }

    @Test
    void requestedServerIdAlwaysUsesFullList() {
        assertEquals(FULL_LIST, decide(false, null, 1234));
        assertEquals(FULL_LIST, decide(true, null, 1234));
        assertEquals(FULL_LIST, decide(false, "slave", 1234));
        assertEquals(FULL_LIST, decide(true, "geo", 1234));
    }
}
