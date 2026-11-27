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
package org.drftpd.master.sitebot.commands;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

public class SiteBotManagementTest {

    @Test
    public void testCbcPrefix() {
        assertArrayEquals(new String[]{"secret", "cbc"}, SiteBotManagement.parseBlowKey("cbc:secret"));
        assertArrayEquals(new String[]{"secret", "cbc"}, SiteBotManagement.parseBlowKey("CBC:secret"));
        assertArrayEquals(new String[]{"cbc:x", "cbc"}, SiteBotManagement.parseBlowKey("Cbc:cbc:x"));
        assertArrayEquals(new String[]{"", "cbc"}, SiteBotManagement.parseBlowKey("cbc:"));
    }

    @Test
    public void testNoPrefixIsEcb() {
        assertArrayEquals(new String[]{"secret", null}, SiteBotManagement.parseBlowKey("secret"));
        assertArrayEquals(new String[]{"cbc", null}, SiteBotManagement.parseBlowKey("cbc"));
        assertArrayEquals(new String[]{"xcbc:secret", null}, SiteBotManagement.parseBlowKey("xcbc:secret"));
    }
}
