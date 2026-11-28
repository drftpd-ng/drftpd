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
package org.drftpd.zipscript.master.badfile;

import org.drftpd.zipscript.master.RaceNameMasker;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BadFileAnnouncerTest {

    @Test
    void eventCarriesReasonAndDetail() {
        BadFileEvent event = new BadFileEvent("bob", null, "a.rar", BadFileReason.BAD_CRC, "SLAVE: 1 SFV: 2");
        assertEquals("bob", event.getUsername());
        assertEquals("a.rar", event.getFileName());
        assertEquals(BadFileReason.BAD_CRC, event.getReason());
        assertEquals("SLAVE: 1 SFV: 2", event.getDetail());
        assertEquals("", new BadFileEvent("bob", null, "a.rar", BadFileReason.ZERO_BYTE, null).getDetail());
    }

    @Test
    void reasonsMapToDistinctThemeKeys() {
        assertEquals("zipscript.badfile.crc", BadFileReason.BAD_CRC.getThemeKey());
        assertEquals("zipscript.badfile.zero", BadFileReason.ZERO_BYTE.getThemeKey());
        assertEquals("zipscript.badfile.notinsfv", BadFileReason.NOT_IN_SFV.getThemeKey());
        assertEquals("zipscript.badfile.other", BadFileReason.OTHER.getThemeKey());
    }

    @Test
    void defaultThemeDefinesEveryReason() throws Exception {
        Properties theme = new Properties();
        try (InputStream in = getClass().getResourceAsStream(
                "/master/config/themes/irc/badfile.announcer.theme.default")) {
            assertNotNull(in);
            theme.load(in);
        }
        for (BadFileReason reason : BadFileReason.values()) {
            assertTrue(theme.containsKey(reason.getThemeKey()), reason.getThemeKey());
        }
    }

    @Test
    void envHasThemeVariables() {
        BadFileEvent event = new BadFileEvent("bob", null, "a.rar", BadFileReason.OTHER, "banned genre");
        Map<String, Object> env = new HashMap<>();
        BadFileAnnouncer.fillEnv(env, event, "grp", new RaceNameMasker(new Properties()), "MP3", "MP3", "5", "Rel-GRP");
        assertEquals("bob", env.get("user"));
        assertEquals("grp", env.get("group"));
        assertEquals("MP3", env.get("section"));
        assertEquals("Rel-GRP", env.get("path"));
        assertEquals("a.rar", env.get("file"));
        assertEquals("OTHER", env.get("reason"));
        assertEquals("banned genre", env.get("detail"));
        assertEquals("store.bad", BadFileAnnouncer.ANNOUNCE_TYPE);
    }

    @Test
    void hideuserMasksUserAndGroup() {
        Properties props = new Properties();
        props.setProperty("hideuser.enabled", "true");
        props.setProperty("hideuser.groups", "testers");
        RaceNameMasker masker = new RaceNameMasker(props);
        BadFileEvent event = new BadFileEvent("lambda", null, "bad.bin", BadFileReason.OTHER, "x");

        Map<String, Object> env = new HashMap<>();
        BadFileAnnouncer.fillEnv(env, event, "testers", masker, "TV", "TV", "5", "Rel-GRP");
        assertEquals("Anonymous", env.get("user"));
        assertEquals("Anonymous", env.get("group"));

        env.clear();
        BadFileAnnouncer.fillEnv(env, event, "others", masker, "TV", "TV", "5", "Rel-GRP");
        assertEquals("lambda", env.get("user"));
        assertEquals("others", env.get("group"));

        env.clear();
        BadFileAnnouncer.fillEnv(env, event, "", masker, "TV", "TV", "5", "Rel-GRP");
        assertEquals("lambda", env.get("user"));
    }

    @Test
    void cooldownSuppressesRepeatsOfTheSameFile() {
        BadFileCooldown c = new BadFileCooldown(60_000L);
        assertTrue(c.tryAnnounce("/TV/Rel/a.rar", 1_000L));
        assertFalse(c.tryAnnounce("/TV/Rel/a.rar", 30_000L));
        // another file or directory is independent
        assertTrue(c.tryAnnounce("/TV/Rel/b.rar", 30_000L));
        assertTrue(c.tryAnnounce("/TV/Other/a.rar", 30_000L));
        // a suppressed attempt does not extend the cooldown
        assertTrue(c.tryAnnounce("/TV/Rel/a.rar", 61_000L));
        assertFalse(c.tryAnnounce("/TV/Rel/a.rar", 62_000L));
    }

    @Test
    void cooldownTableIsBounded() {
        BadFileCooldown c = new BadFileCooldown(Long.MAX_VALUE / 2);
        for (int i = 0; i < 5000; i++) {
            assertTrue(c.tryAnnounce("/d/f" + i, i));
        }
        // the oldest entries were forgotten, the newest are still cooling down
        assertTrue(c.tryAnnounce("/d/f0", 5000L));
        assertFalse(c.tryAnnounce("/d/f4999", 5001L));
    }
}
