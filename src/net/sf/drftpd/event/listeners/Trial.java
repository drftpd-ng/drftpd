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
import net.sf.drftpd.master.usermanager.StaticUser;
import net.sf.drftpd.master.usermanager.User;
import net.sf.drftpd.util.CalendarUtils;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Logger;

/**
 * @author mog
 * @version $Id: Trial.java,v 1.10 2004/01/05 00:14:19 mog Exp $
 */
public class Trial implements FtpListener {
	private static final short ACTION_DISABLE = 0;
	private static final short ACTION_PURGE = 1;
	private static final Logger logger = Logger.getLogger(Trial.class);

	public static final int PERIOD_DAILY = Calendar.DAY_OF_MONTH; // = 5
	public static final short PERIOD_MONTHLY = Calendar.MONTH; // = 2
	public static final short PERIOD_WEEKLY = Calendar.WEEK_OF_YEAR; // = 3
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

	public static Calendar getCalendarForEndOfBonus(User user, int period) {
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
		int period) {
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
	public static Calendar getCalendarForEndOfPeriod(int period) {
		Calendar cal = Calendar.getInstance();
		moveCalendarToEndOfPeriod(cal, period);
		return cal;
	}
	public static String getPeriodName(int s) {
		switch (s) {
			case PERIOD_DAILY :
				return "day";
			case PERIOD_MONTHLY :
				return "month";
			case PERIOD_WEEKLY :
				return "week";
			default :
				throw new IllegalArgumentException("" + s);
		}
	}

	public static long getUploadedBytesForPeriod(User user, int period) {
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

	public static boolean isInFirstPeriod(User user, int period) {
		return isInFirstPeriod(user, period, System.currentTimeMillis());
	}

	public static boolean isInFirstPeriod(User user, int period, long time) {
		return time <= getCalendarForEndOfBonus(user, period).getTimeInMillis();
	}

	public static void main(String args[])
		throws FileNotFoundException, IOException {
		BasicConfigurator.configure();
		Trial trial = new Trial();
		trial.init(null);
		StaticUser user = new StaticUser("test");
		user.setCredits(0L);

		long reset;
		{
			Calendar cal = Calendar.getInstance();
			reset = System.currentTimeMillis() - 1000 * 60 * 60 * 24 * 8;
			cal.setTimeInMillis(reset);
			CalendarUtils.floorAllLessThanDay(cal);
			reset = cal.getTimeInMillis();
		}

		Calendar calToday = Calendar.getInstance();
		calToday.add(Calendar.DAY_OF_MONTH, -1);
		CalendarUtils.ceilAllLessThanDay(calToday);
		user.setCreated(reset);

		user.setLastReset(calToday.getTimeInMillis());
		logger.debug("lastReset\t" + new Date(reset));
		logger.debug("now\t" + new Date());
		logger.debug("reset timestamp\t" + calToday.getTime());
		{
			user.setUploadedBytes(Bytes.parseBytes("100m"));
			user.setUploadedBytesDay(Bytes.parseBytes("100m"));
			user.setUploadedBytesWeek(Bytes.parseBytes("100m"));
			user.setUploadedBytesMonth(Bytes.parseBytes("100m"));
		}

		trial.actionPerformed(
			new UserEvent(user, "RESETDAY", calToday.getTimeInMillis()));
	}

	public static Calendar moveCalendarToEndOfPeriod(
		Calendar cal,
		int period) {
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
	private TrialSiteBot _siteBot;

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
			return;
		}

		//logger.debug("event.getTime(): " + new Date(event.getTime()));
		//logger.debug(
		//	"uevent.getUser().getLastReset(): "
		//		+ new Date(uevent.getUser().getLastReset()));

		if ("RESETDAY".equals(cmd)) {

			Calendar cal;

			//MONTH UNIQUE //
			cal =
				getCalendarForEndOfFirstPeriod(
					uevent.getUser(),
					PERIOD_MONTHLY);
			//logger.debug("end of first, montly, period: " + cal.getTime());
			//last reset before unique period and event time equals or bigger than unique period
			if (uevent.getUser().getLastReset() <= cal.getTimeInMillis()
				&& uevent.getTime() >= cal.getTimeInMillis()) {
				checkPassed(
					uevent.getUser(),
					uevent.getUser().getUploadedBytes(),
					PERIOD_MONTHLY);
			}

			// WEEK UNIQUE //
			// if less than month unique period
			//TODO should be <= ?
			if (uevent.getTime() < cal.getTimeInMillis()) {
				cal =
					getCalendarForEndOfFirstPeriod(
						uevent.getUser(),
						PERIOD_WEEKLY);
				//logger.debug("end of first, weekly, period: " + cal.getTime());
				//last reset before unique period and event time equals or bigger than unique period
				if (uevent.getUser().getLastReset() <= cal.getTimeInMillis()
					&& uevent.getTime() >= cal.getTimeInMillis()) {
					checkPassed(
						uevent.getUser(),
						uevent.getUser().getUploadedBytes(),
						PERIOD_WEEKLY);
				}
			}

			// DAY UNIQUE //
			//if event lesss than week unique period (cal)
			if (uevent.getTime() <= cal.getTimeInMillis()) {
				cal =
					getCalendarForEndOfFirstPeriod(
						uevent.getUser(),
						PERIOD_DAILY);
				//logger.debug("end of first day period: " + cal.getTime());
				//is day unique period
				if (uevent.getUser().getLastReset() <= cal.getTimeInMillis()
					&& uevent.getTime() >= cal.getTimeInMillis()) {
					checkPassed(
						uevent.getUser(),
						uevent.getUser().getUploadedBytes(),
						PERIOD_DAILY);
					//after day unique period
				} else if (uevent.getTime() > cal.getTimeInMillis()) {
					checkPassed(
						uevent.getUser(),
						uevent.getUser().getUploadedBytesDay(),
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

	private void checkPassed(User user, long bytes, int period) {
		for (Iterator iter = _limits.iterator(); iter.hasNext();) {
			Limit limit = (Limit) iter.next();
			if (limit.getPeriod() == period) {
				long bytesleft = limit.getBytes() - bytes;
				if (bytesleft > 0) {
					logger.info(
						user.getUsername()
							+ " failed "
							+ limit.getName()
							+ " by "
							+ Bytes.formatBytes(bytesleft));
					limit.doFailed(user);
				} else {
					logger.info(
						user.getUsername()
							+ " passed "
							+ limit.getName()
							+ " with "
							+ Bytes.formatBytes(-bytesleft)
							+ " extra");
					limit.doPassed(user);
				}
			}
		}
	}

	ConnectionManager getConnectionManager() {
		return _cm;
	}

	public ArrayList getLimits() {
		return _limits;
	}
	public void init(ConnectionManager mgr) {
		_cm = mgr;
		reload();
	}

	private void reload() {
		Properties props = new Properties();
		try {
			props.load(new FileInputStream("trial.conf"));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		reload(props);
	}
	private void reload(Properties props) {
		ArrayList limits = new ArrayList();
		for (int i = 1;; i++) {
			if (props.getProperty(i + ".quota") == null)
				break;
			Limit limit = new Limit();
			limit.setActionPassed(
				props.getProperty(i + ".actionpassed", "").toLowerCase());
			limit.setActionFailed(
				props.getProperty(i + ".actionfail", "").toLowerCase());
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
				perm = "*";
			limit.setPerm(
				new Permission(FtpConfig.makeUsers(new StringTokenizer(perm))));
			limit.setBytes(Bytes.parseBytes(props.getProperty(i + ".quota")));
			limits.add(limit);
			logger.debug("Limit: " + limit);
		}
		reload(limits);
	}

	public void reload(ArrayList limits) {
		_limits = limits;

		if (_siteBot != null) {
			_siteBot.disable();
		}
		if (_cm != null) {
			try {
				IRCListener _irc =
					(IRCListener) _cm.getFtpListener(IRCListener.class);
				_siteBot = new TrialSiteBot(this, _irc, this);
			} catch (ObjectNotFoundException e1) {
				logger.warn("Error loading sitebot component", e1);
			}
		}
	}
}
