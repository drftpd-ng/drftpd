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
package org.drftpd.common;

import org.drftpd.common.network.InetAddressUtils;
import org.junit.jupiter.api.Test;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class InetAddressUtilsTest {

    private static String shortForm(String literal) throws Exception {
        return InetAddressUtils.toShortString(InetAddress.getByName(literal));
    }

    @Test
    public void testShortForm() throws Exception {
        assertEquals("::1", shortForm("0:0:0:0:0:0:0:1"));
        assertEquals("::", shortForm("0:0:0:0:0:0:0:0"));
        assertEquals("2001:db8::1", shortForm("2001:0DB8:0000:0000:0000:0000:0000:0001"));
        // Longest run of zeros is compressed; on a tie, the first one
        assertEquals("1:0:0:2::3", shortForm("1:0:0:2:0:0:0:3"));
        assertEquals("2001:db8::1:0:0:1", shortForm("2001:db8:0:0:1:0:0:1"));
        // A single zero group is not compressed (RFC 5952)
        assertEquals("2001:db8:0:1:1:1:1:1", shortForm("2001:db8:0:1:1:1:1:1"));
        assertEquals("fe80::1", shortForm("fe80:0:0:0:0:0:0:1"));
        // IPv4 is unchanged
        assertEquals("192.0.2.1", shortForm("192.0.2.1"));
    }

    @Test
    public void testPrivateAddresses() throws Exception {
        assertTrue(InetAddressUtils.isPrivateAddress(InetAddress.getByName("192.168.1.1")));
        assertTrue(InetAddressUtils.isPrivateAddress(InetAddress.getByName("10.0.0.1")));
        assertTrue(InetAddressUtils.isPrivateAddress(InetAddress.getByName("fd12:3456::1")));   // unique local
        assertTrue(InetAddressUtils.isPrivateAddress(InetAddress.getByName("fc00::1")));        // unique local
        assertTrue(InetAddressUtils.isPrivateAddress(InetAddress.getByName("fe80::1")));        // link-local
        assertFalse(InetAddressUtils.isPrivateAddress(InetAddress.getByName("2001:db8::1")));
        assertFalse(InetAddressUtils.isPrivateAddress(InetAddress.getByName("192.0.2.1")));
    }

    @Test
    public void testParseLiteralRejectsHostNames() {
        // Host names must never be resolved from client input
        assertThrows(IllegalArgumentException.class, () -> InetAddressUtils.parseLiteral("localhost"));
        assertThrows(IllegalArgumentException.class, () -> InetAddressUtils.parseLiteral("example.com"));
        assertThrows(IllegalArgumentException.class, () -> InetAddressUtils.parseLiteral("256.1.1.1"));
        assertThrows(IllegalArgumentException.class, () -> InetAddressUtils.parseLiteral("1.2.3"));
        assertThrows(IllegalArgumentException.class, () -> InetAddressUtils.parseLiteral(""));
        assertTrue(InetAddressUtils.parseLiteral("::1") instanceof Inet6Address);
        assertTrue(InetAddressUtils.parseLiteral("192.0.2.1") instanceof Inet4Address);
    }

    @Test
    public void testParseEprt() throws Exception {
        assertEquals(new InetSocketAddress(InetAddress.getByName("132.235.1.2"), 6275),
                InetAddressUtils.parseEprt("|1|132.235.1.2|6275|"));
        assertEquals(new InetSocketAddress(InetAddress.getByName("1080::8:800:200C:417A"), 5282),
                InetAddressUtils.parseEprt("|2|1080::8:800:200C:417A|5282|"));
        // The command from #19
        assertEquals(new InetSocketAddress(InetAddress.getByName("::1"), 1838),
                InetAddressUtils.parseEprt("|2|::1|1838|"));
        // Any printable delimiter is allowed
        assertEquals(new InetSocketAddress(InetAddress.getByName("::1"), 21),
                InetAddressUtils.parseEprt("!2!::1!21!"));
    }

    @Test
    public void testParseEprtErrors() {
        assertThrows(InetAddressUtils.UnsupportedNetworkProtocolException.class,
                () -> InetAddressUtils.parseEprt("|3|::1|21|"));
        assertThrows(IllegalArgumentException.class, () -> InetAddressUtils.parseEprt("|1|::1|21|"));
        assertThrows(IllegalArgumentException.class, () -> InetAddressUtils.parseEprt("|2|1.2.3.4|21|"));
        assertThrows(IllegalArgumentException.class, () -> InetAddressUtils.parseEprt("|2|::1|0|"));
        assertThrows(IllegalArgumentException.class, () -> InetAddressUtils.parseEprt("|2|::1|65536|"));
        assertThrows(IllegalArgumentException.class, () -> InetAddressUtils.parseEprt("|2|::1|21"));
        assertThrows(IllegalArgumentException.class, () -> InetAddressUtils.parseEprt("|2|example.com|21|"));
        assertThrows(IllegalArgumentException.class, () -> InetAddressUtils.parseEprt("|2|::1|21|x|"));
        assertThrows(IllegalArgumentException.class, () -> InetAddressUtils.parseEprt(null));
    }

    @Test
    public void testSplitHostPort() {
        assertArrayEquals(new String[]{"192.0.2.1", "5000"}, InetAddressUtils.splitHostPort("192.0.2.1:5000"));
        assertArrayEquals(new String[]{"2001:db8::1", "5000"}, InetAddressUtils.splitHostPort("2001:db8::1:5000"));
        assertArrayEquals(new String[]{"2001:db8::1", "5000"}, InetAddressUtils.splitHostPort("[2001:db8::1]:5000"));
        assertThrows(IllegalArgumentException.class, () -> InetAddressUtils.splitHostPort("192.0.2.1"));
        assertThrows(IllegalArgumentException.class, () -> InetAddressUtils.splitHostPort("192.0.2.1:"));
    }

    @Test
    public void testEpsvReply() {
        assertEquals("Entering Extended Passive Mode (|||6446|)", InetAddressUtils.formatEpsvReply(6446));
    }

    @Test
    public void testAddressFamily() throws Exception {
        assertEquals(InetAddressUtils.AF_INET, InetAddressUtils.getAddressFamily(InetAddress.getByName("192.0.2.1")));
        assertEquals(InetAddressUtils.AF_INET6, InetAddressUtils.getAddressFamily(InetAddress.getByName("::1")));
    }
}
