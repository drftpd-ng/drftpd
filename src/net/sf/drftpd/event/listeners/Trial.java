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
import net.sf.drftpd.FatalException;
import net.sf.drftpd.ObjectNotFoundException;
import net.sf.drftpd.event.Event;
import net.sf.drftpd.event.FtpListener;
import net.sf.drftpd.event.UserEvent;
import net.sf.drftpd.event.irc.IRCListener;
import net.sf.drftpd.master.ConnectionManager;
import net.sf.drftpd.master.config.FtpConfig;
import net.sf.drftpd.master.config.Permission;
import net.sf.drftpd.master.usermanager.NoSuchUserException;
import net.sf.drftpd.master.usermanager.StaticUser;
import net.sf.drftpd.master.usermanager.User;
import net.sf.drftpd.master.usermanager.UserFileException;
import net.sf.drftpd.util.CalendarUtils;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Logger;

import f00f.net.irc.martyr.GenericCommandAutoService;
import f00f.net.irc.martyr.InCommand;
import f00f.net.irc.martyr.commands.MessageCommand;

/**
 * @author mog
 * @version $Id: Trial.java,v 1.6 2003/12/07 22:31:44 mog Exp $
 */
public class Trial implements FtpListener {
	class Limit {
		private String _actionFailed;
		private String _actionPassed;
		private long _bytes;
		private String _name;
		private short _period;
		private Permission _perm;

		public void doFailed(User user) {
			doAction(getActionFailed(), user);
		}

		public void doPassed(User user) {
			doAction(getActionPassed(), user);
		}

		public String getActionFailed() {
			return _actionFailed;
		}

		public String getActionPassed() {
			return _actionPassed;
		}

		public long getBytes() {
			return _bytes;
		}

		public short getPeriod() {
			return _period;
		}

		public Permission getPerm() {
			return _perm;
		}

		public void setActionFailed(String action) {
			validatePassed(action);
			_actionFailed = action;
		}

		public void setActionPassed(String action) {
			validatePassed(action);
			_actionPassed = action;
		}

		public void setBytes(long bytes) {
			_bytes = bytes;
		}

		public void setName(String name) {
			_name = name;
		}

		public void setPeriod(short period) {
			_period = period;
		}

		public void setPerm(Permission perm) {
			_perm = perm;
		}

		public String toString() {
			return "Limit[name="
				+ _name
				+ ",bytes="
				+ Bytes.formatBytes(_bytes)
				+ ",period="
				+ getPeriodName(_period)
				+ "]";
		}

		private void validatePassed(String action) {
			if (action == null)
				return;
			StringTokenizer st = new StringTokenizer(action);
			if (!st.hasMoreTokens())
				return;
			String cmd = st.nextToken().toLowerCase();
			if (!("deluser".equals(action)
				|| "purge".equals(action)
				|| "chgrp".equals(cmd))) {
				throw new IllegalArgumentException();
			}
		}
	}
	class SiteBot extends GenericCommandAutoService {

		private IRCListener _irc;

		private Trial _parent;

		protected SiteBot(IRCListener irc, Trial parent) {
			super(irc.getIRCConnection());
			_irc = irc;
			_parent = parent;
		}

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
							if (limit.getPerm().check(user)) {
								long bytesleft =
									limit.getBytes()
										- Trial.getUploadedBytesForPeriod(
											user,
											limit._period);
								if (bytesleft <= 0) {
									_irc.say(
										"[passed] "
											+ user.getUsername()
											+ " has passed this "
											+ getPeriodName(limit._period)
											+ " "
											+ limit._name
											+ " with "
											+ Bytes.formatBytes(-bytesleft));
								} else {
									_irc.say(
										"[passed] "
											+ user.getUsername()
											+ " is on "
											+ limit._name
											+ " with "
											+ Bytes.formatBytes(bytesleft)
											+ " left until "
											+ Trial
												.getCalendarForEndOfPeriod(
													limit._period)
												.getTime());
								}
								if (isInFirstPeriod(user, limit.getPeriod())) {
									_irc.say(
										user.getUsername()
											+ " is still in unique period/bonus period");
								}
							}
						}
						//_irc.say()
					} catch (NoSuchUserException e) {
						_irc.say("No such user: " + username);
						logger.info("", e);
					} catch (UserFileException e) {
						logger.warn("", e);
					}
				}
			} catch (RuntimeException e) {
				logger.error("", e);
			}

		}
	}
	private static final short ACTION_DISABLE = 0;
	private static final short ACTION_PURGE = 1;
	private static final Logger logger = Logger.getLogger(Trial.class);

	private static final short PERIOD_DAILY = 0;
	private static final short PERIOD_MONTHLY = 2;
	private static final short PERIOD_WEEKLY = 1;
	public static void doAction(String action, User user) {
		if (action == null)
			return;
		StringTokenizer st = new StringTokenizer(action);
		String cmd = st.nextToken().toLowerCase();
		if ("chgrp".equals(cmd)) {
			while (st.hasMoreTokens()) {
				user.toggleGroup(st.nextToken());
			}
		} else if ("deluser".equals(cmd)) {
			user.setDeleted(true);
		} else if ("purge".equals(cmd)) {
			user.setDeleted(true);
			user.purge();
		}
	}

	public static Calendar getCalendarForEndOfBonus(User user, short period) {
		Calendar cal = Calendar.getInstance();
		cal.setTimeInMillis(user.getCreated());
		moveCalendarToEndOfPeriod(cal, period);
		return cal;
	}

	/**
	 * Returns last day of the first, unique, period.
	 */
	public static Calendar getCalendarForEndOfFirstPeriod(
		User user,
		short period) {
		Calendar cal = Calendar.getInstance();
		cal.setTime(new Date(user.getCreated()));
		CalendarUtils.ceilAllLessThanDay(cal);

		switch (period) {
			case PERIOD_DAILY :
				//user added on monday @ 15:00, trial ends on tuesday with reset at 23:59
				//bonus ends at tuesday 23:59
				CalendarUtils.incrementDay(cal);
				return cal;
			case PERIOD_WEEKLY :
				//user added on week 1, wednsday @ 15:00, trial
				//trial ends with a reset at week 2, wednsday 24:00
				//bonus ends on week 3, monday 00:00
				CalendarUtils.incrementWeek(cal);
				return cal;
			case PERIOD_MONTHLY :
				//user added on january 31 15:00
				//trial ends on feb 28 24:00
				//bonus ends on mar 1 00:00
				CalendarUtils.incrementMonth(cal);
				return cal;
			default :
				throw new IllegalArgumentException(
					"Don't know how to handle " + period);
		}
	}
	public static Calendar getCalendarForEndOfPeriod(short period) {
		Calendar cal = Calendar.getInstance();
		moveCalendarToEndOfPeriod(cal, period);
		return cal;
	}
	public static String getPeriodName(short s) {
		switch (s) {
			case PERIOD_DAILY :
				return "day";
			case PERIOD_MONTHLY :
				return "month";
			case PERIOD_WEEKLY :
				return "week";
			default :
				throw new IllegalArgumentException(Short.toString(s));
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

	public static boolean isInFirstPeriod(User user, short period) {
		return isInFirstPeriod(user, period, System.currentTimeMillis());
	}

	public static boolean isInFirstPeriod(User user, short period, long time) {
		return time <= getCalendarForEndOfBonus(user, period).getTimeInMillis();
	}

	public static void main(String args[])
		throws FileNotFoundException, IOException {
		BasicConfigurator.configure();
		Trial trial = new Trial();
		trial.init(null);
		StaticUser user = new StaticUser("test");
		user.setCredits(0L);

		Calendar cal = Calendar.getInstance();
		long reset = System.currentTimeMillis() - 1000 * 60 * 60 * 24 * 8;
		cal.setTimeInMillis(reset);
		CalendarUtils.floorAllLessThanDay(cal);
		reset = cal.getTimeInMillis();

		Calendar calToday = Calendar.getInstance();
		CalendarUtils.floorAllLessThanDay(calToday);

		user.setCreated(reset);
		user.setLastReset(calToday.getTimeInMillis());
		logger.debug("lastReset\t" + new Date(reset));
		logger.debug("now\t" + new Date());

		user.setUploadedBytes(Bytes.parseBytes("100m"));
		user.setUploadedBytesDay(Bytes.parseBytes("100m"));
		user.setUploadedBytesWeek(Bytes.parseBytes("100m"));
		user.setUploadedBytesMonth(Bytes.parseBytes("100m"));
		trial.actionPerformed(
			new UserEvent(user, "RESETDAY", calToday.getTimeInMillis()));
	}

	public static Calendar moveCalendarToEndOfPeriod(
		Calendar cal,
		short period) {
		CalendarUtils.ceilAllLessThanDay(cal);
		switch (period) {
			case PERIOD_DAILY :
				//CalendarUtils.incrementDay(cal);
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

	private ConnectionManager _cm;

	private ArrayList _limits;
	private SiteBot _siteBot;

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
			Calendar cal =
				getCalendarForEndOfFirstPeriod(
					uevent.getUser(),
					PERIOD_MONTHLY);
			logger.debug("end of first, montly, period: " + cal.getTime());
			if (uevent.getUser().getLastReset() <= cal.getTimeInMillis()
				&& uevent.getTime() >= cal.getTimeInMillis()) {
				checkPassed(
					uevent.getUser(),
					uevent.getUser().getUploadedBytes(),
					PERIOD_MONTHLY);
			}

			// > 'bigger than' after
			// < 'smaller than' before
			// if event before month unique perioid
			if (uevent.getTime() < cal.getTimeInMillis()) {
				cal =
					getCalendarForEndOfFirstPeriod(
						uevent.getUser(),
						PERIOD_WEEKLY);
				logger.debug("end of first, weekly, period: " + cal.getTime());
				if (uevent.getUser().getLastReset() <= cal.getTimeInMillis()
					&& uevent.getTime() >= cal.getTimeInMillis()) {
					checkPassed(
						uevent.getUser(),
						uevent.getUser().getUploadedBytes(),
						PERIOD_WEEKLY);
				}
			}

			// if event before month/week unique period and 

			//daily reset if event is after month/week unique period or after day unique period

			//unique period if before month&week unique period 
			if (uevent.getTime() < cal.getTimeInMillis()) {
				cal =
					getCalendarForEndOfFirstPeriod(
						uevent.getUser(),
						PERIOD_DAILY);

				if (cal.getTimeInMillis() == cal.getTimeInMillis()) {
					checkPassed(
						uevent.getUser(),
						uevent.getUser().getUploadedBytes(),
						PERIOD_DAILY);
				}
			} else {
				//always check if after
				checkPassed(
					uevent.getUser(),
					uevent.getUser().getUploadedBytesDay(),
					PERIOD_DAILY);
			}
		}
		if ("RESETWEEK".equals(cmd)) {
			if (!isInFirstPeriod(uevent.getUser(),
				PERIOD_WEEKLY,
				uevent.getTime()))
				checkPassed(
					uevent.getUser(),
					uevent.getUser().getUploadedBytesWeek(),
					PERIOD_WEEKLY);
		}
		if ("RESETMONTH".equals(cmd)) {
			if (!isInFirstPeriod(uevent.getUser(),
				PERIOD_MONTHLY,
				uevent.getTime()))
				checkPassed(
					uevent.getUser(),
					uevent.getUser().getUploadedBytesMonth(),
					PERIOD_MONTHLY);
		}
	}

	private void checkPassed(User user, long bytes, short period) {
		for (Iterator iter = _limits.iterator(); iter.hasNext();) {
			Limit limit = (Limit) iter.next();
			if (limit._period == period) {
				long bytesleft = limit._bytes - bytes;
				if (bytesleft > 0) {
					logger.info(
						user.getUsername()
							+ " failed "
							+ limit._name
							+ " by "
							+ Bytes.formatBytes(bytesleft));
					limit.doFailed(user);
				} else {
					logger.info(
						user.getUsername()
							+ " passed "
							+ limit._name
							+ " with "
							+ Bytes.formatBytes(-bytesleft)
							+ " extra");
					limit.doPassed(user);
				}
			}
		}
	}

	private ConnectionManager getConnectionManager() {
		return _cm;
	}

	public ArrayList getLimits() {
		return _limits;
	}
	public void init(ConnectionManager mgr) {
		_cm = mgr;
		reload();
		//TODO schedule reset of users statistics at end of each day
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
			if (props.getProperty(i + ".quota") == null)
				break;
			Limit limit = new Limit();
			limit.setActionPassed(props.getProperty(i + ".actionpassed"));
			limit.setActionFailed(props.getProperty(i + ".actionfail"));
			limit.setName(props.getProperty(i + ".name"));
			String period = props.getProperty(i + ".period").toLowerCase();
			if ("monthly".equals(period)) {
				limit.setPeriod(PERIOD_MONTHLY);
			} else if ("weekly".equals(period)) {
				limit.setPeriod(PERIOD_WEEKLY);
			} else if ("daily".equals(period)) {
				limit.setPeriod(PERIOD_DAILY);
			} else {
				throw new RuntimeException(
					new IOException(period + " is not a recognized period"));
			}
			String perm = props.getProperty(i + ".perm");
			if (perm == null)
				throw new FatalException(
					i
						+ ".perm must be set, set to * if all users should be matched");
			limit.setPerm(
				new Permission(FtpConfig.makeUsers(new StringTokenizer(perm))));
			limit.setBytes(Bytes.parseBytes(props.getProperty(i + ".quota")));
			limits.add(limit);
			logger.debug("Limit: " + limit);
		}
		_limits = limits;

		if (_siteBot != null) {
			_siteBot.disable();
		}
		if (_cm != null) {
			try {
				IRCListener _irc =
					(IRCListener) _cm.getFtpListener(IRCListener.class);
				_siteBot = new SiteBot(_irc, this);
			} catch (ObjectNotFoundException e1) {
				logger.warn("Error loading sitebot component", e1);
			}
		}
	}
}
