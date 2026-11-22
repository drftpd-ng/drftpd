/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * DrFTPD is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with DrFTPD; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package org.drftpd.zipscript.slave.zip;

import org.drftpd.zipscript.common.zip.DizInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

public class ZipscriptZipHandlerTest {

    @TempDir
    Path dir;

    private File zip(String name, String... namesAndContents) throws IOException {
        File f = dir.resolve(name).toFile();
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(f))) {
            for (int i = 0; i < namesAndContents.length; i += 2) {
                out.putNextEntry(new ZipEntry(namesAndContents[i]));
                if (namesAndContents[i + 1] != null) {
                    out.write(namesAndContents[i + 1].getBytes(StandardCharsets.ISO_8859_1));
                }
                out.closeEntry();
            }
        }
        return f;
    }

    private File bigZip(String name) throws IOException {
        File f = dir.resolve(name).toFile();
        byte[] data = new byte[2_000_000];
        new Random(42).nextBytes(data);
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(f))) {
            out.putNextEntry(new ZipEntry("FILE_ID.DIZ"));
            out.write(" Some.Release [01/05]\r\n".getBytes(StandardCharsets.ISO_8859_1));
            out.closeEntry();
            out.putNextEntry(new ZipEntry("data.bin"));
            out.write(data);
            out.closeEntry();
        }
        return f;
    }

    @Test
    public void validZipPasses() throws IOException {
        File f = zip("ok.zip", "a.txt", "hello", "dir/", null, "dir/b.txt", "world");
        ZipscriptZipHandler.checkZip(f);
    }

    @Test
    public void corruptZipFails() throws IOException {
        File f = bigZip("bad.zip");
        // damage the compressed data in the middle of the file
        try (RandomAccessFile raf = new RandomAccessFile(f, "rw")) {
            raf.seek(raf.length() / 2);
            raf.write(new byte[64]);
        }
        assertThrows(IOException.class, () -> ZipscriptZipHandler.checkZip(f));
    }

    @Test
    public void zipWithoutFilesFails() throws IOException {
        File onlyDir = zip("dir-only.zip", "dir/", null);
        assertThrows(IOException.class, () -> ZipscriptZipHandler.checkZip(onlyDir));
    }

    @Test
    public void notAZipFails() throws IOException {
        File f = dir.resolve("text.zip").toFile();
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write("not a zip".getBytes(StandardCharsets.ISO_8859_1));
        }
        assertThrows(IOException.class, () -> ZipscriptZipHandler.checkZip(f));
    }

    @Test
    public void dizIsReadInAnyDirectoryAndCase() throws IOException {
        DizInfo top = ZipscriptZipHandler.readDiz(zip("top.zip", "a.txt", "x", "FILE_ID.DIZ", " Rel [01/12]\r\n"));
        assertTrue(top.isValid());
        assertEquals(12, top.getTotal());

        DizInfo nested = ZipscriptZipHandler.readDiz(zip("nested.zip", "sub/file_id.diz", " Rel (01/07)\r\n"));
        assertTrue(nested.isValid());
        assertEquals(7, nested.getTotal());
    }

    @Test
    public void dizWithoutTotalOrMissingIsInvalid() throws IOException {
        assertFalse(ZipscriptZipHandler.readDiz(zip("nototal.zip", "FILE_ID.DIZ", "just text\r\n")).isValid());
        assertFalse(ZipscriptZipHandler.readDiz(zip("nodiz.zip", "a.txt", "x")).isValid());
    }

    @Test
    public void dizKeepsTheFirstOneWithATotal() throws IOException {
        DizInfo info = ZipscriptZipHandler.readDiz(zip("two.zip",
                "a/FILE_ID.DIZ", "no total here\r\n", "b/FILE_ID.DIZ", " Rel [01/03]\r\n"));
        assertTrue(info.isValid());
        assertEquals(3, info.getTotal());
    }

    /**
     * The integrity check and the DIZ read can run at the same time on the same zip (several slave
     * command threads). With the zip file system the second open failed, so one of the two
     * randomly reported a bad zip or no DIZ. Both must succeed every time.
     */
    @Test
    public void integrityCheckAndDizReadCanRunConcurrently() throws Exception {
        File f = bigZip("race.zip");
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<Void>> results = new ArrayList<>();
            for (int i = 0; i < 200; i++) {
                final boolean check = i % 2 == 0;
                results.add(pool.submit((Callable<Void>) () -> {
                    if (check) {
                        ZipscriptZipHandler.checkZip(f);
                    } else {
                        DizInfo info = ZipscriptZipHandler.readDiz(f);
                        assertTrue(info.isValid(), "DIZ not read");
                        assertEquals(5, info.getTotal());
                    }
                    return null;
                }));
            }
            for (Future<Void> result : results) {
                result.get(); // rethrows any failure from the worker threads
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
