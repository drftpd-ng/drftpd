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
package org.drftpd.commands;

import junit.framework.TestCase;

import net.sf.drftpd.master.FtpRequest;
import net.sf.drftpd.master.SlaveFileException;
import net.sf.drftpd.remotefile.LinkedRemoteFile;
import net.sf.drftpd.remotefile.LinkedRemoteFileInterface;
import net.sf.drftpd.remotefile.MLSTSerialize;
import net.sf.drftpd.remotefile.StaticRemoteFile;

import org.drftpd.sections.def.SectionManager;

import org.drftpd.tests.DummyBaseFtpConnection;
import org.drftpd.tests.DummyConnectionManager;
import org.drftpd.tests.DummyFtpConfig;
import org.drftpd.tests.DummyGlobalContext;
import org.drftpd.tests.DummyRemoteSlave;
import org.drftpd.tests.DummySlaveManager;
import org.drftpd.tests.DummyUser;
import org.drftpd.tests.DummyUserManager;

import java.io.FileNotFoundException;
import java.io.PrintWriter;

import java.rmi.RemoteException;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;


/**
 * @author mog
 * @version $Id$
 */
public class PreTest extends TestCase {
    private DummyConnectionManager _cm;
    private DummyFtpConfig _config;
    private LinkedRemoteFile _root;
    private List _rslave;

    public PreTest(String fName) {
        super(fName);
    }

    private void buildRoot() throws FileNotFoundException {
        assertNotNull(_config);
        _rslave = Collections.singletonList(new DummyRemoteSlave("test", null));
        _root = new LinkedRemoteFile(_config);

        _root.addFile(new StaticRemoteFile(null, "release", "owner", "group", 0));

        LinkedRemoteFileInterface masterdir = _root.getFile("release");
        masterdir.addFile(new StaticRemoteFile(_rslave, "file1", "owner",
                "group", 1000));

        //_section = _root.addFile(new StaticRemoteFile(null, "section",
        //            "drftpd", "drftpd", 0));
    }

    private void checkOwnershipRecursive(LinkedRemoteFileInterface dir) {
        for (Iterator iter = dir.getFiles().iterator(); iter.hasNext();) {
            LinkedRemoteFileInterface file = (LinkedRemoteFileInterface) iter.next();
            assertEquals(file.getUsername(), "drftpd");

            if (file.isDirectory()) {
                checkOwnershipRecursive(file);
            }
        }
    }

    public void testOwnership()
        throws UnhandledCommandException, FileNotFoundException, 
            RemoteException {
        _config = new DummyFtpConfig();
        buildRoot();

        Pre pre = new Pre();
        DummyBaseFtpConnection conn = new DummyBaseFtpConnection(null);
        pre.initialize(conn, null);
        conn.setRequest(new FtpRequest("SITE PRE release section"));
        _cm = new DummyConnectionManager();

        _config.setConnectionManager(_cm);

        SectionManager sm = new SectionManager(_cm);
        conn.setCurrentDirectory(_root);

        DummyUserManager um = new DummyUserManager();
        um.setUser(new DummyUser("owner"));

        DummyGlobalContext gctx = new DummyGlobalContext();
        gctx.setUserManager(um);
        gctx.setSectionManager(sm);
        gctx.setFtpConfig(_config);
        gctx.setRoot(_root);
        gctx.setConnectionManager(_cm);
        _cm.setGlobalContext(gctx);
        conn.setGlobalConext(gctx);

        DummySlaveManager slavem = null;

        try {
            slavem = new DummySlaveManager();
        } catch (SlaveFileException e) {
        }

        slavem.setSlaves(Collections.EMPTY_LIST);
        gctx.setSlaveManager(slavem);

        Reply reply;
        reply = pre.execute(conn);
        MLSTSerialize.serialize(_root, new PrintWriter(System.err, true));
        assertEquals(reply.getCode(), 200);
    }
}
