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

import org.apache.commons.io.FileUtils;
import org.drftpd.slave.exceptions.FileExistsException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileNotFoundException;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    public void testRename() throws FileNotFoundException, FileExistsException {
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
}
