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
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class SlaveRenameOtherRootsTest {

    @TempDir
    Path tmp;

    private File rootA() throws IOException {
        return Files.createDirectories(tmp.resolve("a")).toFile();
    }

    private File rootB() throws IOException {
        return Files.createDirectories(tmp.resolve("b")).toFile();
    }

    private void write(String rel, String content) throws IOException {
        Path p = tmp.resolve(rel);
        Files.createDirectories(p.getParent());
        Files.writeString(p, content, StandardCharsets.UTF_8);
    }

    private String read(String rel) throws IOException {
        return Files.readString(tmp.resolve(rel), StandardCharsets.UTF_8);
    }

    private long count(String dirRel, String prefix) throws IOException {
        try (Stream<Path> s = Files.list(tmp.resolve(dirRel))) {
            return s.filter(p -> p.getFileName().toString().startsWith(prefix)).count();
        }
    }

    @Test
    void leftoverDirectoryInOtherRootIsMovedAside() throws IOException {
        write("a/x/rel/f.txt", "src");
        write("b/y/rel/old.txt", "old");
        int n = Slave.moveLeftoversAside(List.of(rootA(), rootB()), "/x/rel", "/y/rel");
        assertEquals(1, n);
        assertFalse(Files.exists(tmp.resolve("b/y/rel")));
        assertEquals(1, count("b/y", "rel.drftpd-conflict-"));
        try (Stream<Path> s = Files.list(tmp.resolve("b/y"))) {
            Path aside = s.findFirst().orElseThrow();
            assertEquals("old", Files.readString(aside.resolve("old.txt"), StandardCharsets.UTF_8));
        }
        assertEquals("src", read("a/x/rel/f.txt"));
    }

    @Test
    void leftoverFileInOtherRootIsMovedAside() throws IOException {
        write("a/x/f.nfo", "src");
        write("b/y/f.nfo", "old");
        assertEquals(1, Slave.moveLeftoversAside(List.of(rootA(), rootB()), "/x/f.nfo", "/y/f.nfo"));
        assertFalse(Files.exists(tmp.resolve("b/y/f.nfo")));
        assertEquals(1, count("b/y", "f.nfo.drftpd-conflict-"));
        assertEquals("src", read("a/x/f.nfo"));
    }

    @Test
    void sourceSplitAcrossRootsIsNotMovedAside() throws IOException {
        write("a/x/rel/cd1.rar", "1");
        write("b/x/rel/cd2.rar", "2");
        write("b/y/rel/old.txt", "old");
        // root a holds part of the source and no destination, root b holds part of the source and a destination
        assertEquals(0, Slave.moveLeftoversAside(List.of(rootA(), rootB()), "/x/rel", "/y/rel"));
        assertEquals("old", read("b/y/rel/old.txt"));
        assertEquals("2", read("b/x/rel/cd2.rar"));
        // each root's own merge then handles its destination (rename() creates the parent directory first)
        Files.createDirectories(tmp.resolve("a/y"));
        assertEquals(0, Slave.mergeInto(tmp.resolve("a/x/rel").toFile(), tmp.resolve("a/y/rel").toFile()));
        assertEquals(0, Slave.mergeInto(tmp.resolve("b/x/rel").toFile(), tmp.resolve("b/y/rel").toFile()));
        assertEquals("1", read("a/y/rel/cd1.rar"));
        assertEquals("2", read("b/y/rel/cd2.rar"));
        assertEquals("old", read("b/y/rel/old.txt"));
        assertEquals(0, count("b/y", "rel.drftpd-conflict-"));
    }

    @Test
    void splitSourceWithLeftoverInThirdRoot() throws IOException {
        write("a/x/rel/cd1.rar", "1");
        write("b/x/rel/cd2.rar", "2");
        write("c/y/rel/old.txt", "old");
        File c = Files.createDirectories(tmp.resolve("c")).toFile();
        assertEquals(1, Slave.moveLeftoversAside(List.of(rootA(), rootB(), c), "/x/rel", "/y/rel"));
        assertFalse(Files.exists(tmp.resolve("c/y/rel")));
        assertEquals(1, count("c/y", "rel.drftpd-conflict-"));
    }

    @Test
    void noLeftoverMeansNoChange() throws IOException {
        write("a/x/rel/f.txt", "src");
        Files.createDirectories(tmp.resolve("b/y"));
        assertEquals(0, Slave.moveLeftoversAside(List.of(rootA(), rootB()), "/x/rel", "/y/rel"));
        assertEquals("src", read("a/x/rel/f.txt"));
        assertEquals(0, count("b/y", ""));
        assertEquals(0, count("b", "x"));
    }

    @Test
    void replayedRenameLeavesTheRenamedDataAlone() throws IOException {
        // the rename already happened (source gone everywhere), the master replays it: the destination in
        // both roots is the tracked data and must stay where it is
        write("a/y/rel/f1.txt", "one");
        write("b/y/rel/f2.txt", "two");
        assertEquals(0, Slave.moveLeftoversAside(List.of(rootA(), rootB()), "/x/rel", "/y/rel"));
        assertEquals("one", read("a/y/rel/f1.txt"));
        assertEquals("two", read("b/y/rel/f2.txt"));
        assertEquals(1, count("a/y", ""));
        assertEquals(1, count("b/y", ""));
    }
}
