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
package org.drftpd.zipscript.master.sfv;

import org.drftpd.common.vfs.CaseInsensitiveTreeMap;
import org.drftpd.master.slavemanagement.RemoteSlave;
import org.drftpd.master.vfs.DirectoryHandle;
import org.drftpd.master.vfs.FileHandle;
import org.drftpd.master.vfs.VirtualFileSystem;
import org.drftpd.zipscript.common.sfv.SFVInfo;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for #77: files uploaded before the .sfv must be checked against it once it arrives.
 */
public class SFVToolsTest {

    private static final RemoteSlave SLAVE = new RemoteSlave("slave1");

    @BeforeAll
    static void setUp() {
        // Initializes the on-disk VFS under ./userdata
        VirtualFileSystem.getVirtualFileSystem();
    }

    @AfterAll
    static void tearDown() throws IOException {
        Path userdata = new File("userdata").toPath();
        if (Files.exists(userdata)) {
            try (Stream<Path> paths = Files.walk(userdata)) {
                paths.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
            }
        }
    }

    /**
     * Creates a file as if it had been uploaded with the given size and transfer checksum.
     */
    private static void upload(DirectoryHandle dir, String name, long size, long checksum) throws Exception {
        FileHandle file = dir.createFileUnchecked(name, "drftpd", "drftpd", SLAVE);
        file.setSize(size);
        file.setCheckSum(checksum);
    }

    private static DirectoryHandle createDir(String name) throws Exception {
        return new DirectoryHandle("/").createDirectoryUnchecked(name, "drftpd", "drftpd");
    }

    private static List<String> names(List<FileHandle> files) {
        List<String> names = new ArrayList<>();
        for (FileHandle file : files) {
            names.add(file.getName());
        }
        return names;
    }

    @Test
    public void testFindsFilesNotMatchingSFV() throws Exception {
        DirectoryHandle dir = createDir("SFVToolsTest1");
        upload(dir, "good.mp3", 1000L, 0x11111111L);
        upload(dir, "bad.mp3", 1000L, 0x22222222L);        // corrupt: checksum differs from sfv
        upload(dir, "Mixed.Case.r00", 1000L, 0x33333333L); // sfv names are case insensitive
        upload(dir, "empty.r01", 0L, 0L);                  // 0-byte file, sfv expects real data
        upload(dir, "nocrc.r02", 1000L, 0L);               // checksummed transfers disabled
        upload(dir, "extra.nfo", 100L, 0x55555555L);       // not listed in the sfv

        CaseInsensitiveTreeMap<String, Long> entries = new CaseInsensitiveTreeMap<>();
        entries.put("good.mp3", 0x11111111L);
        entries.put("bad.mp3", 0x99999999L);
        entries.put("mixed.case.r00", 0x33333333L);
        entries.put("empty.r01", 0x44444444L);
        entries.put("nocrc.r02", 0x66666666L);
        entries.put("missing.r03", 0x77777777L);           // listed but not uploaded yet
        SFVInfo sfvInfo = new SFVInfo();
        sfvInfo.setEntries(entries);

        List<String> mismatched = names(SFVTools.getFilesNotMatchingSFV(sfvInfo, dir));

        assertEquals(2, mismatched.size(), "Expected only bad.mp3 and empty.r01, got " + mismatched);
        assertTrue(mismatched.contains("bad.mp3"));
        assertTrue(mismatched.contains("empty.r01"));
    }

    @Test
    public void testEmptyFileExpectedEmptyIsNotReported() throws Exception {
        DirectoryHandle dir = createDir("SFVToolsTest2");
        upload(dir, "empty.txt", 0L, 0L);

        CaseInsensitiveTreeMap<String, Long> entries = new CaseInsensitiveTreeMap<>();
        entries.put("empty.txt", 0L); // CRC32 of an empty file is 00000000
        SFVInfo sfvInfo = new SFVInfo();
        sfvInfo.setEntries(entries);

        assertTrue(SFVTools.getFilesNotMatchingSFV(sfvInfo, dir).isEmpty());
    }
}
