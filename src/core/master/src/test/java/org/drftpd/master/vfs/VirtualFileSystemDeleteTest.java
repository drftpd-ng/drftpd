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

import java.io.File;
import java.io.FileNotFoundException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Deleting a directory must remove the stored json of everything below it for good: children that are
 * still queued in the CommitManager (or committed later) must not re-create the directory on disk, else it
 * comes back after a master restart (SITE WIPE -r, the zipscript audio rules delete, RMD of a release).
 */
public class VirtualFileSystemDeleteTest {

    private static final String DIR = "DeleteTest";
    private static final String RENAMED = "DeleteTestRenamed";

    private VirtualFileSystemDirectory _root;

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
        deleteIfPresent(DIR);
        deleteIfPresent(RENAMED);
        _root.createDirectory(DIR, "drftpd", "drftpd");
    }

    @AfterEach
    void tearDown() {
        deleteIfPresent(DIR);
        deleteIfPresent(RENAMED);
    }

    private void deleteIfPresent(String name) {
        try {
            _root.getInodeByName(name).delete();
        } catch (FileNotFoundException ignored) {
        }
    }

    private static File onDisk(String path) {
        return new File(VirtualFileSystem.fileSystemPath + path);
    }

    private static VirtualFileSystemDirectory dir(VirtualFileSystemDirectory parent, String name)
            throws FileNotFoundException {
        return (VirtualFileSystemDirectory) parent.getInodeByName(name);
    }

    /**
     * The children of a just raced release are still in the commit queue when the release is wiped; the
     * commit thread wrote them afterwards and re-created the directory with their json files.
     */
    @Test
    void queuedChildrenAreNotWrittenAfterTheirDirectoryIsDeleted() throws Exception {
        VirtualFileSystemDirectory release = dir(_root, DIR);
        release.createFile("a.rar", "drftpd", "drftpd", "slaveA", 100L);
        release.createDirectory("Sample", "drftpd", "drftpd");
        VirtualFileSystemDirectory sample = dir(release, "Sample");
        sample.createFile("s.mkv", "drftpd", "drftpd", "slaveA", 10L);
        VirtualFileSystemInode file = release.getInodeByName("a.rar");
        VirtualFileSystemInode sampleFile = sample.getInodeByName("s.mkv");
        // Everything is on disk, as after the commit thread ran once
        release.writeToDisk();
        file.writeToDisk();
        sample.writeToDisk();
        sampleFile.writeToDisk();
        assertTrue(onDisk("/" + DIR + "/Sample/s.mkv").exists());

        release.delete();
        assertFalse(onDisk("/" + DIR).exists());

        // What the commit thread (or a flush) does with the entries still queued
        file.writeToDisk();
        sample.writeToDisk();
        sampleFile.writeToDisk();
        CommitManager.getCommitManager().writeImmediately(sampleFile);

        assertFalse(onDisk("/" + DIR).exists(), "a child written after the delete re-created the directory");
        assertFalse(_root.getInodeNames().contains(DIR));
    }

    /**
     * A transfer or a plugin that still holds a child can change it (and commit it) after the delete.
     */
    @Test
    void childrenChangedAfterTheDeleteAreNotQueued() throws Exception {
        VirtualFileSystemDirectory release = dir(_root, DIR);
        release.createFile("a.rar", "drftpd", "drftpd", "slaveA", 100L);
        VirtualFileSystemInode file = release.getInodeByName("a.rar");
        CommitManager.getCommitManager().remove(file);

        release.delete();
        file.setLastModified(file.getLastModified() + 1000L);

        assertFalse(CommitManager.getCommitManager().contains(file));
        file.writeToDisk();
        assertFalse(onDisk("/" + DIR).exists());
    }

    /**
     * A stale child of a deleted release must not write itself into a new release with the same name.
     */
    @Test
    void staleChildIsNotWrittenIntoANewDirectoryWithTheSameName() throws Exception {
        VirtualFileSystemDirectory old = dir(_root, DIR);
        old.createFile("a.rar", "drftpd", "drftpd", "slaveA", 100L);
        VirtualFileSystemInode staleFile = old.getInodeByName("a.rar");
        old.delete();

        _root.createDirectory(DIR, "drftpd", "drftpd");
        dir(_root, DIR).writeToDisk();
        staleFile.writeToDisk();

        assertTrue(onDisk("/" + DIR + "/" + VirtualFileSystem.dirName).exists());
        assertFalse(onDisk("/" + DIR + "/a.rar").exists(), "the stale file was written into the new directory");
    }

    /**
     * Deleting a file only stops that file: its directory and siblings are still written.
     */
    @Test
    void deletingAFileKeepsWritingItsDirectoryAndSiblings() throws Exception {
        VirtualFileSystemDirectory release = dir(_root, DIR);
        release.createFile("a.rar", "drftpd", "drftpd", "slaveA", 100L);
        release.createFile("b.rar", "drftpd", "drftpd", "slaveA", 100L);
        VirtualFileSystemInode a = release.getInodeByName("a.rar");
        VirtualFileSystemInode b = release.getInodeByName("b.rar");
        a.delete();

        assertTrue(CommitManager.getCommitManager().contains(release));
        release.writeToDisk();
        b.writeToDisk();
        a.writeToDisk();

        assertTrue(onDisk("/" + DIR + "/" + VirtualFileSystem.dirName).exists());
        assertTrue(onDisk("/" + DIR + "/b.rar").exists());
        assertFalse(onDisk("/" + DIR + "/a.rar").exists());
    }

    /**
     * A renamed directory is not deleted: its children are still committed and written at the new path.
     */
    @Test
    void renamedDirectoryChildrenAreStillWritten() throws Exception {
        VirtualFileSystemDirectory release = dir(_root, DIR);
        release.createFile("a.rar", "drftpd", "drftpd", "slaveA", 100L);
        VirtualFileSystemInode file = release.getInodeByName("a.rar");
        release.writeToDisk();
        file.writeToDisk();
        CommitManager.getCommitManager().remove(file);

        release.rename("/" + RENAMED);
        file.setLastModified(file.getLastModified() + 1000L);
        assertTrue(CommitManager.getCommitManager().contains(file));
        file.writeToDisk();

        assertTrue(onDisk("/" + RENAMED + "/a.rar").exists());
        assertFalse(onDisk("/" + DIR).exists());
    }
}
