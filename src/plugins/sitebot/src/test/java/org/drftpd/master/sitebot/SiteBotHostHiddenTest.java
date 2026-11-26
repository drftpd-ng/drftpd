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
import static org.junit.jupiter.api.Assertions.assertNull;

public class SiteBotHostHiddenTest {

    @Test
    public void testHostReplaced() {
        assertEquals("drftpd!~bot@user/drftpd/bot", SiteBot.hostMaskAfterHostHidden("drftpd!~bot@10.0.0.1",
                "drftpd user/drftpd/bot :is now your displayed host"));
    }

    @Test
    public void testUserAndHostReplaced() {
        assertEquals("drftpd!bot@cloak.example", SiteBot.hostMaskAfterHostHidden("drftpd!~bot@10.0.0.1",
                "drftpd bot@cloak.example :is now your hidden host"));
    }

    @Test
    public void testUnreadableReply() {
        assertNull(SiteBot.hostMaskAfterHostHidden(null, "drftpd host :is now your displayed host"));
        assertNull(SiteBot.hostMaskAfterHostHidden("drftpd!~bot@10.0.0.1", "drftpd"));
        assertNull(SiteBot.hostMaskAfterHostHidden("drftpd!~bot@10.0.0.1", "drftpd :is now your displayed host"));
        assertNull(SiteBot.hostMaskAfterHostHidden("nomask", "drftpd host :is now your displayed host"));
    }
}
