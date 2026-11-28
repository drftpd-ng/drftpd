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
package org.drftpd.master.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * SITE RELOAD must choose again between perms.conf and perms.conf.dist, and must never expose a half loaded
 * configuration while it reads the file.
 */
class ConfigManagerReloadTest {
    @TempDir
    Path dir;

    /** A ConfigManager that resolves perms.conf / perms.conf.dist in the temp directory, like ConfigLoader does. */
    private class TestManager extends ConfigManager {
        Runnable duringBuild = () -> {
        };

        @Override
        File resolvePermsFile() {
            duringBuild.run();
            File conf = dir.resolve("perms.conf").toFile();
            return conf.exists() ? conf : dir.resolve("perms.conf.dist").toFile();
        }

        @Override
        Properties readMainProperties() {
            return new Properties();
        }
    }

    private void write(String name, String content) throws IOException {
        Files.writeString(dir.resolve(name), content, StandardCharsets.UTF_8);
    }

    @Test
    void permsConfCreatedAfterStartupIsPickedUp() throws IOException {
        write("perms.conf.dist", "login_prompt from dist\n");
        TestManager cm = new TestManager();
        cm.reload();
        assertEquals("from dist", cm.getLoginPrompt());

        write("perms.conf", "login_prompt from conf\n");
        cm.reload();
        assertEquals("from conf", cm.getLoginPrompt());
    }

    @Test
    void removedPermsConfFallsBackToDist() throws IOException {
        write("perms.conf.dist", "login_prompt from dist\nmax_users 7 3\n");
        write("perms.conf", "login_prompt from conf\n");
        TestManager cm = new TestManager();
        cm.reload();
        assertEquals("from conf", cm.getLoginPrompt());
        assertEquals(1, cm.getMaxUsersTotal());

        Files.delete(dir.resolve("perms.conf"));
        cm.reload();
        assertEquals("from dist", cm.getLoginPrompt());
        assertEquals(7, cm.getMaxUsersTotal());
        assertEquals(3, cm.getMaxUsersExempt());
    }

    @Test
    void valuesRemovedFromTheFileAreResetOnReload() throws IOException {
        write("perms.conf", "max_users 9 4\nhideinstats a\n");
        TestManager cm = new TestManager();
        cm.reload();
        assertEquals(9, cm.getMaxUsersTotal());
        assertEquals("a ", cm.getHideInStats());

        write("perms.conf", "hideinstats a\n");
        cm.reload();
        assertEquals(1, cm.getMaxUsersTotal());
        assertEquals(0, cm.getMaxUsersExempt());
        // reloading must not append to the previous value
        assertEquals("a ", cm.getHideInStats());
    }

    @Test
    void oldConfigurationStaysActiveWhileReloading() throws IOException {
        write("perms.conf", "login_prompt first\nmax_users 5 1\n");
        TestManager cm = new TestManager();
        cm.reload();

        write("perms.conf", "login_prompt second\nmax_users 6 2\n");
        String[] seen = new String[2];
        cm.duringBuild = () -> {
            seen[0] = cm.getLoginPrompt();
            seen[1] = String.valueOf(cm.getMaxUsersTotal());
        };
        // from another thread, as a connected user would
        cm.duringBuild = observeFromOtherThread(cm, seen);
        cm.reload();

        assertEquals("first", seen[0]);
        assertEquals("5", seen[1]);
        assertEquals("second", cm.getLoginPrompt());
        assertEquals(6, cm.getMaxUsersTotal());
    }

    private Runnable observeFromOtherThread(ConfigManager cm, String[] seen) {
        return () -> {
            Thread t = new Thread(() -> {
                seen[0] = cm.getLoginPrompt();
                seen[1] = String.valueOf(cm.getMaxUsersTotal());
            });
            t.start();
            try {
                t.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };
    }

    @Test
    void unreadablePermsFileKeepsPreviousConfiguration() throws IOException {
        write("perms.conf", "login_prompt kept\n");
        TestManager cm = new TestManager();
        cm.reload();
        assertEquals("kept", cm.getLoginPrompt());

        Files.delete(dir.resolve("perms.conf"));   // and there is no .dist either
        cm.reload();
        assertEquals("kept", cm.getLoginPrompt());
    }
}
