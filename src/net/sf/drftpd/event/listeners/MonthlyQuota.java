package net.sf.drftpd.event.listeners;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Calendar;
import java.util.Date;
import java.util.Properties;
import java.util.StringTokenizer;

import f00f.net.irc.martyr.commands.InviteCommand;

import net.sf.drftpd.Bytes;
import net.sf.drftpd.ObjectNotFoundException;
import net.sf.drftpd.event.Event;
import net.sf.drftpd.event.FtpListener;
import net.sf.drftpd.event.UserEvent;
import net.sf.drftpd.event.irc.IRCListener;
import net.sf.drftpd.master.ConnectionManager;
import net.sf.drftpd.master.config.FtpConfig;
import net.sf.drftpd.master.config.Permission;
import net.sf.drftpd.master.usermanager.User;



public class MonthlyQuota implements FtpListener {
	private ConnectionManager _cm;
	long limit;
	Permission perm;
	/**
	 * 
	 */
	public MonthlyQuota() throws FileNotFoundException, IOException {
		super();
		Properties props = new Properties();
		props.load(new FileInputStream("quota.conf"));
		Bytes.parseBytes(props.getProperty("quota"));
		perm = new Permission(FtpConfig.makeUsers(new StringTokenizer(System.getProperty("perm"))));
	}

	public void actionPerformed(Event event) {
		if("RESETMONTH".equals(event.getCommand())) {
			UserEvent myevent = (UserEvent) event;
			//myevent.getUser().getUploadedBytes()
//			IRCListener irclistener;
//			try {
//				irclistener = ((IRCListener) _cm.getFtpListener(IRCListener.class));
//			} catch (ObjectNotFoundException e) {
//				throw new RuntimeException(e);
//			}
//			irclistener.getIRCConnection().sendCommand(new InviteCommand("nick", irclistener.getChannelName()));
		}
	}
	private boolean isExempt(User user) {
		if(!perm.check(user)) return true;
		//TODO exempt if user was created this month
//		Date date = new Date(user.getCreated());
//		Calendar cal = Calendar.getInstance();
//		cal.get(Calendar.DAY_OF_MONTH);
//		cal.setTime(date);
		return false;
	}
	private long checkPassed(User user) {
		return limit-user.getUploadedBytesMonth();
	}
	public void init(ConnectionManager mgr) {
		_cm = mgr;
	}

	public static void main(String[] args) {
	}
}
