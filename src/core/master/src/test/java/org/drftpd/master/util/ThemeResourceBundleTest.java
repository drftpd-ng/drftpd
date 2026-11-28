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
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class ThemeResourceBundleTest {

    @TempDir
    Path dir;

    private void write(String name, String content) throws IOException {
        Files.write(dir.resolve(name), content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void overrideOnlyKeysAreVisible() throws IOException {
        write("a.theme.default", "plain=default plain\nkept=default kept\n");
        write("a.theme", "plain=override plain\nplain.rar=override variant\n");
        ThemeResourceBundle bundle = new ThemeResourceBundle(dir.toString());

        assertTrue(bundle.containsKey("plain.rar"));
        assertEquals("override variant", bundle.getString("plain.rar"));
        assertEquals("override plain", bundle.getString("plain"));
        assertEquals("default kept", bundle.getString("kept"));
    }

    @Test
    public void defaultOnlyKeysStillWork() throws IOException {
        write("a.theme.default", "plain=default plain\nplain.zip=default variant\n");
        ThemeResourceBundle bundle = new ThemeResourceBundle(dir.toString());

        assertEquals("default variant", bundle.getString("plain.zip"));
        assertFalse(bundle.containsKey("plain.rar"));
    }
}
