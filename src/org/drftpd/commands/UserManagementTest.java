/*
 * Created on 2004-sep-29
 *
 * To change the template for this generated file go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
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
 * @version $Id: UserManagmentTest.java 851 2004-12-04 13:29:14Z zubov $
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
        u.setGroupSlots((short) 1);
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
