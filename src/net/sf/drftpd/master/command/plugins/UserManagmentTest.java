/*
 * Created on 2004-sep-29
 *
 * To change the template for this generated file go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
package net.sf.drftpd.master.command.plugins;

import junit.framework.TestCase;

import net.sf.drftpd.master.FtpReply;
import net.sf.drftpd.master.FtpRequest;

import org.apache.log4j.BasicConfigurator;

import org.drftpd.commands.UnhandledCommandException;

import org.drftpd.tests.DummyBaseFtpConnection;
import org.drftpd.tests.DummyGlobalContext;
import org.drftpd.tests.DummyUser;
import org.drftpd.tests.DummyUserManager;

import java.util.ArrayList;


/**
 * @author mog
 * @version $Id: UserManagmentTest.java,v 1.1 2004/10/03 16:13:52 mog Exp $
 */
public class UserManagmentTest extends TestCase {
    public void testGAdmin() throws UnhandledCommandException {
        DummyUserManager um = new DummyUserManager();
        DummyUser u = new DummyUser("dummy", um);
        u.toggleGroup("gadmin");
        um.setUser(u);

        ArrayList allUsers = new ArrayList();
        allUsers.add(u);

        DummyBaseFtpConnection conn = new DummyBaseFtpConnection(null);
        DummyGlobalContext gctx = new DummyGlobalContext();
        gctx.setUserManager(um);
        conn.setGlobalConext(gctx);
        conn.setUser(u);
        u.setGroupSlots((short) 1);
        u.setGroup("group");

        UserManagment cmdmgr = (UserManagment) new UserManagment().initialize(conn,
                null);

        {
            FtpRequest req = new FtpRequest(
                    "site adduser testadd1 testpass1 *@*");
            conn.setRequest(req);

            FtpReply r = cmdmgr.execute(conn);
            assertEquals(r.toString(), r.getCode(), 200);
        }

        {
            FtpRequest req = new FtpRequest(
                    "site adduser testadd2 testpass2 *@*");
            conn.setRequest(req);

            FtpReply r = cmdmgr.execute(conn);
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
