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
package org.drftpd.master.vfs;

import org.drftpd.master.tests.DummyGlobalContext;
import org.drftpd.master.tests.DummySlaveManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.FileNotFoundException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Directory sizes must stay equal to the sum of their children, and never go negative.
 */
public class VirtualFileSystemSizeTest {

    private static final String DIR = "SizeTest";

    private VirtualFileSystemDirectory _root;
    private long _rootBefore;

    @BeforeEach
    void setUp() throws Exception {
        DummyGlobalContext gctx = new DummyGlobalContext();
        gctx.setSlaveManager(new DummySlaveManager() {
            @Override
            public void deleteOnAllSlaves(DirectoryHandle directory) {
                // nothing to delete on fake slaves
            }
        });
        _root = VirtualFileSystem.getVirtualFileSystem().getRoot();
        _rootBefore = _root.getSize();
        _root.createDirectory(DIR, "drftpd", "drftpd");
    }

    @AfterEach
    void tearDown() {
        try {
            _root.getInodeByName(DIR).delete();
        } catch (FileNotFoundException ignored) {
        }
    }

    private VirtualFileSystemDirectory dir() throws FileNotFoundException {
        return (VirtualFileSystemDirectory) _root.getInodeByName(DIR);
    }

    /**
     * Two removers of the same last slave (for instance SITE DELSLAVE and a remerge of that slave, or a
     * remerge racing the unknown-slave cleanup in FileHandle.getSlaves()) both hold the file inode. The
     * first one deletes the file; the second one removed nothing but still saw an empty slave list and
     * deleted the file again, subtracting its size a second time.
     */
    @Test
    void removingTheLastSlaveTwiceSubtractsTheSizeOnce() throws Exception {
        dir().createFile("a.bin", "drftpd", "drftpd", "slaveA", 100L);
        VirtualFileSystemFile file = (VirtualFileSystemFile) dir().getInodeByName("a.bin");
        assertEquals(100L, dir().getSize());

        file.removeSlave("slaveA");
        assertFalse(dir().getInodeNames().contains("a.bin"));
        assertEquals(0L, dir().getSize());

        file.removeSlave("slaveA");
        assertEquals(0L, dir().getSize());
        assertEquals(_rootBefore, _root.getSize());
    }

    /**
     * The stale second delete must not touch a new file that was created with the same name in between.
     */
    @Test
    void staleRemoveSlaveDoesNotDeleteANewFileWithTheSameName() throws Exception {
        dir().createFile("a.bin", "drftpd", "drftpd", "slaveA", 100L);
        VirtualFileSystemFile stale = (VirtualFileSystemFile) dir().getInodeByName("a.bin");
        stale.removeSlave("slaveA");

        dir().createFile("a.bin", "drftpd", "drftpd", "slaveB", 40L);
        stale.removeSlave("slaveA");

        assertTrue(dir().getInodeNames().contains("a.bin"));
        assertEquals(40L, dir().getSize());
        assertEquals(_rootBefore + 40L, _root.getSize());
    }

    /**
     * removeChild() for a child that is no longer in the directory must not subtract its size.
     */
    @Test
    void deletingAnAlreadyDeletedInodeDoesNotSubtractAgain() throws Exception {
        dir().createFile("a.bin", "drftpd", "drftpd", "slaveA", 100L);
        VirtualFileSystemInode file = dir().getInodeByName("a.bin");
        file.delete();
        file.delete();
        assertEquals(0L, dir().getSize());
        assertEquals(_rootBefore, _root.getSize());
    }

    /**
     * SITE FIXSIZE in a subdirectory must correct the parents up to / as well, otherwise they keep the
     * old error and a later delete of the subdirectory's content makes them negative.
     */
    @Test
    void fixSizePropagatesTheDifferenceToTheParents() throws Exception {
        dir().createDirectory("sub", "drftpd", "drftpd");
        VirtualFileSystemDirectory sub = (VirtualFileSystemDirectory) dir().getInodeByName("sub");
        sub.createFile("a.bin", "drftpd", "drftpd", "slaveA", 100L);
        // out of sync by -30 all the way up, as a lost update would leave it
        sub.addSize(-30L);
        assertEquals(70L, dir().getSize());

        long difference = new DirectoryHandle("/" + DIR + "/sub").validateSizeRecursive();

        assertEquals(-30L, difference);
        assertEquals(100L, sub.getSize());
        assertEquals(100L, dir().getSize());
        assertEquals(_rootBefore + 100L, _root.getSize());

        sub.getInodeByName("a.bin").delete();
        assertEquals(0L, dir().getSize());
        assertEquals(_rootBefore, _root.getSize());
    }

    /**
     * Running FIXSIZE higher up fixes nested errors once, not once per level.
     */
    @Test
    void fixSizeOnANestedTreeCountsEachErrorOnce() throws Exception {
        dir().createDirectory("sub", "drftpd", "drftpd");
        VirtualFileSystemDirectory sub = (VirtualFileSystemDirectory) dir().getInodeByName("sub");
        sub.createFile("a.bin", "drftpd", "drftpd", "slaveA", 100L);
        dir().createFile("b.bin", "drftpd", "drftpd", "slaveA", 10L);
        sub.addSize(25L);   // sub, dir and / too big by 25
        dir().addSize(-7L); // dir and / too small by 7

        long difference = new DirectoryHandle("/" + DIR).validateSizeRecursive();

        assertEquals(18L, difference);
        assertEquals(100L, sub.getSize());
        assertEquals(110L, dir().getSize());
        assertEquals(_rootBefore + 110L, _root.getSize());
    }

    /**
     * Safety net: whatever the cause, a directory size is never negative.
     */
    @Test
    void directorySizeIsClampedAtZero() throws Exception {
        dir().createFile("a.bin", "drftpd", "drftpd", "slaveA", 100L);
        dir().addSize(-4177L);
        assertEquals(0L, dir().getSize());
        assertTrue(_root.getSize() >= 0L);
    }
}
