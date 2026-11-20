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
package org.drftpd.slave;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SlaveRenameMergeTest {

    @TempDir
    Path tmp;

    private File write(String rel, String content) throws IOException {
        Path p = tmp.resolve(rel);
        Files.createDirectories(p.getParent());
        Files.writeString(p, content, StandardCharsets.UTF_8);
        return p.toFile();
    }

    private String read(String rel) throws IOException {
        return Files.readString(tmp.resolve(rel), StandardCharsets.UTF_8);
    }

    private File dir(String rel) throws IOException {
        return Files.createDirectories(tmp.resolve(rel)).toFile();
    }

    @Test
    void plainMoveWhenDestinationMissing() throws IOException {
        write("src/a.txt", "A");
        assertEquals(0, Slave.mergeInto(tmp.resolve("src").toFile(), tmp.resolve("dst").toFile()));
        assertEquals("A", read("dst/a.txt"));
        assertFalse(Files.exists(tmp.resolve("src")));
    }

    @Test
    void mergesNewFilesIntoExistingDirectory() throws IOException {
        write("src/new.txt", "new");
        write("dst/old.txt", "old");
        assertEquals(0, Slave.mergeInto(tmp.resolve("src").toFile(), tmp.resolve("dst").toFile()));
        assertEquals("new", read("dst/new.txt"));
        assertEquals("old", read("dst/old.txt"));
        assertFalse(Files.exists(tmp.resolve("src")));
    }

    @Test
    void mergesNestedDirectories() throws IOException {
        write("src/sub/deep/a.txt", "A");
        write("src/sub/b.txt", "B");
        write("dst/sub/c.txt", "C");
        assertEquals(0, Slave.mergeInto(tmp.resolve("src").toFile(), tmp.resolve("dst").toFile()));
        assertEquals("A", read("dst/sub/deep/a.txt"));
        assertEquals("B", read("dst/sub/b.txt"));
        assertEquals("C", read("dst/sub/c.txt"));
        assertFalse(Files.exists(tmp.resolve("src")));
    }

    @Test
    void collidingFileIsReplacedBySource() throws IOException {
        write("src/f.bin", "from-source");
        write("dst/f.bin", "leftover");
        assertEquals(0, Slave.mergeInto(tmp.resolve("src").toFile(), tmp.resolve("dst").toFile()));
        assertEquals("from-source", read("dst/f.bin"));
        assertFalse(Files.exists(tmp.resolve("src")));
    }

    @Test
    void topLevelFileOntoFileIsReplaced() throws IOException {
        File from = write("a.txt", "new");
        File to = write("b.txt", "old");
        assertEquals(0, Slave.mergeInto(from, to));
        assertEquals("new", read("b.txt"));
        assertFalse(from.exists());
    }

    @Test
    void fileVersusDirectoryIsLeftInPlace() throws IOException {
        write("src/clash", "file");
        write("dst/clash/inner.txt", "inner");
        write("src/ok.txt", "ok");
        assertEquals(1, Slave.mergeInto(tmp.resolve("src").toFile(), tmp.resolve("dst").toFile()));
        assertEquals("file", read("src/clash"));
        assertEquals("inner", read("dst/clash/inner.txt"));
        assertEquals("ok", read("dst/ok.txt"));
    }

    @Test
    void directoryVersusFileIsLeftInPlace() throws IOException {
        write("src/clash/inner.txt", "inner");
        write("dst/clash", "file");
        assertEquals(1, Slave.mergeInto(tmp.resolve("src").toFile(), tmp.resolve("dst").toFile()));
        assertEquals("inner", read("src/clash/inner.txt"));
        assertEquals("file", read("dst/clash"));
    }

    @Test
    void topLevelTypeMismatchIsLeftAlone() throws IOException {
        File from = write("a.txt", "file");
        File to = dir("d");
        assertEquals(1, Slave.mergeInto(from, to));
        assertTrue(from.exists());
        assertTrue(to.isDirectory());
    }

    @Test
    void sourceDirectoryRemovedWhenEmptiedAndKeptOnConflict() throws IOException {
        write("s1/a.txt", "a");
        dir("d1");
        assertEquals(0, Slave.mergeInto(tmp.resolve("s1").toFile(), tmp.resolve("d1").toFile()));
        assertFalse(Files.exists(tmp.resolve("s1")));

        write("s2/a.txt", "a");
        write("s2/clash", "x");
        write("d2/clash/y.txt", "y");
        assertEquals(1, Slave.mergeInto(tmp.resolve("s2").toFile(), tmp.resolve("d2").toFile()));
        assertTrue(Files.isDirectory(tmp.resolve("s2")));
        assertEquals("x", read("s2/clash"));
        assertEquals("a", read("d2/a.txt"));
    }

    @Test
    void symlinkedDirectoryIsNotFollowed() throws IOException {
        write("outside/keep.txt", "k");
        dir("src");
        Files.createSymbolicLink(tmp.resolve("src/link"), tmp.resolve("outside"));
        write("dst/link/other.txt", "o");
        // the source link meets a real directory: a conflict, and nothing behind the link is touched
        assertEquals(1, Slave.mergeInto(tmp.resolve("src").toFile(), tmp.resolve("dst").toFile()));
        assertEquals("k", read("outside/keep.txt"));
        assertTrue(Files.isSymbolicLink(tmp.resolve("src/link")));
        assertEquals("o", read("dst/link/other.txt"));
    }

    @Test
    void danglingLinkAtDestinationIsReplaced() throws IOException {
        write("src/a.txt", "new");
        dir("dst");
        Files.createSymbolicLink(tmp.resolve("dst/a.txt"), tmp.resolve("does-not-exist"));
        assertEquals(0, Slave.mergeInto(tmp.resolve("src").toFile(), tmp.resolve("dst").toFile()));
        assertFalse(Files.isSymbolicLink(tmp.resolve("dst/a.txt")));
        assertEquals("new", read("dst/a.txt"));
    }
}
