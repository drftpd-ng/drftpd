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
package org.drftpd.master.commands.usermanagement.ipsecurity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class IpSecurityManagerTest {

    @Test
    public void testFullAddressesFixAllGroups() {
        assertEquals(8, IpSecurityManager.getIPv6FixedGroups("2001:db8::1"));
        assertEquals(8, IpSecurityManager.getIPv6FixedGroups("::1"));
        assertEquals(8, IpSecurityManager.getIPv6FixedGroups("0:0:0:0:0:0:0:1"));
    }

    @Test
    public void testPrefixes() {
        assertEquals(4, IpSecurityManager.getIPv6FixedGroups("2001:db8:1:2:*"));
        assertEquals(2, IpSecurityManager.getIPv6FixedGroups("2001:db8:*"));
        assertEquals(7, IpSecurityManager.getIPv6FixedGroups("1:2:3:4:5:6:7:*"));
    }

    @Test
    public void testInvalidMasks() {
        // "::" before the "*" makes the number of groups ambiguous
        assertEquals(-1, IpSecurityManager.getIPv6FixedGroups("2001:db8::*"));
        // Only a trailing "*" is allowed
        assertEquals(-1, IpSecurityManager.getIPv6FixedGroups("2001:*:1:2:*"));
        assertEquals(-1, IpSecurityManager.getIPv6FixedGroups("2001:db8:1:2?"));
        assertEquals(-1, IpSecurityManager.getIPv6FixedGroups("*"));
        assertEquals(-1, IpSecurityManager.getIPv6FixedGroups("1:2:3:4:5:6:7:8:*"));
        assertEquals(-1, IpSecurityManager.getIPv6FixedGroups("2001:xyz:*"));
        // IPv4 is handled by the ipsecurity rules
        assertEquals(-1, IpSecurityManager.getIPv6FixedGroups("192.0.2.1"));
    }
}
