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

import org.drftpd.master.slavemanagement.DummyRemoteSlave;
import org.drftpd.master.slavemanagement.RemoteSlave;
import org.drftpd.master.tests.DummyGlobalContext;
import org.drftpd.master.tests.DummySlaveManager;
import org.drftpd.slave.exceptions.ObjectNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Merging a directory into an existing one ({@link InodeHandle#renameToUnchecked(InodeHandle)} with both
 * directories in the VFS) must tell every slave that has a file to rename it from its old path.
 */
public class InodeHandleMergeTest {

    /** Records the renames it is asked to do instead of talking to a slave. */
    static class RecordingSlave extends DummyRemoteSlave {
        final List<String> renames = new ArrayList<>();

        RecordingSlave(String name) {
            super(name);
        }

        @Override
        public void simpleRename(String from, String toDirPath, String toName) {
            renames.add(from + " -> " + toDirPath + "/" + toName);
        }
    }

    static class RecordingSlaveManager extends DummySlaveManager {
        final Map<String, RecordingSlave> slaves = new HashMap<>();

        @Override
        public RemoteSlave getRemoteSlave(String s) throws ObjectNotFoundException {
            RecordingSlave rs = slaves.get(s);
            if (rs == null) {
                throw new ObjectNotFoundException(s);
            }
            return rs;
        }

        @Override
        public void deleteOnAllSlaves(DirectoryHandle directory) {
            // nothing to delete on fake slaves
        }
    }

    private RecordingSlaveManager _sm;

    @BeforeEach
    void setUp() {
        DummyGlobalContext gctx = new DummyGlobalContext();
        _sm = new RecordingSlaveManager();
        _sm.slaves.put("slaveA", new RecordingSlave("slaveA"));
        _sm.slaves.put("slaveB", new RecordingSlave("slaveB"));
        gctx.setSlaveManager(_sm);
    }

    @AfterEach
    void tearDown() {
        for (String name : new String[]{"MergeSrc", "MergeDst"}) {
            try {
                new DirectoryHandle("/" + name).deleteUnchecked();
            } catch (FileNotFoundException ignored) {
            }
        }
    }

    @Test
    void mergeRenamesTheFileOnEverySlaveFromItsOldPath() throws Exception {
        VirtualFileSystemDirectory root = VirtualFileSystem.getVirtualFileSystem().getRoot();
        root.createDirectory("MergeSrc", "drftpd", "drftpd");
        root.createDirectory("MergeDst", "drftpd", "drftpd");
        VirtualFileSystemDirectory src = (VirtualFileSystemDirectory) root.getInodeByName("MergeSrc");
        src.createFile("file.bin", "drftpd", "drftpd", "slaveA");
        ((VirtualFileSystemFile) src.getInodeByName("file.bin")).addSlave("slaveB");

        new DirectoryHandle("/MergeSrc").renameToUnchecked(new DirectoryHandle("/MergeDst"));

        assertEquals(List.of("/MergeSrc/file.bin -> /MergeDst/file.bin"), _sm.slaves.get("slaveA").renames);
        assertEquals(List.of("/MergeSrc/file.bin -> /MergeDst/file.bin"), _sm.slaves.get("slaveB").renames);
        FileHandle moved = new FileHandle("/MergeDst/file.bin");
        assertTrue(moved.exists());
        assertEquals(2, moved.getSlaveNames().size());
        assertFalse(new DirectoryHandle("/MergeSrc").exists());
    }
}
