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
package net.sf.drftpd.master.command.plugins;

import junit.framework.TestCase;
import junit.framework.TestSuite;

import net.sf.drftpd.DuplicateElementException;
import net.sf.drftpd.master.BaseFtpConnection;
import net.sf.drftpd.master.FtpReply;
import net.sf.drftpd.master.FtpRequest;
import net.sf.drftpd.master.config.FtpConfig;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Logger;

import org.drftpd.commands.UnhandledCommandException;

import org.drftpd.tests.DummyBaseFtpConnection;
import org.drftpd.tests.DummyConnectionManager;
import org.drftpd.tests.DummyGlobalContext;
import org.drftpd.tests.DummyUser;
import org.drftpd.tests.DummyUserManager;

import org.drftpd.usermanager.User;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.net.InetAddress;
import java.net.UnknownHostException;

import java.util.Properties;


/**
 * @author mog
 * @version $Id: LoginTest.java,v 1.14 2004/11/05 15:56:56 zubov Exp $
 */
public class LoginTest extends TestCase {
    private static final Logger logger = Logger.getLogger(LoginTest.class);
    private DummyUser _user;
    private DummyUserManager _userManager;
    private DummyBaseFtpConnection _conn;
    private Login _login;

    public LoginTest(String name) {
        super(name);
    }

    public static TestSuite suite() {
        return new TestSuite(LoginTest.class);
    }

    private void internalSetUp() {
        _login = (Login) new Login().initialize(null, null);
        _conn = new DummyBaseFtpConnection(null);
        _userManager = new DummyUserManager();
        _user = new DummyUser("myuser", _userManager);
        _userManager.setUser(_user);

        FC fc = new FC();
        DummyGlobalContext gctx = new DummyGlobalContext();
        gctx.setFtpConfig(fc);
        gctx.setUserManager(_userManager);

        DummyConnectionManager cm = new DummyConnectionManager() {
                public FtpReply canLogin(BaseFtpConnection baseconn, User user) {
                    return null;
                }
            };

        cm.setGlobalContext(gctx);
        gctx.setConnectionManager(cm);
        _conn.setGlobalConext(gctx);

        //_conn.setConnectionManager(cm);
    }

    public void setUp() {
        BasicConfigurator.configure();
    }

    public void testUSER()
        throws UnknownHostException, UnhandledCommandException, 
            DuplicateElementException {
        internalSetUp();
        _conn.setClientAddress(InetAddress.getByName("127.0.0.1"));

        FtpReply reply;
        _conn.setRequest(new FtpRequest("USER myuser"));
        reply = _login.execute(_conn);
        assertEquals(530, reply.getCode());
        assertNull(_conn.getUserNull());

        _user.addIPMask("*@1.2.3.4");
        reply = _login.execute(_conn);
        assertEquals(530, reply.getCode());
        assertNull(_conn.getUserNull());

        _user.addIPMask("*@127.0.0.1");
        reply = _login.execute(_conn);
        assertEquals(331, reply.getCode());
        assertNotNull(_conn.getUserNull());
    }

    public void testIDNT()
        throws UnhandledCommandException, DuplicateElementException, 
            UnknownHostException {
        internalSetUp();
        _conn.setClientAddress(InetAddress.getByName("10.0.0.2"));
        _conn.setRequest(new FtpRequest("IDNT user@127.0.0.1:localhost"));

        FtpReply reply;
        reply = _login.execute(_conn);
        assertNotNull(reply);
        assertEquals(530, reply.getCode());
        assertNull(_login._idntAddress);

        internalSetUp();
        _conn.setClientAddress(InetAddress.getByName("10.0.0.1"));
        _conn.setRequest(new FtpRequest("IDNT user@127.0.0.1:localhost"));
        reply = _login.execute(_conn);
        assertNull(String.valueOf(reply), reply);

        _conn.setRequest(new FtpRequest("USER myuser"));

        _user.addIPMask("*@127.0.0.0"); //invalid
        reply = _login.execute(_conn);
        assertEquals(530, reply.getCode());
        assertNull(_conn.getUserNull());
        logger.debug(reply.toString());

        _user.addIPMask("*@127.0.0.1"); //what was given in IDNT cmd
        reply = _login.execute(_conn);
        assertEquals(331, reply.getCode());
        assertEquals(_user, _conn.getUserNull());
        logger.debug(reply.toString());
    }

    public static class FC extends FtpConfig {
        public FC() {
            Properties cfg = new Properties();
            cfg.setProperty("bouncer_ip", "10.0.1.1 10.0.0.1");
            Reader r = new StringReader("shutdown !*");
            cfg.setProperty("shutdown", "!*");
            try {
                loadConfig1(cfg);
                loadConfig2(r);
            } catch (UnknownHostException e) {
                throw new RuntimeException(e);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        //		public List getBouncerIps() {
        //			try {
        //				return Arrays.asList(
        //					new InetAddress[] {
        //						InetAddress.getByName("10.0.1.1"),
        //						InetAddress.getByName("10.0.0.1")});
        //			} catch (UnknownHostException e) {
        //				throw new RuntimeException(e);
        //			}
        //		}
    }
}
