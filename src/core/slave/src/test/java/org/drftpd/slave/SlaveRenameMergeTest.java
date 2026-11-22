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

    /** The single entry in dir whose name starts with prefix + ".drftpd-conflict-". */
    private Path aside(String dir, String prefix) throws IOException {
        try (var s = Files.list(tmp.resolve(dir))) {
            var found = s.filter(p -> p.getFileName().toString().startsWith(prefix + ".drftpd-conflict-")).toList();
            assertEquals(1, found.size());
            return found.get(0);
        }
    }

    @Test
    void directoryInTheWayOfAFileIsMovedAside() throws IOException {
        write("src/clash", "file");
        write("dst/clash/inner.txt", "inner");
        write("src/ok.txt", "ok");
        assertEquals(1, Slave.mergeInto(tmp.resolve("src").toFile(), tmp.resolve("dst").toFile()));
        // the tracked source ends up at the destination, the leftover is kept next to it
        assertEquals("file", read("dst/clash"));
        assertEquals("inner", Files.readString(aside("dst", "clash").resolve("inner.txt")));
        assertEquals("ok", read("dst/ok.txt"));
        // nothing is left at the source, so deleting the old path later can't remove the tracked copy
        assertFalse(Files.exists(tmp.resolve("src")));
    }

    @Test
    void fileInTheWayOfADirectoryIsMovedAside() throws IOException {
        write("src/clash/inner.txt", "inner");
        write("dst/clash", "file");
        assertEquals(1, Slave.mergeInto(tmp.resolve("src").toFile(), tmp.resolve("dst").toFile()));
        assertEquals("inner", read("dst/clash/inner.txt"));
        assertEquals("file", Files.readString(aside("dst", "clash")));
        assertFalse(Files.exists(tmp.resolve("src")));
    }

    @Test
    void topLevelTypeMismatchMovesDestinationAside() throws IOException {
        File from = write("a.txt", "file");
        write("d/x.txt", "x");
        assertEquals(1, Slave.mergeInto(from, tmp.resolve("d").toFile()));
        assertFalse(from.exists());
        assertEquals("file", read("d"));
        assertEquals("x", Files.readString(aside("", "d").resolve("x.txt")));
    }

    @Test
    void sourceDirectoryRemovedWhenEmptied() throws IOException {
        write("s1/a.txt", "a");
        write("s1/sub/b.txt", "b");
        write("d1/sub/c.txt", "c");
        assertEquals(0, Slave.mergeInto(tmp.resolve("s1").toFile(), tmp.resolve("d1").toFile()));
        assertFalse(Files.exists(tmp.resolve("s1")));
        assertEquals("a", read("d1/a.txt"));
        assertEquals("b", read("d1/sub/b.txt"));
        assertEquals("c", read("d1/sub/c.txt"));
    }

    @Test
    void missingSourceIsSkippedSoAMergeCanBeRunAgain() throws IOException {
        write("src/a.txt", "a");
        dir("dst");
        assertEquals(0, Slave.mergeInto(tmp.resolve("src").toFile(), tmp.resolve("dst").toFile()));
        // a second run (the master requeues a rename that failed halfway) finds the source gone
        assertEquals(0, Slave.mergeInto(tmp.resolve("src").toFile(), tmp.resolve("dst").toFile()));
        assertEquals("a", read("dst/a.txt"));
    }

    @Test
    void halfDoneMergeIsCompletedByASecondRun() throws IOException {
        write("src/a.txt", "a");
        write("src/b.txt", "b");
        write("dst/a.txt", "a");   // a.txt was already moved by the first, interrupted run
        Files.delete(tmp.resolve("src/a.txt"));
        assertEquals(0, Slave.mergeInto(tmp.resolve("src").toFile(), tmp.resolve("dst").toFile()));
        assertEquals("a", read("dst/a.txt"));
        assertEquals("b", read("dst/b.txt"));
        assertFalse(Files.exists(tmp.resolve("src")));
    }

    @Test
    void symlinkedDirectoryIsNotFollowed() throws IOException {
        write("outside/keep.txt", "k");
        dir("src");
        Files.createSymbolicLink(tmp.resolve("src/link"), tmp.resolve("outside"));
        write("dst/link/other.txt", "o");
        // the source link meets a real directory: the directory is moved aside and the link moved in,
        // nothing behind the link is touched
        assertEquals(1, Slave.mergeInto(tmp.resolve("src").toFile(), tmp.resolve("dst").toFile()));
        assertEquals("k", read("outside/keep.txt"));
        assertTrue(Files.isSymbolicLink(tmp.resolve("dst/link")));
        assertEquals("o", Files.readString(aside("dst", "link").resolve("other.txt")));
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
