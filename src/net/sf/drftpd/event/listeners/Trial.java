package net.sf.drftpd.event.listeners;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
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
import net.sf.drftpd.util.CalendarUtils;

import org.apache.log4j.Logger;

import f00f.net.irc.martyr.GenericCommandAutoService;
import f00f.net.irc.martyr.InCommand;
import f00f.net.irc.martyr.commands.MessageCommand;

public class Trial implements FtpListener {
	class Limit {
		String action;
		String name;
		short period;
		Permission perm;
		long bytes;
	}

	class SiteBot extends GenericCommandAutoService {

		//private static final Logger logger = Logger.getLogger(SiteBot.class);

		private IRCListener _irc;

		private Trial _parent;

		/**
		 * @param connection
		 */
		protected SiteBot(IRCListener irc, Trial parent) {
			super(irc.getIRCConnection());
			_irc = irc;
			_parent = parent;
		}

		/* (non-Javadoc)
		 * @see f00f.net.irc.martyr.GenericCommandAutoService#updateCommand(f00f.net.irc.martyr.InCommand)
		 */
		protected void updateCommand(InCommand command) {
			try {
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
						for (Iterator iter = _parent.getLimits().iterator();
							iter.hasNext();
							) {
							Limit limit = (Limit) iter.next();
							if (limit.perm.check(user)) {
								long bytesleft =
									limit.bytes
										- Trial.getUploadedBytesForPeriod(
											user,
											limit.period);
								if (bytesleft <= 0) {
									_irc.say(
										"[passed] "
											+ user.getUsername()
											+ " has passed this "
											+ getPeriodName(limit.period)
											+ " "
											+ limit.name
											+ " with "
											+ Bytes.formatBytes(-bytesleft));
								} else {
									_irc.say(
										"[passed] "
											+ user.getUsername()
											+ " is on "
											+ limit.name
											+ " with "
											+ Bytes.formatBytes(bytesleft)
											+ " left until "
											+ Trial
												.getCalendarForEndOfPeriod(
													limit.period)
												.getTime());
								}
							}
						}
						//_irc.say()
					} catch (NoSuchUserException e) {
						_irc.say("No such user: " + username);
						logger.info("", e);
					} catch (IOException e) {
						logger.warn("", e);
					}
				}
			} catch (RuntimeException e) {
				logger.error("", e);
			}

		}

		/**
		 * @param s
		 * @return
		 */
		private String getPeriodName(short s) {
			switch (s) {
				case PERIOD_DAILY :
					return "days";
				case PERIOD_MONTHLY :
					return "months";
				case PERIOD_WEEKLY :
					return "weeks";
				default :
					throw new IllegalArgumentException("" + s);
			}
		}
	}
	private static final short ACTION_DISABLE = 0;
	private static final short ACTION_PURGE = 1;
	private static final Logger logger = Logger.getLogger(Trial.class);

	private static final short PERIOD_DAILY = 0;
	private static final short PERIOD_MONTHLY = 2;
	private static final short PERIOD_WEEKLY = 1;

	//TODO use "ALLUP" for first period
	public static boolean isInFirstPeriod(User user, short period) {
		Calendar cal = Calendar.getInstance();
		cal.setTime(new Date(user.getCreated()));
		CalendarUtils.floorAllLessThanDay(cal);

		switch (period) {
			case PERIOD_DAILY :
				//user added on monday @ 15:00, trial ends on tuesday with reset at 24:00
				//bonus ends at wednsday 00:00
				CalendarUtils.incrementDay(cal);
				break;
			case PERIOD_WEEKLY :
				//user added on week 1, wednsday @ 15:00, trial
				//trial ends with a reset at week 2, wednsday 24:00
				//bonus ends on week 3, monday 00:00
				CalendarUtils.incrementWeek(cal);
				break;
			case PERIOD_MONTHLY :
				//user added on january 31 15:00
				//trial ends on feb 28 24:00
				//bonus ends on mar 1 00:00
				CalendarUtils.incrementMonth(cal);
				break;
			default :
				throw new IllegalArgumentException("Can't handle " + period);
		}
		//return (now is before end of bonus)
		return System.currentTimeMillis() >= cal.getTimeInMillis();
	}

	public static Calendar getCalendarForEndOfPeriod(short period) {
		Calendar cal = Calendar.getInstance();
		CalendarUtils.floorAllLessThanDay(cal);
		switch (period) {
			case PERIOD_DAILY :
				CalendarUtils.incrementDay(cal);
				return cal;
			case PERIOD_WEEKLY :
				CalendarUtils.incrementWeek(cal);
				return cal;
			case PERIOD_MONTHLY :
				CalendarUtils.incrementMonth(cal);
				return cal;
			default :
				throw new IllegalArgumentException("" + period);
		}
	}

	public static long getUploadedBytesForPeriod(User user, short period) {
		if (isInFirstPeriod(user, period))
			return user.getDownloadedBytes();
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
	private ConnectionManager _cm;

	ArrayList _limits;
	private SiteBot _siteBot;

	/**
	 * 
	 */
	public Trial() throws FileNotFoundException, IOException {
		super();
	}

	public void actionPerformed(Event event) {
		if (!(event instanceof UserEvent))
			return;
		UserEvent uevent = (UserEvent) event;
		String cmd = event.getCommand();
		if ("RELOAD".equals(cmd)) {
			reload();
		}
		if ("RESETDAY".equals(cmd)) {
			checkPassed(uevent.getUser(), uevent.getUser().getUploadedBytesDay(), PERIOD_DAILY);
			//call checkPassed for unique period if this resetday event also reset's the unique period
			Calendar cal;
			cal = getCalendarForEndOfPeriod(PERIOD_WEEKLY);
			Date eventdate = new Date(uevent.getTime());
			//			logger.debug("Checking whether to reset unique weekly period for "+uevent.getUser().getUsername());
			//			logger.debug("created = "+new Date(uevent.getUser().getCreated()));
			//			logger.debug("eventdate = "+eventdate);
			//			logger.debug("lastreset = "+new Date(uevent.getUser().getLastReset()));
			//			logger.debug("calendarforendofperiod = "+cal.getTime());

			if (eventdate.equals(cal.getTime())
				&& new Date(uevent.getUser().getLastReset()).before(eventdate)) {
				checkPassed(uevent.getUser(), uevent.getUser().getUploadedBytes(), PERIOD_WEEKLY);
			}

			cal = getCalendarForEndOfPeriod(PERIOD_MONTHLY);
			//			logger.debug("Checking whether to reset unique monthly period for "+uevent.getUser().getUsername());
			//			logger.debug("created = "+new Date(uevent.getUser().getCreated()));
			//			logger.debug("eventdate = "+eventdate);
			//			logger.debug("lastreset = "+new Date(uevent.getUser().getLastReset()));
			//			logger.debug("calendarforendofperiod = "+cal.getTime());

			//cal = getCalendarForEndOfPeriod(PERIOD_MONTHLY);
		}
		if ("RESETWEEK".equals(cmd)) {
			checkPassed(uevent.getUser(), uevent.getUser().getUploadedBytesWeek(), PERIOD_WEEKLY);
		}
		if ("RESETMONTH".equals(cmd)) {
			checkPassed(uevent.getUser(), uevent.getUser().getUploadedBytesMonth(), PERIOD_MONTHLY);
		}
	}

	private void checkPassed(User user, long bytes, short period) {
		for (Iterator iter = _limits.iterator(); iter.hasNext();) {
			Limit limit = (Limit) iter.next();
			if (limit.period == period) {
				//is this the reset for the unique period
				isResetForUniquePeriod(user, period);
				isInFirstPeriod(user, period);
				long bytesleft = limit.bytes - bytes;
				if (bytesleft > 0) {
					logger.info(
						user.getUsername()
							+ " failed "
							+ limit.name
							+ " by "
							+ Bytes.formatBytes(bytesleft));
					//TODO take action: disable, delete, change group
				} else {
					logger.info(
						user.getUsername()
							+ " passed "
							+ limit.name
							+ " with "
							+ Bytes.formatBytes(-bytesleft)
							+ " extra");
					//TODO take action: change group
				}
			}
		}
	}

	/**
	 * @param user
	 * @param period
	 */
	private boolean isResetForUniquePeriod(User user, short period) {
		return false;
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

	/**
	 * 
	 */
	public ArrayList getLimits() {
		return _limits;
	}
	public void init(ConnectionManager mgr) {
		_cm = mgr;
		reload();
		//schedule to reset users statistics at end of each day
	}

	private void reload() {
		Properties props = new Properties();
		try {
			props.load(new FileInputStream("passed.conf"));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		ArrayList limits = new ArrayList();
		for (int i = 1;; i++) {
			if (props.getProperty(i + ".limit") == null)
				break;

			Limit limit = new Limit();
			limit.action = props.getProperty(i + ".action");
			limit.name = props.getProperty(i + ".name");
			String period = props.getProperty(i + ".period").toLowerCase();
			if ("monthly".equals(period)) {
				limit.period = PERIOD_MONTHLY;
			} else if ("weekly".equals(period)) {
				limit.period = PERIOD_WEEKLY;
			} else if ("daily".equals(period)) {
				limit.period = PERIOD_DAILY;
			} else {
				throw new RuntimeException(
					new IOException(period + " is not a recognized period"));
			}
			String perm = props.getProperty(i + ".perm");
			assert perm != null : i;
			limit.perm =
				new Permission(FtpConfig.makeUsers(new StringTokenizer(perm)));
			limit.bytes = Bytes.parseBytes(props.getProperty(i + ".limit"));
			limits.add(limit);
		}
		_limits = limits;

		if (_siteBot != null) {
			_siteBot.disable();
		}
		try {
			IRCListener _irc =
				(IRCListener) _cm.getFtpListener(IRCListener.class);
			_siteBot = new SiteBot(_irc, this);
		} catch (ObjectNotFoundException e1) {
			logger.warn("Error loading sitebot component", e1);
		}
	}
}
