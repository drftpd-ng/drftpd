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
package org.drftpd.master.util;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.drftpd.master.util.ReplacerUtils.jprintf;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ReplacerUtilsTest {

    @Test
    public void testVarReplacement() {
        Map<String, Object> env = new HashMap<>();
        env.put("position", 3);
        env.put("user", "test");
        env.put("group", "sysop");
        env.put("files", 11);
        env.put("bytes", "10To");
        env.put("speed", "27.4MB/s");
        env.put("percent", "45");
        // Complex template
        String template = "#${position,3.3} ${user,-13} @ ${group,-11} - ${files,7}F - ${bytes,7} - ${speed,10} - ${percent,6}";
        assertEquals("# 3 test      @ sysop  -    11F - 10To - 27.4MB/s -   45", jprintf(template, env));
        // Simple template
        template = "#${position} ${user} @ ${group} - ${files}F - ${bytes} - ${speed} - ${percent}";
        assertEquals("#3 test @ sysop - 11F - 10To - 27.4MB/s - 45", jprintf(template, env));
    }
}
