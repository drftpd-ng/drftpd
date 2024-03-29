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

import org.drftpd.common.util.HostMask;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.util.regex.PatternSyntaxException;

import static org.junit.jupiter.api.Assertions.*;


/**
 * @author zubov
 * @version $Id$
 */
public class HostMaskTest {

    @Test
    public void testMatchesHost() throws Exception {
        HostMask h = new HostMask("*@1.1.1.1");
        assertTrue(h.matchesHost(InetAddress.getByName("1.1.1.1")));
        assertFalse(h.matchesHost(InetAddress.getByName("1.1.1.2")));
        h = new HostMask("1.*.*.*");
        assertTrue(h.matchesHost(InetAddress.getByName("1.2.3.4")));
        assertFalse(h.matchesHost(InetAddress.getByName("2.2.3.4")));
        h = new HostMask("5.*.7.8");
        assertTrue(h.matchesHost(InetAddress.getByName("5.6.7.8")));
        assertFalse(h.matchesHost(InetAddress.getByName("6.6.7.8")));
        h = new HostMask("9.10.*.12");
        assertTrue(h.matchesHost(InetAddress.getByName("9.10.11.12")));
        assertFalse(h.matchesHost(InetAddress.getByName("9.10.11.5")));
    }

    @Test
    public void testMatchesIdent() throws PatternSyntaxException {
        HostMask h = new HostMask("*@1.1.1.1");
        assertTrue(h.matchesIdent(null));
        assertTrue(h.matchesIdent("anything"));
        h = new HostMask("anything@1.1.1.1");
        assertFalse(h.matchesIdent(null));
        assertTrue(h.matchesIdent("anything"));
        assertFalse(h.matchesIdent("nothing"));
    }

    @Test
    public void testEquals() {
        HostMask a = new HostMask("test@1.1.1.1");
        HostMask b = new HostMask("test@1.1.1.1");
        assertEquals(a, b);
        a = new HostMask("*@1.1.1.*");
        b = new HostMask("1.1.1.*");
        assertEquals(a, b);
        a = new HostMask("@1.1.1.*");
        assertEquals(a, b);
        a = new HostMask("notequal@4.2.3.4");
        assertFalse(a.equals(b));
    }
}
