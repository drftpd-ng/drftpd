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
package org.drftpd.traffic.master;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DualRuleTest {
    @Test
    void entriesWithinTheCooldownAreKeptEvenAfterAnHour() {
        long now = 10_000_000L;
        long cooldown = 2 * 3600_000L;
        Map<String, Long> fired = new HashMap<>();
        fired.put("recent", now - 3700_000L);
        fired.put("expired", now - cooldown);
        DualRule.purgeExpired(fired, now, cooldown);
        assertTrue(fired.containsKey("recent"));
        assertFalse(fired.containsKey("expired"));
        assertEquals(1, fired.size());
    }
}
