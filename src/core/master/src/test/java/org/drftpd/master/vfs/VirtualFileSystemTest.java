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
package org.drftpd.master.vfs;

import com.cedarsoftware.util.io.JsonReader;
import com.cedarsoftware.util.io.JsonWriter;
import org.apache.commons.io.FileUtils;
import org.drftpd.slave.exceptions.FileExistsException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class VirtualFileSystemTest {

    public static VirtualFileSystem vfs = null;

    @BeforeAll
    static void setUp() {
        vfs = VirtualFileSystem.getVirtualFileSystem();
        try {
            vfs.getRoot().createDirectory("Test", "drftpd", "drftpd");
        } catch (FileExistsException ignored) {
        }
        try {
            vfs.getRoot().createFile("testFile", "drftpd", "drftpd", "testSlave");
        } catch (FileExistsException ignored) {
        }
    }

    @AfterAll
    static void tearDown() throws Exception {
        for (String file : vfs.getRoot().getInodeNames()) {
            vfs.getRoot().getInodeByName(file).delete();
        }
        FileUtils.deleteDirectory(new File("userdata"));
    }

    @Test
    public void testGetLast() {
        assertEquals("file", VirtualFileSystem.getLast("/full/path/to/file"));
        assertEquals("to", VirtualFileSystem.getLast("/full/path/to"));
    }

    @Test
    public void testMemory() {
        System.out.println("Stress test, we can scale now with lots of small files");
        for (int x = 0; x < 3; x++) {
            VirtualFileSystemDirectory walker1;
            try {
                vfs.getRoot().createDirectory(String.valueOf(x), "test", "test");
            } catch (FileExistsException ignored) {
            }
            try {
                walker1 = (VirtualFileSystemDirectory) vfs.getRoot().getInodeByName(String.valueOf(x));
            } catch (FileNotFoundException e) {
                throw new RuntimeException("this can't be good1");
            }
            for (int y = 0; y < 3; y++) {
                VirtualFileSystemDirectory walker2;
                try {
                    walker1.createDirectory(String.valueOf(y), "test2", "test2");
                } catch (FileExistsException ignored) {
                }
                try {
                    walker2 = (VirtualFileSystemDirectory) walker1.getInodeByName(String.valueOf(y));
                } catch (FileNotFoundException e) {
                    throw new RuntimeException("this can't be good2");
                }
                for (int z = 0; z < 3; z++) {
                    try {
                        walker2.createFile(String.valueOf(z), "test3", "test3", "testSlave");
                    } catch (FileExistsException ignored) {
                    }
                }
            }
        }
        System.out.println("Done testing memory");
    }

    @Test
    public void testRename() throws Exception {
        //vfs.getRoot().createDirectory("Test", "drftpd", "drftpd");
        VirtualFileSystemInode inode = vfs.getInodeByPath("/Test");
        ((VirtualFileSystemDirectory) inode).createFile("testme", "drftpd", "drftpd", "testSlave");
        assertEquals("/Test", inode.getPath());
        inode.rename("/Test2");
        assertEquals("/Test2", inode.getPath());
        assertNotNull(((VirtualFileSystemDirectory) inode).getInodeByName("testme"));
    }

    @Test
    public void testStripLast() {
        assertEquals("/full/path/to", VirtualFileSystem.stripLast("/full/path/to/file"));
        assertEquals("/full/path", VirtualFileSystem.stripLast("/full/path/to"));
    }

    @Test
    public void testSlaveRefCount() throws Exception {
        vfs.getRoot().createDirectory("SlaveRefTest", "drftpd", "drftpd");
        VirtualFileSystemDirectory dir = (VirtualFileSystemDirectory) vfs.getInodeByPath("/SlaveRefTest");
        assertEquals(0, dir.getSlaveRefCounts().size());

        dir.createFile("file1", "drftpd", "drftpd", "slave1");
        dir.createFile("file2", "drftpd", "drftpd", "slave1");
        dir.createFile("file3", "drftpd", "drftpd", "slave2");
        assertEquals(2, dir.getRefCountForSlave("slave1"));
        assertEquals(1, dir.getRefCountForSlave("slave2"));

        dir.getInodeByName("file1").delete();
        dir.getInodeByName("file3").delete();
        assertEquals(1, dir.getRefCountForSlave("slave1"));
        // A count that reaches 0 is removed instead of being kept around
        assertFalse(dir.getSlaveRefCounts().containsKey("slave2"));
    }

    @Test
    public void testRecalcDoesNotOverwriteChildCounts() throws Exception {
        // Before the fix, recalc stored the first child's AtomicInteger in the parent map and then
        // added the other children to that same object, overwriting the first child's count.
        vfs.getRoot().createDirectory("RecalcTest", "drftpd", "drftpd");
        VirtualFileSystemDirectory parent = (VirtualFileSystemDirectory) vfs.getInodeByPath("/RecalcTest");
        parent.createDirectory("a", "drftpd", "drftpd");
        parent.createDirectory("b", "drftpd", "drftpd");
        VirtualFileSystemDirectory a = (VirtualFileSystemDirectory) parent.getInodeByName("a");
        VirtualFileSystemDirectory b = (VirtualFileSystemDirectory) parent.getInodeByName("b");
        a.createFile("f1", "drftpd", "drftpd", "slave1");
        b.createFile("f2", "drftpd", "drftpd", "slave1");
        b.createFile("f3", "drftpd", "drftpd", "slave1");

        parent.recalcSlaveRefCounts();

        assertEquals(3, parent.getRefCountForSlave("slave1"));
        assertEquals(1, a.getRefCountForSlave("slave1"));
        assertEquals(2, b.getRefCountForSlave("slave1"));
    }

    private static VirtualFileSystemDirectory roundTrip(VirtualFileSystemDirectory dir) throws Exception {
        // Same json-io calls as VirtualFileSystem.writeInode / loadInode
        Map<String, Object> params = new HashMap<>();
        params.put(JsonWriter.PRETTY_PRINT, true);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (JsonWriter writer = new JsonWriter(out, params)) {
            writer.write(dir);
        }
        try (JsonReader reader = new JsonReader(new ByteArrayInputStream(out.toByteArray()))) {
            VirtualFileSystemDirectory loaded = (VirtualFileSystemDirectory) reader.readObject();
            loaded.inodeLoadCompleted();
            return loaded;
        }
    }

    @Test
    public void testLoadsDirectoryWithLegacyAtomicIntegerCounts() throws Exception {
        // Simulate a .dirProperties written by the previous version, where counts were AtomicIntegers
        VirtualFileSystemDirectory legacy = new VirtualFileSystemDirectory("drftpd", "drftpd");
        Map<String, Object> oldCounts = new TreeMap<>();
        oldCounts.put("slave1", new AtomicInteger(3));
        oldCounts.put("slave2", new AtomicInteger(0));
        Field field = VirtualFileSystemDirectory.class.getDeclaredField("_slaveRefCounts");
        field.setAccessible(true);
        field.set(legacy, oldCounts);

        VirtualFileSystemDirectory loaded = roundTrip(legacy);

        Map<String, Integer> counts = loaded.getSlaveRefCounts();
        assertInstanceOf(Integer.class, ((Map<String, ?>) counts).get("slave1"));
        assertEquals(3, counts.get("slave1").intValue());
        assertEquals(3, loaded.getRefCountForSlave("slave1"));
        assertFalse(counts.containsKey("slave2"));
    }

    @Test
    public void testIntegerCountsSurviveRoundTrip() throws Exception {
        VirtualFileSystemDirectory dir = new VirtualFileSystemDirectory("drftpd", "drftpd");
        Map<String, Integer> counts = new TreeMap<>();
        counts.put("slave1", 5);
        dir.setSlaveRefCounts(counts);

        VirtualFileSystemDirectory loaded = roundTrip(dir);

        assertInstanceOf(Integer.class, ((Map<String, ?>) loaded.getSlaveRefCounts()).get("slave1"));
        assertEquals(5, loaded.getRefCountForSlave("slave1"));
    }
}
