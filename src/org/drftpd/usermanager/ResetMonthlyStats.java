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
package org.drftpd.usermanager;

import net.sf.drftpd.master.ConnectionManager;

import org.apache.log4j.BasicConfigurator;

import org.drftpd.slave.Slave;

import org.drftpd.tests.DummyGlobalContext;

import java.io.FileInputStream;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import java.util.Date;
import java.util.Iterator;
import java.util.Properties;


/**
 * @author mog
 * @version $Id: ResetMonthlyStats.java,v 1.7 2004/11/03 16:46:49 mog Exp $
 */
public class ResetMonthlyStats {
    public ResetMonthlyStats() {
        super();
    }

    public static void main(String[] args) throws Exception {
        System.out.println(Slave.VERSION + " resetmonth starting.");
        System.out.println("All your sites are belong to mog ^^");
        System.out.println("http://drftpd.org/");
        BasicConfigurator.configure();

        String cfgFileName;

        if (args.length >= 1) {
            cfgFileName = args[0];
        } else {
            cfgFileName = "drftpd.conf";
        }

        /** load master config **/
        Properties cfg = new Properties();
        cfg.load(new FileInputStream(cfgFileName));

        CM cm = new CM(cfg, cfgFileName);
        cm.getGlobalContext().getUserManager().getAllUsers();

        Method m = AbstractUser.class.getDeclaredMethod("resetMonth",
                new Class[] { ConnectionManager.class, Date.class });
        m.setAccessible(true);

        Field f = AbstractUser.class.getDeclaredField("lastReset");
        f.setAccessible(true);

        for (Iterator iter = cm.getGlobalContext().getUserManager().getAllUsers()
                               .iterator(); iter.hasNext();) {
            AbstractUser user = (AbstractUser) iter.next();
            f.setLong(user, System.currentTimeMillis());

            //user.resetMonth(cm, new Date())
            m.invoke(user, new Object[] { cm, new Date() });
            user.commit();
        }

        f.setAccessible(false);
        m.setAccessible(false);
    }

    public static class CM extends ConnectionManager {
        private DummyGlobalContext _gctx;

        public CM(Properties cfg, String cfgFileName) {
            _gctx = new DummyGlobalContext();
            super._gctx = _gctx;
            _gctx.loadUserManager(cfg, cfgFileName);
            _gctx.loadPlugins(cfg);
        }
    }
}
