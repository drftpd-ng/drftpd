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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Slave reference counts of directories, see issue #52.
 */
public class VirtualFileSystemSlaveRefCountTest {

    private static VirtualFileSystem vfs = null;

    // A .dirProperties as written by DrFTPD 4.x (taken from issue #52)
    private static final String DIR_PROPERTIES = "{\n" +
            "  \"@type\":\"org.drftpd.master.vfs.VirtualFileSystemDirectory\",\n" +
            "  \"_size\":682610252,\n" +
            "  \"_placeHolderLastModified\":false,\n" +
            "  \"_slaveRefCounts\":{\n" +
            "    \"@type\":\"java.util.TreeMap\",\n" +
            "    \"ARCHiVES\":{\n" +
            "      \"@type\":\"java.util.concurrent.atomic.AtomicInteger\",\n" +
            "      \"value\":17\n" +
            "    },\n" +
            "    \"slave_1\":{\n" +
            "      \"@type\":\"java.util.concurrent.atomic.AtomicInteger\",\n" +
            "      \"value\":0\n" +
            "    }\n" +
            "  },\n" +
            "  \"_username\":\"drftpd\",\n" +
            "  \"_group\":\"drftpd\",\n" +
            "  \"_keyedMap\":{\n" +
            "  },\n" +
            "  \"_pluginMap\":{\n" +
            "  },\n" +
            "  \"_untypedPluginMap\":{\n" +
            "    \"@type\":\"java.util.TreeMap\"\n" +
            "  },\n" +
            "  \"_lastModified\":1611926207034,\n" +
            "  \"_creationTime\":1602593512394\n" +
            "}";

    @BeforeAll
    static void setUp() {
        vfs = VirtualFileSystem.getVirtualFileSystem();
    }

    @AfterAll
    static void tearDown() throws Exception {
        for (String file : vfs.getRoot().getInodeNames()) {
            vfs.getRoot().getInodeByName(file).delete();
        }
        FileUtils.deleteDirectory(new File("userdata"));
    }

    private static VirtualFileSystemDirectory createDir(VirtualFileSystemDirectory parent, String name) throws Exception {
        parent.createDirectory(name, "drftpd", "drftpd");
        return (VirtualFileSystemDirectory) parent.getInodeByName(name);
    }

    @Test
    public void testRecalcDoesNotOverwriteChildCounts() throws Exception {
        VirtualFileSystemDirectory parent = createDir(vfs.getRoot(), "RefCountRecalc");
        VirtualFileSystemDirectory a = createDir(parent, "a");
        VirtualFileSystemDirectory b = createDir(parent, "b");
        a.createFile("f1", "drftpd", "drftpd", "slave1");
        b.createFile("f2", "drftpd", "drftpd", "slave1");
        b.createFile("f3", "drftpd", "drftpd", "slave1");
        b.createFile("f4", "drftpd", "drftpd", "slave2");
        // A stale slave that no file references any more
        Map<String, AtomicInteger> stale = parent.getSlaveRefCounts();
        stale.put("ARCHiVES", new AtomicInteger(17));
        parent.setSlaveRefCounts(stale);

        parent.recalcSlaveRefCounts();

        // Before the fix, the first child got the parent's total
        assertEquals(1, a.getRefCountForSlave("slave1"));
        assertEquals(2, b.getRefCountForSlave("slave1"));
        assertEquals(1, b.getRefCountForSlave("slave2"));
        assertEquals(3, parent.getRefCountForSlave("slave1"));
        assertEquals(1, parent.getRefCountForSlave("slave2"));
        assertFalse(parent.getSlaveRefCounts().containsKey("ARCHiVES"));

        // Running it again gives the same result
        parent.recalcSlaveRefCounts();
        assertEquals(1, a.getRefCountForSlave("slave1"));
        assertEquals(2, b.getRefCountForSlave("slave1"));
        assertEquals(3, parent.getRefCountForSlave("slave1"));
    }

    @Test
    public void testGetSlaveRefCountsReturnsCopies() throws Exception {
        VirtualFileSystemDirectory dir = createDir(vfs.getRoot(), "RefCountCopy");
        dir.createFile("f1", "drftpd", "drftpd", "slave1");

        dir.getSlaveRefCounts().get("slave1").addAndGet(10);

        assertEquals(1, dir.getRefCountForSlave("slave1"));
    }

    @Test
    public void testCountsReachingZeroAreRemoved() throws Exception {
        VirtualFileSystemDirectory parent = createDir(vfs.getRoot(), "RefCountZero");
        VirtualFileSystemDirectory dir = createDir(parent, "sub");
        dir.createFile("f1", "drftpd", "drftpd", "slave1");
        dir.createFile("f2", "drftpd", "drftpd", "slave2");
        VirtualFileSystemFile f2 = (VirtualFileSystemFile) dir.getInodeByName("f2");
        f2.addSlave("slave3");
        assertEquals(1, dir.getRefCountForSlave("slave3"));

        // decrementSlaveRefCount
        f2.removeSlave("slave3");
        assertFalse(dir.getSlaveRefCounts().containsKey("slave3"));
        assertFalse(parent.getSlaveRefCounts().containsKey("slave3"));

        // removeChildSlaveRefCounts
        dir.getInodeByName("f1").delete();
        assertFalse(dir.getSlaveRefCounts().containsKey("slave1"));
        assertFalse(parent.getSlaveRefCounts().containsKey("slave1"));
        assertEquals(1, dir.getRefCountForSlave("slave2"));

        dir.delete();
        assertTrue(parent.getSlaveRefCounts().isEmpty());
    }

    @Test
    public void testDecrementMissingEntryDoesNotCreateOne() throws Exception {
        VirtualFileSystemDirectory parent = createDir(vfs.getRoot(), "RefCountMissing");
        VirtualFileSystemDirectory dir = createDir(parent, "sub");

        dir.decrementSlaveRefCount("ghost");

        assertFalse(dir.getSlaveRefCounts().containsKey("ghost"));
        assertFalse(parent.getSlaveRefCounts().containsKey("ghost"));
        assertEquals(0, dir.getRefCountForSlave("ghost"));
    }

    @Test
    public void testDirPropertiesWithAtomicIntegerCountsStillLoads() throws Exception {
        VirtualFileSystemDirectory loaded = read(DIR_PROPERTIES.getBytes(StandardCharsets.UTF_8));

        assertEquals(682610252L, loaded.getSize());
        Map<String, AtomicInteger> counts = loaded.getSlaveRefCounts();
        assertEquals(17, loaded.getRefCountForSlave("ARCHiVES"));
        // The 0 count is dropped on load
        assertFalse(counts.containsKey("slave_1"));
        assertEquals(1, counts.size());

        // Write it back as VirtualFileSystem.writeInode does: the counts are still AtomicIntegers
        Map<String, Object> params = new HashMap<>();
        params.put(JsonWriter.PRETTY_PRINT, true);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (JsonWriter writer = new JsonWriter(out, params)) {
            writer.write(loaded);
        }
        String json = out.toString(StandardCharsets.UTF_8);
        assertTrue(json.contains("\"_slaveRefCounts\""), json);
        assertTrue(json.contains("\"@type\":\"java.util.concurrent.atomic.AtomicInteger\""), json);

        VirtualFileSystemDirectory reloaded = read(out.toByteArray());
        assertEquals(17, reloaded.getRefCountForSlave("ARCHiVES"));
        assertEquals(682610252L, reloaded.getSize());
        Object value = ((Map<?, ?>) readField(reloaded)).get("ARCHiVES");
        assertInstanceOf(AtomicInteger.class, value);
    }

    private static VirtualFileSystemDirectory read(byte[] json) throws Exception {
        // Same json-io calls as VirtualFileSystem.loadInode
        try (JsonReader reader = new JsonReader(new ByteArrayInputStream(json))) {
            VirtualFileSystemDirectory dir = (VirtualFileSystemDirectory) reader.readObject();
            dir.setName("TV-HD");
            dir.inodeLoadCompleted();
            return dir;
        }
    }

    private static Object readField(VirtualFileSystemDirectory dir) throws Exception {
        java.lang.reflect.Field field = VirtualFileSystemDirectory.class.getDeclaredField("_slaveRefCounts");
        field.setAccessible(true);
        return field.get(dir);
    }
}
