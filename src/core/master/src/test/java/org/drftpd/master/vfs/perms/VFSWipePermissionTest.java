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
package org.drftpd.master.vfs.perms;

import org.drftpd.master.permissions.GlobPathPermission;
import org.drftpd.master.permissions.Permission;
import org.drftpd.master.permissions.RegexPathPermission;
import org.drftpd.master.tests.DummyUser;
import org.drftpd.master.usermanager.User;
import org.drftpd.master.vfs.DirectoryHandle;
import org.drftpd.master.vfs.FileHandle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.StringTokenizer;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the 'wipe' / 'wipeex' path permissions (issue #253).
 * The wipe check is done with defaults=true: no matching line means allowed.
 */
public class VFSWipePermissionTest {

    private VFSPermissions _perms;
    private User _siteop;
    private User _user;

    private static User makeUser(String name, String... groups) {
        Set<String> groupSet = Set.of(groups);
        return new DummyUser(name) {
            @Override
            public boolean isMemberOf(String group) {
                return groupSet.contains(group);
            }
        };
    }

    private void addWipe(String pattern, String users) throws Exception {
        _perms.addPermissionToMap("wipe",
                new GlobPathPermission(pattern, Permission.makeUsers(new StringTokenizer(users))));
    }

    private boolean canWipe(User user, String path, boolean dir) {
        return _perms.checkPathPermission("wipe", user,
                dir ? new DirectoryHandle(path) : new FileHandle(path), true);
    }

    @BeforeEach
    void setUp() {
        _perms = new VFSPermissions();
        _siteop = makeUser("op", "siteop");
        _user = makeUser("joe", "users");
    }

    @Test
    public void testNoWipeLineAllows() {
        assertTrue(canWipe(_user, "/archive/mp3", true));
        assertTrue(canWipe(_siteop, "/incoming/release", true));
        assertTrue(canWipe(_user, "/incoming/release/file.rar", false));
    }

    @Test
    public void testDenyAllInArchive() throws Exception {
        addWipe("/archive/*", "!*");
        assertFalse(canWipe(_user, "/archive/x", true));
        assertFalse(canWipe(_siteop, "/archive/x", true));
        assertFalse(canWipe(_siteop, "/archive/x/file.rar", false));
        // no line matches outside /archive: allowed
        assertTrue(canWipe(_user, "/incoming/x", true));
        assertTrue(canWipe(_siteop, "/incoming/x/file.rar", false));
    }

    @Test
    public void testGroupLine() throws Exception {
        addWipe("*", "=siteop =wipe");
        assertTrue(canWipe(_siteop, "/incoming/x", true));
        assertTrue(canWipe(makeUser("nuker", "wipe"), "/incoming/x", true));
        assertFalse(canWipe(_user, "/incoming/x", true));
    }

    @Test
    public void testDocumentedArchiveExample() throws Exception {
        // the example from perms.conf.dist, first match wins
        addWipe("/archive/*/?*", "=siteop =wipe");
        addWipe("/archive/*", "!*");
        addWipe("*", "=siteop =wipe");

        // /archive itself and the section dirs cannot be wiped
        assertFalse(canWipe(_siteop, "/archive", true));
        assertFalse(canWipe(_siteop, "/archive/mp3", true));
        // releases (and files) inside a section can be wiped by siteops
        assertTrue(canWipe(_siteop, "/archive/mp3/Some.Release-GRP", true));
        assertTrue(canWipe(_siteop, "/archive/mp3/file.rar", false));
        assertFalse(canWipe(_user, "/archive/mp3/Some.Release-GRP", true));
        // elsewhere
        assertTrue(canWipe(_siteop, "/incoming/Some.Release-GRP", true));
        assertFalse(canWipe(_user, "/incoming/Some.Release-GRP", true));
    }

    @Test
    public void testRegexVariant() {
        _perms.addPermissionToMap("wipeex",
                new RegexPathPermission(Pattern.compile("^/[^/]+$", Pattern.CASE_INSENSITIVE),
                        Permission.makeUsers(new StringTokenizer("!*"))));
        assertFalse(canWipe(_siteop, "/mp3", true));
        assertTrue(canWipe(_siteop, "/mp3/Some.Release-GRP", true));
    }
}
