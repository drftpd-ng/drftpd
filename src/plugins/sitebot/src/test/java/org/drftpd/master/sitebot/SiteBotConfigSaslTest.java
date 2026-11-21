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

import org.drftpd.master.sitebot.config.ServerConfig;
import org.drftpd.master.sitebot.config.SiteBotConfig;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Parsing of the per server SASL settings of irc.conf and the resulting decisions.
 */
public class SiteBotConfigSaslTest {

    private static ServerConfig server(Properties p) {
        return new SiteBotConfig(p).getServer();
    }

    @Test
    public void plaintextRefusedByDefault() {
        Properties p = SiteBotSaslConnectTest.baseProperties(6667, false);
        ServerConfig sc = server(p);
        assertTrue(sc.isSaslEnabled());
        assertFalse(sc.isSsl());
        assertFalse(sc.isSaslPlaintextAllowed());
        assertTrue(sc.isSaslRefusedWithoutTls());
    }

    @Test
    public void plaintextAllowedExplicitly() {
        Properties p = SiteBotSaslConnectTest.baseProperties(6667, true);
        p.setProperty("server.1.sasl.allow_plaintext", " TRUE ");
        ServerConfig sc = server(p);
        assertTrue(sc.isSaslPlaintextAllowed());
        assertFalse(sc.isSaslRefusedWithoutTls());
        assertTrue(sc.isSaslRequired());
    }

    @Test
    public void plaintextAllowFalse() {
        Properties p = SiteBotSaslConnectTest.baseProperties(6667, false);
        p.setProperty("server.1.sasl.allow_plaintext", "false");
        assertTrue(server(p).isSaslRefusedWithoutTls());
    }

    @Test
    public void tlsIsNotRefused() {
        Properties p = SiteBotSaslConnectTest.baseProperties(6697, false);
        p.setProperty("server.1.use_ssl", "true");
        ServerConfig sc = server(p);
        assertTrue(sc.isSaslEnabled());
        assertFalse(sc.isSaslRefusedWithoutTls());
    }

    @Test
    public void noSaslIsNotRefused() {
        // e.g. EFnet without SASL on a plain connection: unchanged behaviour
        Properties p = SiteBotSaslConnectTest.baseProperties(6667, false);
        p.setProperty("server.1.sasl.username", "");
        p.setProperty("server.1.sasl.password", "");
        ServerConfig sc = server(p);
        assertFalse(sc.isSaslEnabled());
        assertFalse(sc.isSaslRefusedWithoutTls());
    }
}
