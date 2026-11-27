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

import org.drftpd.master.sitebot.config.ChannelConfig;
import org.drftpd.master.sitebot.config.SiteBotConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Blowfish settings of irc.conf
 */
public class SiteBotConfigBlowfishTest {

    private static List<ChannelConfig> channels(String... keyModes) {
        Properties p = SiteBotSaslConnectTest.baseProperties(6667, false);
        for (int i = 0; i < keyModes.length; i += 2) {
            int n = i / 2 + 1;
            p.setProperty("channel." + n, "#chan" + n);
            p.setProperty("channel." + n + ".perms", "*");
            p.setProperty("channel." + n + ".blowkey", keyModes[i]);
            if (keyModes[i + 1] != null) {
                p.setProperty("channel." + n + ".blowmode", keyModes[i + 1]);
            }
        }
        return new SiteBotConfig(p).getChannels();
    }

    @Test
    public void testCbcPrefixOnChannelKey() {
        List<ChannelConfig> chans = channels("cbc:secret", "cbc", "CBC:secret", null, "cbc:secret", "ecb", "plain", "ecb");
        assertEquals("secret", chans.get(0).getBlowKey());
        assertEquals("cbc", chans.get(0).getBlowMode());
        assertEquals("secret", chans.get(1).getBlowKey());
        assertEquals("cbc", chans.get(1).getBlowMode());
        // the prefix wins over blowmode=ecb
        assertEquals("secret", chans.get(2).getBlowKey());
        assertEquals("cbc", chans.get(2).getBlowMode());
        assertEquals("plain", chans.get(3).getBlowKey());
        assertEquals("ecb", chans.get(3).getBlowMode());
    }

    @Test
    public void testEcbFallbackDefaultsOff() {
        Properties p = SiteBotSaslConnectTest.baseProperties(6667, false);
        assertFalse(new SiteBotConfig(p).getBlowfishEcbFallback());
        p.setProperty("blowfish.ecb.fallback", "true");
        assertTrue(new SiteBotConfig(p).getBlowfishEcbFallback());
    }
}
