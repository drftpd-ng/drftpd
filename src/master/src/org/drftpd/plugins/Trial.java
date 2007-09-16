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
package org.drftpd.plugins;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.Properties;
import java.util.StringTokenizer;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.drftpd.Bytes;
import org.drftpd.PropertyHelper;
import org.drftpd.commands.UserManagement;
import org.drftpd.dynamicdata.KeyNotFoundException;
import org.drftpd.event.Event;
import org.drftpd.event.FtpListener;
import org.drftpd.event.UserEvent;
import org.drftpd.permissions.Permission;
import org.drftpd.usermanager.User;
import org.drftpd.util.CalendarUtils;

/**
 * @author mog
 * @version $Id$
 */
public class Trial extends FtpListener {
	private static final Logger logger = Logger.getLogger(Trial.class);

	public static final int PERIOD_ALL = 0;
	public static final int PERIOD_MONTHLY = 1;
	public static final int PERIOD_WEEKLY = 2;
	public static final int PERIOD_DAILY = 3;

	private ArrayList<Limit> _limits;

	public Trial() throws FileNotFoundException, IOException {
		super();
	}

	public static void doAction(String action, User user) {
		try {
			if (action == null) {
				return;
			}

			StringTokenizer st = new StringTokenizer(action);

			if (!st.hasMoreTokens()) {
				logger.info(user.getName() + " no action specified");

				return;
			}

			String cmd = st.nextToken().toLowerCase();

			if ("chgrp".equals(cmd)) {
				while (st.hasMoreTokens()) {
					user.toggleGroup(st.nextToken());
				}
			} else if ("setgrp".equals(cmd)) {
				user.setGroup(st.nextToken());
				logger.info(user.getName() + " primary group set to "
						+ user.getGroup());
			} else if ("delete".equals(cmd)) {
				user.setDeleted(true);
				logger.info(user.getName() + " deleted");
			} else if ("purge".equals(cmd)) {
				user.setDeleted(true);
				user.purge();
				logger.info(user.getName() + " purged");
			}
		} catch (java.util.NoSuchElementException e) {
			logger.info("Error parsing \"" + action + "\"", e);
		}
	}

	public static Calendar getCalendarForEndOfBonus(User user, int period) {
		Calendar cal = Calendar.getInstance();
		try {
			cal.setTime((Date) user.getKeyedMap().getObject(
					UserManagement.CREATED));
		} catch (KeyNotFoundException e) {
			throw new IllegalArgumentException("User has no created");
		}
		moveCalendarToEndOfPeriod(cal, period);

		return cal;
	}

	/**
	 * Returns last day of the first, unique, period.
	 */
	public static Calendar getCalendarForEndOfFirstPeriod(User user, int period) {
		Calendar cal = Calendar.getInstance();
		try {
			cal.setTime((Date) user.getKeyedMap().getObject(
					UserManagement.CREATED));
		} catch (KeyNotFoundException e) {
			throw new IllegalArgumentException("User has no created info");
		}
		CalendarUtils.ceilAllLessThanDay(cal);

		switch (period) {
		case PERIOD_DAILY:

			// user added on monday @ 15:00, trial ends on tuesday with reset at
			// 23:59
			// bonus ends at tuesday 23:59
			CalendarUtils.incrementDay(cal);

			return cal;

		case PERIOD_WEEKLY:

			// user added on week 1, wednsday @ 15:00, trial
			// trial ends with a reset at week 2, wednsday 24:00
			// bonus ends on week 3, monday 00:00
			CalendarUtils.incrementWeek(cal);

			return cal;

		case PERIOD_MONTHLY:

			// user added on january 31 15:00
			// trial ends on feb 28 24:00
			// bonus ends on mar 1 00:00
			CalendarUtils.incrementMonth(cal);

			return cal;

		default:
			throw new IllegalArgumentException("Don't know how to handle "
					+ period);
		}
	}

	public static Calendar getCalendarForEndOfPeriod(int period) {
		Calendar cal = Calendar.getInstance();
		CalendarUtils.ceilAllLessThanDay(cal);

		switch (period) {
		case PERIOD_DAILY:
			break;

		case PERIOD_WEEKLY:

			int dow = CalendarUtils.getLastDayOfWeek(cal);

			// dow is less than current day of week, increment week
			if (dow < cal.get(Calendar.DAY_OF_WEEK)) {
				cal.add(Calendar.WEEK_OF_YEAR, 1);
			}

			cal.set(Calendar.DAY_OF_WEEK, dow);

			return cal;

		case PERIOD_MONTHLY:
			cal.set(Calendar.DAY_OF_MONTH, cal
					.getActualMaximum(Calendar.DAY_OF_MONTH));

			return cal;

		default:
			throw new IllegalArgumentException("" + period);
		}

		// moveCalendarToEndOfPeriod(cal, period);
		return cal;
	}

	public static String getPeriodName(int s) {
		switch (s) {
		case PERIOD_DAILY:
			return "day";

		case PERIOD_MONTHLY:
			return "month";

		case PERIOD_WEEKLY:
			return "week";

		default:
			throw new IllegalArgumentException("" + s);
		}
	}

	public static String getPeriodName2(int s) {
		switch (s) {
		case PERIOD_DAILY:
			return "daily";

		case PERIOD_MONTHLY:
			return "monthly";

		case PERIOD_WEEKLY:
			return "weekly";

		default:
			throw new IllegalArgumentException("" + s);
		}
	}

	public static long getUploadedBytesForPeriod(User user, int period) {
		if (isInFirstPeriod(user, period)) {
			return user.getDownloadedBytes();
		}

		switch (period) {
		case PERIOD_DAILY:
			return user.getUploadedBytesDay();

		case PERIOD_WEEKLY:
			return user.getUploadedBytesWeek();

		case PERIOD_MONTHLY:
			return user.getUploadedBytesMonth();

		default:
			throw new IllegalArgumentException();
		}
	}

	public static boolean isInFirstPeriod(User user, int period) {
		return isInFirstPeriod(user, period, System.currentTimeMillis());
	}

	public static boolean isInFirstPeriod(User user, int period, long time) {
		return time <= getCalendarForEndOfBonus(user, period).getTimeInMillis();
	}

	public static Calendar moveCalendarToEndOfPeriod(Calendar cal, int period) {
		CalendarUtils.ceilAllLessThanDay(cal);

		switch (period) {
		case PERIOD_DAILY:

			// CalendarUtils.incrementDay(cal);
			return cal;

		case PERIOD_WEEKLY:
			CalendarUtils.incrementWeek(cal);

			return cal;

		case PERIOD_MONTHLY:
			CalendarUtils.incrementMonth(cal);

			return cal;

		default:
			throw new IllegalArgumentException("" + period);
		}
	}

	public void actionPerformed(Event event) {
		String cmd = event.getCommand();
		if (cmd.equals("RELOAD")) {
			try {
				reload();
			} catch (IOException e) {
				logger.log(Level.WARN, "", e);
			}
		}
		if (!(event instanceof UserEvent)) {
			return;
		}

		UserEvent uevent = (UserEvent) event;

		// logger.debug("event.getTime(): " + new Date(event.getTime()));
		// logger.debug(
		// "uevent.getUser().getLastReset(): "
		// + new Date(uevent.getUser().getLastReset()));
		if ("RESETDAY".equals(cmd)) {
			Calendar cal;

			// MONTH UNIQUE //
			cal = getCalendarForEndOfFirstPeriod(uevent.getUser(),
					PERIOD_MONTHLY);

			// logger.debug("end of first, montly, period: " + cal.getTime());
			// last reset before unique period and event time equals or bigger
			// than unique period
			if ((uevent.getUser().getKeyedMap().getObjectDate(
					UserManagement.LASTSEEN).getTime() <= cal.getTimeInMillis())
					&& (uevent.getTime() >= cal.getTimeInMillis())) {
				checkPassed(uevent.getUser(), uevent.getUser()
						.getUploadedBytes(), PERIOD_MONTHLY);
			}

			// WEEK UNIQUE //
			// if less than month unique period
			if (uevent.getTime() < cal.getTimeInMillis()) {
				cal = getCalendarForEndOfFirstPeriod(uevent.getUser(),
						PERIOD_WEEKLY);

				// logger.debug("end of first, weekly, period: " +
				// cal.getTime());
				// last reset before unique period and event time equals or
				// bigger than unique period
				if ((uevent.getUser().getKeyedMap().getObjectDate(
						UserManagement.LASTSEEN).getTime() <= cal
						.getTimeInMillis())
						&& (uevent.getTime() >= cal.getTimeInMillis())) {
					checkPassed(uevent.getUser(), uevent.getUser()
							.getUploadedBytes(), PERIOD_WEEKLY);
				}
			}

			// DAY UNIQUE //
			// if event lesss than week unique period (cal)
			if (uevent.getTime() < cal.getTimeInMillis()) {
				cal = getCalendarForEndOfFirstPeriod(uevent.getUser(),
						PERIOD_DAILY);

				// logger.debug("end of first day period: " + cal.getTime());
				// is day unique period
				if ((uevent.getUser().getKeyedMap().getObjectDate(
						UserManagement.LASTSEEN).getTime() <= cal
						.getTimeInMillis())
						&& (uevent.getTime() >= cal.getTimeInMillis())) {
					checkPassed(uevent.getUser(), uevent.getUser()
							.getUploadedBytes(), PERIOD_DAILY);

					// after day unique period
				} else if (uevent.getTime() > cal.getTimeInMillis()) {
					checkPassed(uevent.getUser(), uevent.getUser()
							.getUploadedBytesDay(), PERIOD_DAILY);
				}
			} else {
				// always check if after
				checkPassed(uevent.getUser(), uevent.getUser()
						.getUploadedBytesDay(), PERIOD_DAILY);
			}
		}

		if ("RESETWEEK".equals(cmd)) {
			if (!isInFirstPeriod(uevent.getUser(), PERIOD_WEEKLY, uevent
					.getTime())) {
				checkPassed(uevent.getUser(), uevent.getUser()
						.getUploadedBytesWeek(), PERIOD_WEEKLY);
			}
		}

		if ("RESETMONTH".equals(cmd)) {
			if (!isInFirstPeriod(uevent.getUser(), PERIOD_MONTHLY, uevent
					.getTime())) {
				checkPassed(uevent.getUser(), uevent.getUser()
						.getUploadedBytesMonth(), PERIOD_MONTHLY);
			}
		}
	}

	private void checkPassed(User user, long bytes, int period) {
		for (Iterator iter = _limits.iterator(); iter.hasNext();) {
			Limit limit = (Limit) iter.next();

			if ((limit.getPeriod() == period) && limit.getPerm().check(user)) {
				long bytesleft = limit.getBytes() - bytes;

				if (bytesleft > 0) {
					logger.info(user.getName() + " failed " + limit.getName()
							+ " by " + Bytes.formatBytes(bytesleft));
					limit.doFailed(user);
				} else {
					logger.info(user.getName() + " passed " + limit.getName()
							+ " with " + Bytes.formatBytes(-bytesleft)
							+ " extra");
					limit.doPassed(user);
				}
			}
		}
	}

	public ArrayList getLimits() {
		return _limits;
	}

	public void init() {
		try {
			reload();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private void reload() throws IOException {
		Properties props = new Properties();
		FileInputStream fis = null;
		try {
			fis = new FileInputStream("conf/trial.conf");
			props.load(fis);
		} finally {
			if (fis != null) {
				fis.close();
			}
		}
		reload(props);
	}

	public void reload(ArrayList<Limit> limits) {
		_limits = limits;
	}

	protected void reload(Properties props) {
		ArrayList<Limit> limits = new ArrayList<Limit>();

		for (int i = 1;; i++) {
			if (props.getProperty(i + ".quota") == null) {
				break;
			}

			Limit limit = new Limit();
			limit.setName(PropertyHelper.getProperty(props, i + ".name"));
			limit.setActionPassed(props.getProperty(i + ".pass", props
					.getProperty(i + ".passed", "")));
			limit.setActionFailed(props.getProperty(i + ".fail", ""));

			if (limit.getActionFailed().equals("")
					&& limit.getActionPassed().equals("")) {
				throw new IllegalArgumentException(
						"Both .pass and .fail cannot be empty for " + i + " ("
								+ limit.getName() + ")");
			}

			String period = PropertyHelper.getProperty(props, i + ".period")
					.toLowerCase();

			if ("monthly".equals(period)) {
				limit.setPeriod(PERIOD_MONTHLY);
			} else if ("weekly".equals(period)) {
				limit.setPeriod(PERIOD_WEEKLY);
			} else if ("daily".equals(period)) {
				limit.setPeriod(PERIOD_DAILY);
			} else {
				throw new RuntimeException(new IOException(period
						+ " is not a recognized period"));
			}

			String perm = props.getProperty(i + ".perm");

			if (perm == null) {
				perm = "*";
			}

			limit.setPerm(new Permission(Permission.makeUsers(new StringTokenizer(perm))));
			limit.setBytes(Bytes.parseBytes(PropertyHelper.getProperty(props, i
					+ ".quota")));
			limits.add(limit);
			logger.debug("Limit: " + limit);
		}

		reload(limits);
	}

	public void unload() {
	}

	public static class Limit {
		private String _actionFailed;

		private String _actionPassed;

		private long _bytes;

		private String _name;

		private int _period;

		private Permission _perm;

		public Limit() {
		}

		public void doFailed(User user) {
			Trial.doAction(getActionFailed(), user);
		}

		public void doPassed(User user) {
			Trial.doAction(getActionPassed(), user);
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

		public String getName() {
			return _name;
		}

		public int getPeriod() {
			return _period;
		}

		public Permission getPerm() {
			return _perm;
		}

		public void setActionFailed(String action) {
			validateAction(action);
			_actionFailed = action;
		}

		public void setActionPassed(String action) {
			validateAction(action);
			_actionPassed = action;
		}

		public void setBytes(long bytes) {
			_bytes = bytes;
		}

		public void setName(String name) {
			_name = name;
		}

		public void setPeriod(int period) {
			_period = period;
		}

		public void setPerm(Permission perm) {
			_perm = perm;
		}

		public String toString() {
			return "Limit[name=" + _name + ",bytes="
					+ Bytes.formatBytes(_bytes) + ",period="
					+ Trial.getPeriodName(_period) + "]";
		}

		private void validateAction(String action) {
			if (action == null) {
				return;
			}

			StringTokenizer st = new StringTokenizer(action);

			if (!st.hasMoreTokens()) {
				return;
			}

			String cmd = st.nextToken();

			if (!("delete".equals(action) || "purge".equals(action)
					|| "chgrp".equals(cmd) || "setgrp".equals(cmd))) {
				throw new IllegalArgumentException(cmd
						+ " is not a valid action");
			}

			if ("setgrp".equals(cmd)) {
				st.nextToken();

				if (st.hasMoreTokens()) {
					throw new IllegalArgumentException("extra tokens in \""
							+ action + "\"");
				}
			}
		}
	}
}
