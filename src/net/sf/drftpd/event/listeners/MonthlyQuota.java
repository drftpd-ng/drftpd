package net.sf.drftpd.event.listeners;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Properties;
import java.util.StringTokenizer;

import net.sf.drftpd.Bytes;
import net.sf.drftpd.event.Event;
import net.sf.drftpd.event.FtpListener;
import net.sf.drftpd.master.ConnectionManager;
import net.sf.drftpd.master.config.FtpConfig;
import net.sf.drftpd.master.config.Permission;

import org.apache.log4j.Logger;

public class MonthlyQuota implements FtpListener {
	private static final short ACTION_DISABLE = 0;
	private static final short ACTION_PURGE = 1;
	
	private static final short PERIOD_DAILY = 0;
	private static final short PERIOD_WEEKLY = 1;
	private static final short PERIOD_MONTHLY = 2;
	
	ArrayList _quotas;
	private class Quota {
		long quota;
		String name;
		String action;
		int period;
		Permission perm;
	}
	private static Logger logger = Logger.getLogger(MonthlyQuota.class);
	private ConnectionManager _cm;

	/**
	 * 
	 */
	public MonthlyQuota() throws FileNotFoundException, IOException {
		super();
	}

	public void actionPerformed(Event event) {
		String cmd = event.getCommand();
		if ("RESETMONTH".equals(cmd)) {
		}
		if ("RESETWEEK".equals(cmd)) {
		}
		if ("RESETDAY".equals(cmd)) {
		}

		//			UserEvent myevent = (UserEvent) event;
		//			if(isExempt(myevent.getUser())) return;
		//			if(checkPassedBytesLeft(myevent.getUser()) > 0) {
		//				logger.info(myevent.getUser().getUsername()+" failed quota");
		//			}
		//myevent.getUser().getUploadedBytes()
		//			IRCListener irclistener;
		//			try {
		//				irclistener = ((IRCListener) _cm.getFtpListener(IRCListener.class));
		//			} catch (ObjectNotFoundException e) {
		//				throw new RuntimeException(e);
		//			}
		//			irclistener.getIRCConnection().sendCommand(new InviteCommand("nick", irclistener.getChannelName()));
	}
//	private boolean isExempt(User user) {
//		if (!perm.check(user))
//			return true;
//
//		Calendar cal = Calendar.getInstance();
//		cal.setTime(new Date(user.getCreated()));
//		if (cal.getTime().after(new Date())) {
//			//user was created after the first day of this month
//			return true;
//		}
//
//		return false;
//	}
//	private long checkPassedBytesLeft(User user) {
//		return limit - user.getUploadedBytesMonth();
//	}
	public void init(ConnectionManager mgr) {
		_cm = mgr;

		Properties props = new Properties();
		try {
			props.load(new FileInputStream("quota.conf"));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		for (int i = 1;; i++) {
			if (props.getProperty(i + ".quota") == null) break;
			
			Quota quota = new Quota();
			quota.action = props.getProperty(i+".action");
			quota.name = props.getProperty(i+".name");
			String period = props.getProperty(i+".period").toLowerCase();
			if("monthly".equals(period)) {
				quota.period = PERIOD_MONTHLY;
			} else if("weekly".equals(period)) {
				quota.period=PERIOD_WEEKLY;
			} else if("daily".equals(period)) {
				quota.period = PERIOD_DAILY;
			} else {
				throw new RuntimeException(new IOException(period+" is not a recognized period"));
			}
			quota.perm = new Permission(
					FtpConfig.makeUsers(
						new StringTokenizer(System.getProperty(i+".perm"))));
			quota.quota = Bytes.parseBytes(props.getProperty("quota"));
		}
	}

	public static void main(String[] args) {
	}
}
