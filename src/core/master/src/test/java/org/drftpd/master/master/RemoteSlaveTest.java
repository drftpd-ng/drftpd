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
package org.drftpd.master.master;

import org.drftpd.common.network.AsyncResponse;
import org.drftpd.master.GlobalContext;
import org.drftpd.master.event.Event;
import org.drftpd.master.exceptions.SlaveUnavailableException;
import org.drftpd.master.slavemanagement.DummyRemoteSlave;
import org.drftpd.master.slavemanagement.RemoteSlave;
import org.drftpd.master.slavemanagement.SlaveManager;
import org.drftpd.master.tests.DummySlaveManager;
import org.drftpd.master.vfs.DirectoryHandle;
import org.junit.jupiter.api.Test;

import java.net.SocketException;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;


/**
 * @author mog
 * @version $Id$
 */
public class RemoteSlaveTest {

    @Test
    public void testEquals() {
        DummySlaveManager sm = new DummySlaveManager();
        GlobalContext gc = new GC();

        //sm.setGlobalContext(gc); -zubov
        ((GC) gc).setSlaveManager(sm);

        RemoteSlave rslave1 = new DummyRemoteSlave("test1");
        RemoteSlave rslave2 = new DummyRemoteSlave("test1");
        RemoteSlave rslave3 = new DummyRemoteSlave("test2");
        assertEquals(rslave1, rslave1);
        assertEquals(rslave1, rslave2);
        assertNotEquals(rslave1, rslave3);
    }

    public void testAddNetworkError()
            throws InterruptedException {
        DummySlaveManager sm = new DummySlaveManager();
        GC gc = new GC();
        //sm.setGlobalContext(gc); -zubov
        gc.setSlaveManager(sm);

        DummyRemoteSlave rslave = new DummyRemoteSlave("test");
        sm.addSlave(rslave);
        rslave.setProperty("errortimeout", "100");
        rslave.setProperty("maxerrors", "2");
        rslave.fakeConnect();
        rslave.setAvailable(true);
        assertTrue(rslave.isAvailable());
        rslave.addNetworkError(new SocketException());
        assertTrue(rslave.isAvailable());
        rslave.addNetworkError(new SocketException());
        assertTrue(rslave.isAvailable());
    }

    public static class GC extends GlobalContext {
        public GC() {
            _gctx = this;
        }

        public SlaveManager getSlaveManager() {
            return super.getSlaveManager();
        }

        public void setSlaveManager(SlaveManager sm) {
            _slaveManager = sm;
        }

        public void dispatchFtpEvent(Event event) { }

        public DirectoryHandle getRoot() {
            System.out.println("new lrf");

            return new DirectoryHandle("/");
        }
    }

    public static class RemergeRemoteSlave extends RemoteSlave {
        private HashSet<String> _filelist = null;

        public RemergeRemoteSlave(String name) {
            super(name);
        }

        /**
         * @param filelist
         */
        public void setFileList(HashSet<String> filelist) {
            _filelist = filelist;
        }

        public String issueDeleteToSlave(String sourceFile) {
            _filelist.remove(sourceFile);

            return null;
        }

        public String issueRenameToSlave(String from, String toDirPath,
                                         String toName) {
            _filelist.remove(from);
            _filelist.add(toDirPath + "/" + toName);

            return null;
        }

        public void simpleDelete(String path) {
            addQueueDelete(path);
        }

        public void simpleRename(String from, String toDirPath, String toName) {
            addQueueRename(from, toDirPath + "/" + toName);
        }

        public AsyncResponse fetchResponse(String index)
                throws SlaveUnavailableException {
            return null;
        }
    }
}
