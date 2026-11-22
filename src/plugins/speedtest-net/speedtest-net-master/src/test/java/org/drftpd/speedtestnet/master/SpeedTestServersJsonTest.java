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

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SpeedTestServersJsonTest {

    // Shaped like the speedtest.net js API answer (UNVERIFIED against the live API: no network access).
    // lat/lon/id are strings there, distance is a number (km); both forms are accepted.
    private static final String SAMPLE = "[" +
            "{\"url\":\"http://st1.example.net:8080/speedtest/upload.php\",\"lat\":\"52.3740\",\"lon\":\"4.8897\"," +
            "\"distance\":12,\"name\":\"Amsterdam\",\"country\":\"Netherlands\",\"cc\":\"NL\"," +
            "\"sponsor\":\"Example Net\",\"id\":\"1234\",\"host\":\"st1.example.net:8080\"}," +
            "{\"url\":\"http://st2.example.net:8080/speedtest/upload.php\",\"lat\":50.85,\"lon\":4.35," +
            "\"distance\":173.5,\"name\":\"Brussels\",\"country\":\"Belgium\"," +
            "\"sponsor\":\"Other\",\"id\":5678,\"host\":\"st2.example.net:8080\"}," +
            "{\"name\":\"no url, must be skipped\",\"id\":\"1\"}" +
            "]";

    @Test
    void parsesServersInGivenOrder() {
        List<SpeedTestServer> servers = SpeedTestUtils.parseServersJson(SAMPLE);
        assertEquals(2, servers.size());

        SpeedTestServer first = servers.get(0);
        assertEquals(1234, first.getId());
        assertEquals("http://st1.example.net:8080/speedtest/upload.php", first.getUrl());
        assertEquals("st1.example.net:8080", first.getHost());
        assertEquals("Amsterdam", first.getName());
        assertEquals("Netherlands", first.getCountry());
        assertEquals("NL", first.getCc());
        assertEquals("Example Net", first.getSponsor());
        assertEquals(52.3740, first.getLatitude(), 1e-9);
        assertEquals(4.8897, first.getLongitude(), 1e-9);
        assertEquals(12.0, first.getDistance(), 1e-9);

        assertEquals(5678, servers.get(1).getId());
        assertEquals(173.5, servers.get(1).getDistance(), 1e-9);
    }

    @Test
    void rejectsGarbage() {
        assertTrue(SpeedTestUtils.parseServersJson(null).isEmpty());
        assertTrue(SpeedTestUtils.parseServersJson("").isEmpty());
        assertTrue(SpeedTestUtils.parseServersJson("<html>blocked</html>").isEmpty());
        assertTrue(SpeedTestUtils.parseServersJson("{\"error\":\"x\"}").isEmpty());
    }
}
