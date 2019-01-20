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
package org.drftpd.master;

import junit.framework.TestCase;
import junit.framework.TestSuite;
import org.drftpd.GlobalContext;
import org.drftpd.event.Event;
import org.drftpd.exceptions.SlaveUnavailableException;
import org.drftpd.slave.async.AsyncResponse;
import org.drftpd.tests.DummyRemoteSlave;
import org.drftpd.tests.DummySlaveManager;
import org.drftpd.vfs.DirectoryHandle;
import org.junit.Assert;

import java.io.IOException;
import java.net.SocketException;
import java.util.HashSet;


/**
 * @author mog
 * @version $Id$
 */
public class RemoteSlaveTest extends TestCase {
    public RemoteSlaveTest(String fName) {
        super(fName);
    }

    public static TestSuite suite() {
        return new TestSuite(RemoteSlaveTest.class);
    }

    public void testEquals() {
        DummySlaveManager sm = new DummySlaveManager();
        GlobalContext gc = new GC();
        
        //sm.setGlobalContext(gc); -zubov
        ((GC) gc).setSlaveManager(sm);

        RemoteSlave rslave1 = new DummyRemoteSlave("test1");
        RemoteSlave rslave2 = new DummyRemoteSlave("test1");
        RemoteSlave rslave3 = new DummyRemoteSlave("test2");
        Assert.assertTrue(rslave1.equals(rslave1));
        Assert.assertTrue(rslave1.equals(rslave2));
        Assert.assertFalse(rslave1.equals(rslave3));
    }

    public void testProcessQueue()
        throws IOException, SlaveUnavailableException {
        DummySlaveManager sm = new DummySlaveManager();
        GC gc = new GC();
        //sm.setGlobalContext(gc); -zubov
        gc.setSlaveManager(sm);

        RemergeRemoteSlave rslave = new RemergeRemoteSlave("test", gc);
        sm.addSlave(rslave);

        rslave.simpleDelete("/deleteme");
        rslave.simpleRename("/renameme", "/indir", "tofile");

        HashSet<String> filelist = new HashSet<>();
        filelist.add("/deleteme");
        filelist.add("/renameme");
        filelist.add("/indir");

        rslave.setFileList(filelist);
        rslave.processQueue();

        Assert.assertFalse(filelist.contains("/deleteme"));
        Assert.assertFalse(filelist.contains("/renameme"));
        Assert.assertTrue(filelist.contains("/indir"));
        Assert.assertTrue(filelist.contains("/indir/tofile"));
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
        Assert.assertTrue(rslave.isAvailable());
        rslave.addNetworkError(new SocketException());
        Assert.assertTrue(rslave.isAvailable());
        rslave.addNetworkError(new SocketException());
        Assert.assertTrue(rslave.isAvailable());
        Thread.sleep(100);
        rslave.addNetworkError(new SocketException());
        Assert.assertTrue(rslave.isAvailable());
        rslave.addNetworkError(new SocketException());
        Assert.assertFalse(rslave.isAvailable());
    }

    public static class GC extends GlobalContext {
        public SlaveManager getSlaveManager() {
            return super.getSlaveManager();
        }

        public void dispatchFtpEvent(Event event) {
        }

        public void setSlaveManager(SlaveManager sm) {
            _slaveManager = sm;
        }

        public DirectoryHandle getRoot() {
            System.out.println("new lrf");

            return new DirectoryHandle("/");
        }
    }

    public static class RemergeRemoteSlave extends RemoteSlave {
        private HashSet<String> _filelist = null;

        public RemergeRemoteSlave(String name, GlobalContext gctx) {
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
            _filelist.add(new String(toDirPath + "/" + toName));

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
