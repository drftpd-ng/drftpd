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

import org.apache.log4j.BasicConfigurator;


import org.drftpd.tests.DummyBaseFtpConnection;
import org.drftpd.tests.DummyGlobalContext;
import org.drftpd.tests.DummyUser;
import org.drftpd.tests.DummyUserManager;
import org.drftpd.usermanager.User;

import java.util.ArrayList;


/**
 * @author mog
 * @version $Id$
 */
public class UserManagementTest extends TestCase {
    public void testGAdmin() throws ReplyException {
        DummyUserManager um = new DummyUserManager();
        DummyUser u = new DummyUser("dummy", um);
        u.toggleGroup("gadmin");
        um.setUser(u);

        ArrayList<User> allUsers = new ArrayList<User>();
        allUsers.add(u);

        DummyBaseFtpConnection conn = new DummyBaseFtpConnection(null);
        DummyGlobalContext gctx = new DummyGlobalContext();
        gctx.setUserManager(um);
        conn.setGlobalConext(gctx);
        conn.setUser(u);
        u.getKeyedMap().setObject(UserManagement.GROUPSLOTS, ((short) 1));
        u.setGroup("group");

        UserManagement cmdmgr = (UserManagement) new UserManagement().initialize(conn,
                null);

        {
            FtpRequest req = new FtpRequest(
                    "site adduser testadd1 testpass1 *@*");
            conn.setRequest(req);

            Reply r = cmdmgr.execute(conn);
            assertEquals(r.toString(), r.getCode(), 200);
        }

        {
            FtpRequest req = new FtpRequest(
                    "site adduser testadd2 testpass2 *@*");
            conn.setRequest(req);

            Reply r = cmdmgr.execute(conn);
            assertEquals(r.toString(), r.getMessage(),
                "Sorry, no more open slots available");
        }
    }

    protected void setUp() throws Exception {
        BasicConfigurator.configure();
    }

    protected void tearDown() throws Exception {
        super.tearDown();
    }
}
