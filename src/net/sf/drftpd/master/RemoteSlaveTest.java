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
package net.sf.drftpd.master;

import junit.framework.TestCase;
import junit.framework.TestSuite;

import net.sf.drftpd.SlaveUnavailableException;
import net.sf.drftpd.event.Event;
import net.sf.drftpd.remotefile.LinkedRemoteFileInterface;

import org.drftpd.GlobalContext;

import org.drftpd.remotefile.AbstractLinkedRemoteFile;
import org.drftpd.remotefile.CaseInsensitiveHashtable;

import org.drftpd.slave.async.AsyncResponse;

import org.drftpd.tests.DummyRemoteSlave;
import org.drftpd.tests.DummySlaveManager;

import java.io.IOException;

import java.net.SocketException;

import java.util.HashSet;
import java.util.Set;


/**
 * @author mog
 * @version $Id: RemoteSlaveTest.java,v 1.17 2004/11/09 18:59:47 mog Exp $
 */
public class RemoteSlaveTest extends TestCase {
    public RemoteSlaveTest(String fName) {
        super(fName);
    }

    public static TestSuite suite() {
        return new TestSuite(RemoteSlaveTest.class);
    }

    public void testEquals() throws SlaveFileException {
        DummySlaveManager sm = new DummySlaveManager();
        GlobalContext gc = new GC();
        sm.setGlobalContext(gc);
        ((GC) gc).setSlaveManager(sm);

        RemoteSlave rslave1 = new DummyRemoteSlave("test1", gc);
        RemoteSlave rslave2 = new DummyRemoteSlave("test1", gc);
        RemoteSlave rslave3 = new DummyRemoteSlave("test2", gc);
        assertTrue(rslave1.equals(rslave1));
        assertTrue(rslave1.equals(rslave2));
        assertFalse(rslave1.equals(rslave3));
    }

    public void testProcessQueue()
        throws SlaveFileException, IOException, SlaveUnavailableException {
        DummySlaveManager sm = new DummySlaveManager();
        GC gc = new GC();
        sm.setGlobalContext(gc);
        gc.setSlaveManager(sm);

        RemergeRemoteSlave rslave = new RemergeRemoteSlave("test", gc);
        sm.addSlave(rslave);

        rslave.simpleDelete("/deleteme");
        rslave.simpleRename("/renameme", "/indir", "tofile");

        HashSet filelist = new HashSet();
        filelist.add("/deleteme");
        filelist.add("/renameme");
        filelist.add("/indir");

        rslave.setFileList(filelist);
        rslave.processQueue();

        assertFalse(filelist.contains("/deleteme"));
        assertFalse(filelist.contains("/renameme"));
        assertTrue(filelist.contains("/indir"));
        assertTrue(filelist.contains("/indir/tofile"));
    }

    public void testAddNetworkError()
        throws InterruptedException, SlaveFileException {
        DummySlaveManager sm = new DummySlaveManager();
        GC gc = new GC();
        sm.setGlobalContext(gc);
        gc.setSlaveManager(sm);

        DummyRemoteSlave rslave = new DummyRemoteSlave("test", gc);
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
        Thread.sleep(100);
        rslave.addNetworkError(new SocketException());
        assertTrue(rslave.isAvailable());
        rslave.addNetworkError(new SocketException());
        assertFalse(rslave.isAvailable());
    }

    public class LRF extends AbstractLinkedRemoteFile {
        public void cleanSlaveFromMerging(RemoteSlave slave) {
        }

        public void resetSlaveForMerging(RemoteSlave slave) {
        }

        public void setSlaveForMerging(RemoteSlave rslave) {
        }

        public void deleteOthers(Set destSlaves) {
        }

        /* (non-Javadoc)
         * @see net.sf.drftpd.remotefile.LinkedRemoteFileInterface#remerge(net.sf.drftpd.remotefile.LinkedRemoteFile.CaseInsensitiveHashtable, net.sf.drftpd.master.RemoteSlave)
         */
        public void remerge(CaseInsensitiveHashtable lightRemoteFiles,
            RemoteSlave rslave) throws IOException {
            // TODO Auto-generated method stub
        }
    }

    public class GC extends GlobalContext {
        public SlaveManager getSlaveManager() {
            return super.getSlaveManager();
        }

        public void dispatchFtpEvent(Event event) {
        }

        public void setSlaveManager(SlaveManager sm) {
            _slaveManager = sm;
        }

        public LinkedRemoteFileInterface getRoot() {
            System.out.println("new lrf");

            return new LRF();
        }
    }

    public class RemergeRemoteSlave extends RemoteSlave {
        private HashSet _filelist = null;

        public RemergeRemoteSlave(String name, GlobalContext gctx) {
            super(name, gctx);
        }

        /**
         * @param filelist
         */
        public void setFileList(HashSet filelist) {
            _filelist = filelist;
        }

        public String issueDeleteToSlave(String sourceFile)
            throws SlaveUnavailableException {
            _filelist.remove(sourceFile);

            return null;
        }

        public String issueRenameToSlave(String from, String toDirPath,
            String toName) throws SlaveUnavailableException {
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
