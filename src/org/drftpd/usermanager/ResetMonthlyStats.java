/*
 * Created on Jun 1, 2004
 *
 * To change the template for this generated file go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
package org.drftpd.usermanager;

import java.io.FileInputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Date;
import java.util.Iterator;
import java.util.Properties;

import net.sf.drftpd.master.ConnectionManager;
import net.sf.drftpd.master.usermanager.AbstractUser;
import net.sf.drftpd.slave.SlaveImpl;

import org.apache.log4j.BasicConfigurator;

/**
 * @author mog
 * @version $Id: ResetMonthlyStats.java,v 1.2 2004/06/02 00:32:43 mog Exp $
 */
public class ResetMonthlyStats {

	public ResetMonthlyStats() {
		super();
	}

	public static void main(String[] args) throws Exception {
		System.out.println(SlaveImpl.VERSION + " resetmonth starting.");
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
		cm.getUserManager().getAllUsers();
		Method m =
			AbstractUser.class.getDeclaredMethod(
				"resetMonth",
				new Class[] { ConnectionManager.class, Date.class });
		m.setAccessible(true);

		Field f = AbstractUser.class.getDeclaredField("lastReset");
		f.setAccessible(true);

		for (Iterator iter = cm.getUserManager().getAllUsers().iterator();
			iter.hasNext();
			) {
			AbstractUser user = (AbstractUser) iter.next();
			f.setLong(user, System.currentTimeMillis());
			//user.resetMonth(cm, new Date())
			m.invoke(user, new Object[] { cm, new Date()});
			user.commit();
		}
		f.setAccessible(false);
		m.setAccessible(false);
	}

	public static class CM extends ConnectionManager {
		public CM(Properties cfg, String cfgFileName) {
			loadUserManager(cfg, cfgFileName);
			loadPlugins(cfg);
		}
	}
}
