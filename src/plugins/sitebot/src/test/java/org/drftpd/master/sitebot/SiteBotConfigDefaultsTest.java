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

import org.drftpd.master.sitebot.config.SiteBotConfig;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Defaults of irc.conf keys that are missing, matching irc.conf.dist.
 */
public class SiteBotConfigDefaultsTest {

    @Test
    public void emptyConfigUsesDistDefaults() {
        SiteBotConfig cfg = new SiteBotConfig(new Properties());
        assertTrue(cfg.getAutoNick());
        assertEquals(30000L, cfg.getConnectDelay());
        assertEquals(300L, cfg.getMessageDelay());
        assertEquals(5, cfg.getCommandsMax());
        assertFalse(cfg.getCommandsBlock());
        assertTrue(cfg.getCommandsQueue());
        assertEquals(512, cfg.getMaxLineLength());
        assertTrue(cfg.getChannelAutoJoin());
        assertTrue(cfg.getBlowfishEnabled());
        assertFalse(cfg.getNickservEnabled());
    }

    @Test
    public void commandsFullBlock() {
        Properties p = new Properties();
        p.setProperty("commands.full", " Block ");
        SiteBotConfig cfg = new SiteBotConfig(p);
        assertTrue(cfg.getCommandsBlock());
        assertFalse(cfg.getCommandsQueue());
    }

    @Test
    public void valuesAreTrimmed() {
        Properties p = new Properties();
        p.setProperty("commands.max", "7 ");
        p.setProperty("nick.auto", "false ");
        SiteBotConfig cfg = new SiteBotConfig(p);
        assertEquals(7, cfg.getCommandsMax());
        assertFalse(cfg.getAutoNick());
    }

    @Test
    public void missingServerPortNamesTheKey() {
        Properties p = new Properties();
        p.setProperty("server.1.host", "irc.example.org");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> new SiteBotConfig(p));
        assertTrue(e.getMessage().contains("server.1.port"));
    }
}
