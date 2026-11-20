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
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the recursive part of the wipe permission check (issue #253).
 */
public class WipeTreeCheckTest {

    private static final Map<String, List<String>> TREE = Map.of(
            "/A", List.of("/A/x", "/A/protected", "/A/y"),
            "/A/protected", List.of("/A/protected/file"),
            "/A/y", List.of("/A/y/deep"),
            "/A/y/deep", List.of("/A/y/deep/leaf"));

    private static List<String> kids(String node) {
        return TREE.getOrDefault(node, List.of());
    }

    @Test
    void nothingDeniedReturnsNull() {
        assertNull(WipeTreeCheck.findFirstDenied("/A", WipeTreeCheckTest::kids, n -> false));
    }

    @Test
    void deniedDirectChildIsFound() {
        assertEquals("/A/protected",
                WipeTreeCheck.findFirstDenied("/A", WipeTreeCheckTest::kids, n -> n.equals("/A/protected")));
    }

    @Test
    void deniedDeepDescendantIsFound() {
        assertEquals("/A/y/deep/leaf",
                WipeTreeCheck.findFirstDenied("/A", WipeTreeCheckTest::kids, n -> n.endsWith("leaf")));
    }

    @Test
    void rootItselfIsNotTested() {
        assertNull(WipeTreeCheck.findFirstDenied("/A", WipeTreeCheckTest::kids, n -> n.equals("/A")));
    }

    @Test
    void firstDeniedFollowsTreeOrder() {
        // /A/protected/file comes before /A/y/deep in depth first order
        assertEquals("/A/protected/file", WipeTreeCheck.findFirstDenied("/A", WipeTreeCheckTest::kids,
                n -> n.equals("/A/protected/file") || n.equals("/A/y/deep")));
    }

    @Test
    void emptyTreeIsAllowed() {
        assertNull(WipeTreeCheck.findFirstDenied("/empty", WipeTreeCheckTest::kids, n -> true));
    }

    @Test
    void stopsAtFirstDeniedWithoutTestingTheRest() {
        int[] calls = {0};
        WipeTreeCheck.findFirstDenied("/A", WipeTreeCheckTest::kids, n -> {
            calls[0]++;
            return n.equals("/A/x");
        });
        assertEquals(1, calls[0]);
    }

    @Test
    void worksWithRealWipeRules() throws Exception {
        VFSPermissions perms = new VFSPermissions();
        assertFalse(perms.hasPathPermissions("wipe"));
        perms.addPermissionToMap("wipe",
                new GlobPathPermission("/A/protected*", Permission.makeUsers(new StringTokenizer("!*"))));
        assertTrue(perms.hasPathPermissions("wipe"));
        assertFalse(perms.hasPathPermissions("delete"));
        assertThrows(IllegalArgumentException.class, () -> perms.hasPathPermissions("bogus"));
    }
}
