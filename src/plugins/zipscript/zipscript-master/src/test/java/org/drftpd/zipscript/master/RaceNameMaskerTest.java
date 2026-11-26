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

package org.drftpd.zipscript.master;

import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RaceNameMaskerTest {

    private static Properties props(String... kv) {
        Properties p = new Properties();
        for (int i = 0; i < kv.length; i += 2) {
            p.setProperty(kv[i], kv[i + 1]);
        }
        return p;
    }

    @Test
    void offByDefaultReturnsEverythingUnchanged() {
        RaceNameMasker m = new RaceNameMasker(new Properties());
        assertEquals("bob", m.user("bob", "PRE", "MP3"));
        assertEquals("PRE", m.group("PRE", "MP3"));
        assertEquals("PRE", m.groupOf("bob", "PRE", "MP3"));
    }

    @Test
    void listsIgnoredWhenNotEnabled() {
        RaceNameMasker m = new RaceNameMasker(props("hideuser.groups", "PRE", "hideuser.users", "bob"));
        assertEquals("bob", m.user("bob", "PRE", "MP3"));
        assertEquals("PRE", m.group("PRE", "MP3"));
    }

    @Test
    void enabledWithoutListsChangesNothing() {
        RaceNameMasker m = new RaceNameMasker(props("hideuser.enabled", "true"));
        assertEquals("bob", m.user("bob", "PRE", null));
        assertEquals("PRE", m.group("PRE", null));
    }

    @Test
    void hidesMembersOfListedGroups() {
        RaceNameMasker m = new RaceNameMasker(props("hideuser.enabled", "true", "hideuser.groups", "PRE, Other"));
        assertEquals("Anonymous", m.user("bob", "pre", "MP3"));
        assertEquals("Anonymous", m.user("al", "OTHER", "MP3"));
        assertEquals("carol", m.user("carol", "SiteOps", "MP3"));
        assertEquals("Anonymous", m.group("PRE", "MP3"));
        assertEquals("SiteOps", m.group("SiteOps", "MP3"));
        assertEquals("Anonymous", m.groupOf("bob", "PRE", "MP3"));
    }

    @Test
    void hidesListedUsersAndTheirGroupColumn() {
        RaceNameMasker m = new RaceNameMasker(props("hideuser.enabled", "true", "hideuser.users", "Bob dave",
                "hideuser.placeholder", "Hidden"));
        assertEquals("Hidden", m.user("bob", "SiteOps", null));
        assertEquals("Hidden", m.user("Dave", "", null));
        assertEquals("carol", m.user("carol", "SiteOps", null));
        // the group itself is not listed, but next to a hidden user it is hidden
        assertEquals("SiteOps", m.group("SiteOps", null));
        assertEquals("Hidden", m.groupOf("bob", "SiteOps", null));
        assertEquals("SiteOps", m.groupOf("carol", "SiteOps", null));
        assertEquals("", m.groupOf("bob", "", null));
    }

    @Test
    void separateGroupPlaceholder() {
        RaceNameMasker m = new RaceNameMasker(props("hideuser.enabled", "true", "hideuser.groups", "PRE",
                "hideuser.placeholder", "Anon", "hideuser.group.placeholder", "Affil"));
        assertEquals("Anon", m.user("bob", "PRE", null));
        assertEquals("Affil", m.group("PRE", null));
    }

    @Test
    void sectionScoping() {
        RaceNameMasker m = new RaceNameMasker(props("hideuser.enabled", "true", "hideuser.groups", "PRE",
                "hideuser.sections", "MP3 0DAY"));
        assertEquals("Anonymous", m.user("bob", "PRE", "mp3"));
        assertEquals("Anonymous", m.user("bob", "PRE", "0DAY"));
        assertEquals("bob", m.user("bob", "PRE", "TV"));
        assertEquals("bob", m.user("bob", "PRE", null));
        assertEquals("PRE", m.group("PRE", "TV"));
    }

    @Test
    void blankPlaceholderFallsBackToDefault() {
        RaceNameMasker m = new RaceNameMasker(props("hideuser.enabled", "true", "hideuser.users", "bob",
                "hideuser.placeholder", "  "));
        assertEquals("Anonymous", m.user("bob", null, null));
    }
}
