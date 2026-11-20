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

import org.drftpd.common.io.PermissionDeniedException;
import org.drftpd.master.vfs.ObjectNotValidException;
import org.drftpd.slave.exceptions.FileExistsException;
import org.junit.jupiter.api.Test;

import java.io.FileNotFoundException;
import java.util.HashSet;
import java.util.Set;

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

    @Test
    public void testParseChangeArgument() {
        assertEquals("/home/${user}", HomeDirectory.parseChangeArgument("/home/${user}"));
        assertEquals("/My Home/${user}", HomeDirectory.parseChangeArgument("/My Home/${user}"));
        assertEquals("/a b/c d", HomeDirectory.parseChangeArgument(" /a b/c d "));
        assertEquals("", HomeDirectory.parseChangeArgument("none"));
        assertEquals("", HomeDirectory.parseChangeArgument("NONE"));
        assertNull(HomeDirectory.parseChangeArgument(null));
        assertNull(HomeDirectory.parseChangeArgument(""));
        assertNull(HomeDirectory.parseChangeArgument("   "));
        assertNull(HomeDirectory.parseChangeArgument("home dir"));
        assertNull(HomeDirectory.parseChangeArgument("/home/../root dir"));
    }

    @Test
    public void testSpacesInResolvedPath() {
        assertEquals("/My Home/bob", HomeDirectory.resolve("/My Home/${user}", "bob", "users"));
    }

    /**
     * In-memory {@link HomeDirectory.Access}: 'visible' are the directories the user
     * can see, 'hidden' exist but are hidden from the user, 'files' are not directories.
     */
    private static class FakeAccess implements HomeDirectory.Access<String> {
        final Set<String> visible = new HashSet<>();
        final Set<String> hidden = new HashSet<>();
        final Set<String> files = new HashSet<>();
        boolean makeDir;
        boolean concurrentCreate;
        boolean denyOnCreate;
        int created;

        @Override
        public String lookup(String path) throws FileNotFoundException, ObjectNotValidException {
            if (files.contains(path)) {
                throw new ObjectNotValidException(path + " is not a directory");
            }
            if (!visible.contains(path)) {
                throw new FileNotFoundException(path);
            }
            return path;
        }

        @Override
        public boolean exists(String path) {
            return visible.contains(path) || hidden.contains(path) || files.contains(path);
        }

        @Override
        public boolean canMakeDir(String path) {
            return makeDir;
        }

        @Override
        public String create(String path) throws FileExistsException, PermissionDeniedException {
            if (denyOnCreate) {
                throw new PermissionDeniedException("denied");
            }
            if (concurrentCreate) {
                // Another login created it between our lookup and our create
                visible.add(path);
                throw new FileExistsException(path + " already exists");
            }
            created++;
            visible.add(path);
            return path;
        }
    }

    @Test
    public void testLocateExistingDirectory() {
        FakeAccess access = new FakeAccess();
        access.visible.add("/home/bob");
        assertEquals("/home/bob", HomeDirectory.locate("/home/bob", false, "bob", access));
        assertEquals("/home/bob", HomeDirectory.locate("/home/bob", true, "bob", access));
        assertEquals(0, access.created);
    }

    @Test
    public void testLocateMissingWithoutCreate() {
        FakeAccess access = new FakeAccess();
        access.makeDir = true;
        assertNull(HomeDirectory.locate("/home/bob", false, "bob", access));
        assertEquals(0, access.created);
    }

    @Test
    public void testLocateMissingCreatesWithMakeDir() {
        FakeAccess access = new FakeAccess();
        access.makeDir = true;
        assertEquals("/home/bob", HomeDirectory.locate("/home/bob", true, "bob", access));
        assertEquals(1, access.created);
    }

    @Test
    public void testLocateMissingWithoutMakeDir() {
        FakeAccess access = new FakeAccess();
        access.makeDir = false;
        assertNull(HomeDirectory.locate("/home/bob", true, "bob", access));
        assertEquals(0, access.created);
    }

    @Test
    public void testLocatePermissionDeniedOnCreate() {
        FakeAccess access = new FakeAccess();
        access.makeDir = true;
        access.denyOnCreate = true;
        assertNull(HomeDirectory.locate("/home/bob", true, "bob", access));
    }

    @Test
    public void testLocateConcurrentCreate() {
        FakeAccess access = new FakeAccess();
        access.makeDir = true;
        access.concurrentCreate = true;
        assertEquals("/home/bob", HomeDirectory.locate("/home/bob", true, "bob", access));
        assertEquals(0, access.created);
    }

    @Test
    public void testLocateHiddenIsNotTouched() {
        FakeAccess access = new FakeAccess();
        access.makeDir = true;
        access.hidden.add("/home/bob");
        assertNull(HomeDirectory.locate("/home/bob", true, "bob", access));
        assertEquals(0, access.created);
    }

    @Test
    public void testLocateNotADirectory() {
        FakeAccess access = new FakeAccess();
        access.makeDir = true;
        access.files.add("/home/bob");
        assertNull(HomeDirectory.locate("/home/bob", true, "bob", access));
        assertEquals(0, access.created);
    }
}
