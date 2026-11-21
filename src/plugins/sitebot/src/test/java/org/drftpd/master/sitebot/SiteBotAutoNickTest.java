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

public class SiteBotAutoNickTest {

    @Test
    public void testNickFromNickInUse() {
        assertEquals("test-drft",
                SiteBot.getNickFromNickInUse(":irc.example.net 433 * test-drft :Nickname is already in use"));
        assertEquals("bot2",
                SiteBot.getNickFromNickInUse(":irc.example.net 433 bot bot2 :Nickname is already in use"));
        assertNull(SiteBot.getNickFromNickInUse(":irc.example.net 433 *"));
    }

    @Test
    public void testAutoNickWithoutKnownLimit() {
        // Unchanged behaviour while the server hasn't truncated anything
        assertEquals("test-drftpd2", SiteBot.getAutoNick("test-drftpd", 2, 0));
        assertEquals("bot10", SiteBot.getAutoNick("bot", 10, 0));
    }

    @Test
    public void testAutoNickFitsNickLen() {
        // NICKLEN=9: "test-drftpd2" would be truncated back to the taken "test-drft"
        assertEquals("test-drf2", SiteBot.getAutoNick("test-drftpd", 2, 9));
        assertEquals("test-dr10", SiteBot.getAutoNick("test-drftpd", 10, 9));
        // A short nick that already fits is left alone
        assertEquals("bot2", SiteBot.getAutoNick("bot", 2, 9));
    }

    @Test
    public void testAutoNickWithTinyLimitKeepsOneCharacter() {
        assertEquals("t12", SiteBot.getAutoNick("test", 12, 2));
    }
}
