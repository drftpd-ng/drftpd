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
package org.drftpd.master.sitebot;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SiteBotSaslBackoffTest {

    private static final long HOUR = 60L * 60L * 1000L;

    @Test
    public void firstFailureWaitsConnectDelay() {
        assertEquals(30_000L, SiteBot.saslRetryDelay(30_000L, 1));
    }

    @Test
    public void doublesForEveryConsecutiveFailure() {
        assertEquals(60_000L, SiteBot.saslRetryDelay(30_000L, 2));
        assertEquals(120_000L, SiteBot.saslRetryDelay(30_000L, 3));
        assertEquals(240_000L, SiteBot.saslRetryDelay(30_000L, 4));
        assertEquals(1_920_000L, SiteBot.saslRetryDelay(30_000L, 7));
    }

    @Test
    public void cappedAtOneHour() {
        assertEquals(HOUR, SiteBot.saslRetryDelay(30_000L, 8));
        assertEquals(HOUR, SiteBot.saslRetryDelay(30_000L, 50));
        assertEquals(HOUR, SiteBot.saslRetryDelay(30_000L, 63));
        assertEquals(HOUR, SiteBot.saslRetryDelay(30_000L, 64));
        assertEquals(HOUR, SiteBot.saslRetryDelay(30_000L, Integer.MAX_VALUE));
    }

    @Test
    public void zeroConnectDelayUsesMinimumBase() {
        assertEquals(1_000L, SiteBot.saslRetryDelay(0L, 1));
        assertEquals(2_000L, SiteBot.saslRetryDelay(0L, 2));
        assertEquals(1_000L, SiteBot.saslRetryDelay(-5L, 1));
    }

    @Test
    public void neverBelowConnectDelay() {
        // a connect.delay above the cap is kept as is
        assertEquals(2 * HOUR, SiteBot.saslRetryDelay(2 * HOUR, 1));
        assertEquals(2 * HOUR, SiteBot.saslRetryDelay(2 * HOUR, 10));
    }

    @Test
    public void nonPositiveFailureCountUsesBase() {
        assertEquals(30_000L, SiteBot.saslRetryDelay(30_000L, 0));
        assertEquals(30_000L, SiteBot.saslRetryDelay(30_000L, -1));
    }
}
