package net.sf.drftpd.event.listeners;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Iterator;
import java.util.Properties;
import java.util.StringTokenizer;

import net.sf.drftpd.Bytes;
import net.sf.drftpd.ObjectNotFoundException;
import net.sf.drftpd.event.Event;
import net.sf.drftpd.event.FtpListener;
import net.sf.drftpd.event.UserEvent;
import net.sf.drftpd.event.irc.IRCListener;
import net.sf.drftpd.master.ConnectionManager;
import net.sf.drftpd.master.config.FtpConfig;
import net.sf.drftpd.master.config.Permission;
import net.sf.drftpd.master.usermanager.NoSuchUserException;
import net.sf.drftpd.master.usermanager.User;

import org.apache.log4j.Logger;

import f00f.net.irc.martyr.GenericCommandAutoService;
import f00f.net.irc.martyr.InCommand;
import f00f.net.irc.martyr.commands.MessageCommand;

public class MonthlyQuota implements FtpListener {
	private SiteBot _siteBot;
	private static final short ACTION_DISABLE = 0;
	private static final short ACTION_PURGE = 1;

	private static final short PERIOD_DAILY = 0;
	private static final short PERIOD_WEEKLY = 1;
	private static final short PERIOD_MONTHLY = 2;

	ArrayList _quotas;
	private static final Logger logger = Logger.getLogger(MonthlyQuota.class);
	private ConnectionManager _cm;

	/**
	 * 
	 */
	public MonthlyQuota() throws FileNotFoundException, IOException {
		super();
	}

	public void actionPerformed(Event event) {
		if (!(event instanceof UserEvent))
			return;
		UserEvent uevent = (UserEvent) event;
		String cmd = event.getCommand();
		if ("RESETMONTH".equals(cmd)) {
			checkPassed(uevent.getUser(), PERIOD_MONTHLY);
		}
		if ("RESETWEEK".equals(cmd)) {
			checkPassed(uevent.getUser(), PERIOD_WEEKLY);
		}
		if ("RESETDAY".equals(cmd)) {
			checkPassed(uevent.getUser(), PERIOD_DAILY);
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
	/**
		 * @param user
		 * @param PERIOD_MONTHLY
		 */
	private void checkPassed(User user, short period) {
		for (Iterator iter = _quotas.iterator(); iter.hasNext();) {
			Quota quota = (Quota) iter.next();
			if (quota.period == period) {
				long bytesleft =
					quota.quota - getUploadedBytesForPeriod(user, period);
				if (bytesleft > 0) {
					logger.info(
						user.getUsername()
							+ " failed quota by "
							+ bytesleft
							+ " bytes ("
							+ Bytes.formatBytes(bytesleft)
							+ ")");
				} else {
					logger.info(
						user.getUsername()
							+ " passed quota with "
							+ (-bytesleft)
							+ " extra ("
							+ Bytes.formatBytes(-bytesleft)
							+ ")");
				}
			}
		}
	}

	public static long getUploadedBytesForPeriod(User user, short period) {
		switch (period) {
			case PERIOD_DAILY :
				return user.getDownloadedBytesDay();
			case PERIOD_WEEKLY :
				return user.getDownloadedBytesWeek();
			case PERIOD_MONTHLY :
				return user.getDownloadedBytesMonth();
			default :
				throw new IllegalArgumentException();
		}
	}
	
	public static Calendar getCalendarForEndOfPeriod(short period) {
		Calendar cal = Calendar.getInstance();
		cal.set(Calendar.MILLISECOND, 0);
		cal.set(Calendar.SECOND, 0);
		cal.set(Calendar.MINUTE, 0);
		if(period == PERIOD_DAILY) {
		}
		cal.set(Calendar.HOUR_OF_DAY, 0);
		
		throw new IllegalArgumentException();
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
	ConnectionManager getConnectionManager() {
		return _cm;
	}
	public void init(ConnectionManager mgr) {
		_cm = mgr;

		Properties props = new Properties();
		try {
			props.load(new FileInputStream("quota.conf"));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		_quotas = new ArrayList();
		for (int i = 1;; i++) {
			if (props.getProperty(i + ".quota") == null)
				break;

			Quota quota = new Quota();
			quota.action = props.getProperty(i + ".action");
			quota.name = props.getProperty(i + ".name");
			String period = props.getProperty(i + ".period").toLowerCase();
			if ("monthly".equals(period)) {
				quota.period = PERIOD_MONTHLY;
			} else if ("weekly".equals(period)) {
				quota.period = PERIOD_WEEKLY;
			} else if ("daily".equals(period)) {
				quota.period = PERIOD_DAILY;
			} else {
				throw new RuntimeException(
					new IOException(period + " is not a recognized period"));
			}
			String perm = props.getProperty(i + ".perm");
			assert perm != null : i;
			quota.perm =
				new Permission(FtpConfig.makeUsers(new StringTokenizer(perm)));
			quota.quota = Bytes.parseBytes(props.getProperty(i + ".quota"));
			_quotas.add(quota);
		}

		//TODO register SiteBot component
		try {
			IRCListener _irc =
				(IRCListener) _cm.getFtpListener(IRCListener.class);
			_siteBot = new SiteBot(_irc, this);
		} catch (ObjectNotFoundException e1) {
			logger.warn("Error loading sitebot component", e1);
		}
	}

	/**
	 * 
	 */
	public ArrayList getQuotas() {
		return _quotas;
	}
	class Quota {
		long quota;
		String name;
		String action;
		short period;
		Permission perm;
	}

	class SiteBot extends GenericCommandAutoService {

		//private static final Logger logger = Logger.getLogger(SiteBot.class);

		private IRCListener _irc;

		private MonthlyQuota _parent;

		/**
		 * @param connection
		 */
		protected SiteBot(IRCListener irc, MonthlyQuota parent) {
			super(irc.getIRCConnection());
			_irc = irc;
			_parent = parent;
		}

		/* (non-Javadoc)
		 * @see f00f.net.irc.martyr.GenericCommandAutoService#updateCommand(f00f.net.irc.martyr.InCommand)
		 */
		protected void updateCommand(InCommand command) {
			if (!(command instanceof MessageCommand))
				return;
			MessageCommand msgc = (MessageCommand) command;
			String msg = msgc.getMessage();
			if (msg.startsWith("!passed ")) {
				String username = msg.substring("!passed ".length());
				try {
					User user =
						_parent
							.getConnectionManager()
							.getUserManager()
							.getUserByName(
							username);
					for (Iterator iter = _parent.getQuotas().iterator();
						iter.hasNext();
						) {
						Quota quota = (Quota) iter.next();
						if (quota.perm.check(user)) {
							long bytesleft =
								quota.quota
									- MonthlyQuota.getUploadedBytesForPeriod(
										user,
										quota.period);
							_irc.say(
								"[quota] "+user.getUsername()+" is on "
									+ quota.name
									+ " with "
									+ Bytes.formatBytes(bytesleft)+" left");
						}
					}
					//_irc.say()
				} catch (NoSuchUserException e) {
					_irc.say("No such user: " + username);
					logger.warn("", e);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}

	}
}
