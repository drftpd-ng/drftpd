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
import org.drftpd.master.exceptions.NoAvailableSlaveException;
import org.drftpd.master.slavemanagement.RemoteSlave;
import org.drftpd.slave.exceptions.FileExistsException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for #328: a slave reporting a file as missing while the master asks for its
 * checksum (e.g. during a nuke, delete, move or wipe) must not be set offline.
 */
public class FileHandleChecksumTest {

    private static VirtualFileSystem vfs;

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

    /**
     * A slave whose checksum reply is scripted by the test and which records whether it was set offline.
     */
    static class StubSlave extends RemoteSlave {
        private final IOException _failure;
        private final long _checksum;
        boolean wentOffline = false;
        int checksumCalls = 0;

        StubSlave(String name, long checksum) {
            super(name);
            _failure = null;
            _checksum = checksum;
        }

        StubSlave(String name, IOException failure) {
            super(name);
            _failure = failure;
            _checksum = 0L;
        }

        @Override
        public long getCheckSumForPath(String path) throws IOException {
            checksumCalls++;
            if (_failure != null) {
                throw _failure;
            }
            return _checksum;
        }

        @Override
        public void setOffline(Throwable t, long generation) {
            wentOffline = true;
        }
    }

    /**
     * A FileHandle that uses the given slaves, in order, instead of asking the SlaveManager.
     */
    static class StubFileHandle extends FileHandle {
        private final List<StubSlave> _slaves;

        StubFileHandle(String path, StubSlave... slaves) {
            super(path);
            _slaves = Arrays.asList(slaves);
        }

        @Override
        public Collection<RemoteSlave> getAvailableSlaves() throws NoAvailableSlaveException {
            List<RemoteSlave> available = new ArrayList<>();
            for (StubSlave slave : _slaves) {
                if (!slave.wentOffline) {
                    available.add(slave);
                }
            }
            if (available.isEmpty()) {
                throw new NoAvailableSlaveException();
            }
            return available;
        }
    }

    private static String createFile(String name) throws FileExistsException {
        vfs.getRoot().createFile(name, "drftpd", "drftpd", "slave1", 1024L);
        return "/" + name;
    }

    @Test
    public void testMissingOnOneSlaveUsesNextSlave() throws Exception {
        String path = createFile("crcMissingOnFirst");
        StubSlave missing = new StubSlave("slave1", new FileNotFoundException(path + " wasn't found in any root"));
        StubSlave good = new StubSlave("slave2", 0xCAFEL);

        long checksum = new StubFileHandle(path, missing, good).getCheckSumFromSlave();

        assertEquals(0xCAFEL, checksum);
        assertFalse(missing.wentOffline, "A slave that is missing the file must not be set offline");
        assertFalse(good.wentOffline);
        assertEquals(1, missing.checksumCalls, "The slave missing the file should only be asked once");
    }

    @Test
    public void testMissingOnAllSlavesThrowsFileNotFound() throws Exception {
        String path = createFile("crcMissingEverywhere");
        StubSlave first = new StubSlave("slave1", new FileNotFoundException(path + " wasn't found in any root"));
        StubSlave second = new StubSlave("slave2", new FileNotFoundException(path + " wasn't found in any root"));

        assertThrows(FileNotFoundException.class,
                () -> new StubFileHandle(path, first, second).getCheckSumFromSlave());

        assertFalse(first.wentOffline, "A slave that is missing the file must not be set offline");
        assertFalse(second.wentOffline, "A slave that is missing the file must not be set offline");
    }

    @Test
    public void testOtherIOErrorStillSetsSlaveOffline() throws Exception {
        String path = createFile("crcIOError");
        StubSlave broken = new StubSlave("slave1", new IOException("Input/output error"));
        StubSlave good = new StubSlave("slave2", 0xBEEFL);

        long checksum = new StubFileHandle(path, broken, good).getCheckSumFromSlave();

        assertEquals(0xBEEFL, checksum);
        assertTrue(broken.wentOffline, "A real I/O error should still set the slave offline");
        assertFalse(good.wentOffline);
    }
}
