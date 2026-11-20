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
package org.drftpd.master.commands.login;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class HomeDirectoryTest {

    @Test
    public void testEmptyTemplateDisablesFeature() {
        assertNull(HomeDirectory.resolve(null, "bob", "users"));
        assertNull(HomeDirectory.resolve("", "bob", "users"));
        assertNull(HomeDirectory.resolve("   ", "bob", "users"));
    }

    @Test
    public void testPlaceholders() {
        assertEquals("/home/bob", HomeDirectory.resolve("/home/${user}", "bob", "users"));
        assertEquals("/groups/users/bob", HomeDirectory.resolve("/groups/${group}/${user}", "bob", "users"));
        assertEquals("/home/bob-bob", HomeDirectory.resolve("/home/${user}-${user}", "bob", "users"));
        assertEquals("/static/dir", HomeDirectory.resolve("/static/dir", "bob", null));
        assertEquals("/home/bob", HomeDirectory.resolve(" /home/${user}/ ", "bob", "users"));
        assertEquals("/", HomeDirectory.resolve("/", "bob", "users"));
    }

    @Test
    public void testMissingGroupRejected() {
        assertNull(HomeDirectory.resolve("/groups/${group}", "bob", null));
        assertNull(HomeDirectory.resolve("/groups/${group}", "bob", ""));
    }

    @Test
    public void testUnknownPlaceholderRejected() {
        assertNull(HomeDirectory.resolve("/home/${nick}", "bob", "users"));
    }

    @Test
    public void testRelativePathRejected() {
        assertNull(HomeDirectory.resolve("home/${user}", "bob", "users"));
        assertNull(HomeDirectory.resolve("${user}", "bob", "users"));
    }

    @Test
    public void testTraversalInTemplateRejected() {
        assertNull(HomeDirectory.resolve("/home/../${user}", "bob", "users"));
        assertNull(HomeDirectory.resolve("/home/./${user}", "bob", "users"));
        assertNull(HomeDirectory.resolve("/home//${user}", "bob", "users"));
        assertNull(HomeDirectory.resolve("/home\\${user}", "bob", "users"));
    }

    @Test
    public void testCraftedUserOrGroupRejected() {
        assertNull(HomeDirectory.resolve("/home/${user}", "..", "users"));
        assertNull(HomeDirectory.resolve("/home/${user}", ".", "users"));
        assertNull(HomeDirectory.resolve("/home/${user}", "../etc", "users"));
        assertNull(HomeDirectory.resolve("/home/${user}", "a/b", "users"));
        assertNull(HomeDirectory.resolve("/home/${user}", "a\\b", "users"));
        assertNull(HomeDirectory.resolve("/home/${user}", "a\nb", "users"));
        assertNull(HomeDirectory.resolve("/home/${user}", "", "users"));
        assertNull(HomeDirectory.resolve("/home/${user}", null, "users"));
        assertNull(HomeDirectory.resolve("/groups/${group}", "bob", ".."));
        assertNull(HomeDirectory.resolve("/groups/${group}", "bob", "x/../.."));
        // A user named like a placeholder must not be expanded again
        assertNull(HomeDirectory.resolve("/home/${user}", "${nick}", "users"));
        assertNull(HomeDirectory.resolve("/home/${user}", "${group}", "users"));
    }

    @Test
    public void testIsValidTemplate() {
        assertTrue(HomeDirectory.isValidTemplate("/home/${user}"));
        assertTrue(HomeDirectory.isValidTemplate("/groups/${group}"));
        assertFalse(HomeDirectory.isValidTemplate("home/${user}"));
        assertFalse(HomeDirectory.isValidTemplate("/home/../root"));
        assertFalse(HomeDirectory.isValidTemplate(""));
    }
}
