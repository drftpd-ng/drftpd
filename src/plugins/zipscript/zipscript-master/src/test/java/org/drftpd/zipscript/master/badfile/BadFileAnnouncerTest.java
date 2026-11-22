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

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        BadFileAnnouncer.fillEnv(env, event, "grp", "MP3", "5", "Rel-GRP");
        assertEquals("bob", env.get("user"));
        assertEquals("grp", env.get("group"));
        assertEquals("MP3", env.get("section"));
        assertEquals("Rel-GRP", env.get("path"));
        assertEquals("a.rar", env.get("file"));
        assertEquals("OTHER", env.get("reason"));
        assertEquals("banned genre", env.get("detail"));
        assertEquals("store.bad", BadFileAnnouncer.ANNOUNCE_TYPE);
    }
}
