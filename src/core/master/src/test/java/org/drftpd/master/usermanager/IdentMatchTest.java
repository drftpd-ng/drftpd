/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * DrFTPD is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with DrFTPD; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package org.drftpd.master.usermanager;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class IdentMatchTest {

    private static final String BOT = "DrFTPD-Bot";
    private static final String STORED = "DrFTPD-Bot|nick!~user@host.example.org";

    @Test
    public void testExactIdentMatches() {
        assertTrue(AbstractUserManager.identListContains(STORED, BOT, "nick!~user@host.example.org"));
    }

    @Test
    public void testOneOfSeveralIdents() {
        String list = "OtherBot|other!~o@elsewhere.net," + STORED;
        assertTrue(AbstractUserManager.identListContains(list, BOT, "nick!~user@host.example.org"));
        assertTrue(AbstractUserManager.identListContains(list, "OtherBot", "other!~o@elsewhere.net"));
    }

    @Test
    public void testOtherBotOrIdentDoesNotMatch() {
        assertFalse(AbstractUserManager.identListContains(STORED, "OtherBot", "nick!~user@host.example.org"));
        assertFalse(AbstractUserManager.identListContains(STORED, BOT, "nick!~user@other.example.org"));
        assertFalse(AbstractUserManager.identListContains(STORED, BOT, "nick2!~user@host.example.org"));
    }

    @Test
    public void testIdentIsComparedLiterally() {
        // characters that are special in a regex must not act as a pattern
        assertFalse(AbstractUserManager.identListContains(STORED, BOT, "n[a-z]ck!~user@host.example.org"));
        assertFalse(AbstractUserManager.identListContains(STORED, BOT, "nick!~user@host\\Sexample\\Sorg"));
        assertFalse(AbstractUserManager.identListContains(STORED, BOT, "nick!~user@hostXexampleXorg"));
        assertFalse(AbstractUserManager.identListContains(STORED, BOT, "x|nick!~user@host.example.org"));
        assertFalse(AbstractUserManager.identListContains("DrFTPD-Bot|n[a-z]ck!~u@h", BOT, "nick!~u@h"));
        // and such characters are fine when they are really part of the nick
        assertTrue(AbstractUserManager.identListContains("DrFTPD-Bot|[n]ick^!~user@h", BOT, "[n]ick^!~user@h"));
    }

    @Test
    public void testEmptyOrMissing() {
        assertFalse(AbstractUserManager.identListContains("", BOT, "nick!~user@host.example.org"));
        assertFalse(AbstractUserManager.identListContains(null, BOT, "nick!~user@host.example.org"));
        assertFalse(AbstractUserManager.identListContains(STORED, BOT, null));
    }
}
