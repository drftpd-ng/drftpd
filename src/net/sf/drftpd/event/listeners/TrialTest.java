package net.sf.drftpd.event.listeners;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Locale;

import junit.framework.TestCase;
import junit.framework.TestSuite;
import net.sf.drftpd.Bytes;
import net.sf.drftpd.event.UserEvent;
import net.sf.drftpd.master.config.Permission;
import net.sf.drftpd.master.usermanager.StaticUser;
import net.sf.drftpd.util.CalendarUtils;

import org.apache.log4j.BasicConfigurator;

/**
 * @author mog
 * @version $Id: TrialTest.java,v 1.3 2004/01/13 00:38:55 mog Exp $
 */
public class TrialTest extends TestCase {
	/**
	 * Mon Dec 15 23:59:59 CET 2003
	 */
	private static final long RESETTIME = 1071519356421L;
	private static final long TESTBYTES = Bytes.parseBytes("100m");
	public static TestSuite suite() {
		//TestSuite suite = new TestSuite();
		//suite.addTest(new TrialTest("testMonth"));
		//return suite;
		return new TestSuite(TrialTest.class);
	}
	private Calendar cal;
	private int period;
	private Trial trial;
	private StaticUser user;

	public TrialTest(String fName) {
		super(fName);
	}

	private void action(int period) {
		trial.actionPerformed(getUserEvent(period));
	}
	private void action() {
		action(period);
	}
	private void assertUserFailed() {
		assertTrue(user.getGroups().toString(), user.isMemberOf("failed"));
		assertFalse(user.getGroups().toString(), user.isMemberOf("passed"));
	}
	private void assertUserNeither() {
		assertEquals(user.getGroups().toString(), 0, user.getGroups().size());
	}
	private void assertUserPassed() {
		assertTrue(user.getGroups().toString(), user.isMemberOf("passed"));
		assertFalse(user.getGroups().toString(), user.isMemberOf("failed"));
	}
	private Calendar getJUnitCalendar() {
		Calendar cal = Calendar.getInstance();
		cal.setTimeInMillis(RESETTIME);
		CalendarUtils.ceilAllLessThanDay(cal);
		return cal;
	}

	private Limit getJUnitLimit() {
		Limit limit = new Limit();
		limit.setActionFailed("chgrp failed");
		limit.setActionPassed("chgrp passed");
		limit.setBytes(TESTBYTES);
		limit.setName(Trial.getPeriodName(period));
		limit.setPerm(new Permission(Arrays.asList(new String[] { "*" })));
		return limit;
	}

	private Trial getJUnitTrial() throws Exception {
		ArrayList limits = new ArrayList();
		Limit limit = getJUnitLimit();
		limit.setPeriod(period);
		limits.add(limit);
		Trial trial = new Trial();
		trial.reload(limits);
		return trial;
	}

	/**
	 * Returns a fresh user object.
	 * @return a fresh user object.
	 */
	private StaticUser getJUnitUser() {
		StaticUser user = new StaticUser("junit", RESETTIME);
		user.setLastReset(cal.getTimeInMillis());
		return user;
	}

	private UserEvent getUserEvent(int period) {
		return new UserEvent(user, period, cal.getTimeInMillis());
	}

	private void internalTestBeforeUnique() throws Exception {
		//before unique period is over
		internalSetUp();
		action(Trial.PERIOD_DAILY);
		assertUserNeither();
	}

	private void internalTestUnique() throws Exception {
		internalSetUp();
		cal.add(period, 1);

		//fail
		user = getJUnitUser();
		action(Trial.PERIOD_DAILY);
		assertUserFailed();

		//pass
		user = getJUnitUser();
		user.setUploadedBytes(TESTBYTES);
		action(Trial.PERIOD_DAILY);
		assertUserPassed();
	}

	private void internalTestUniqueAfterUnique() throws Exception {
		internalSetUp();
		cal.add(period, 1);
		Calendar calOld = (Calendar) cal.clone();
		cal.add(period, 1);

		//fail
		user = getJUnitUser();
		user.setLastReset(calOld.getTimeInMillis());
		action(Trial.PERIOD_DAILY);
		assertUserFailed();

		//pass
		user = getJUnitUser();
		user.setLastReset(calOld.getTimeInMillis());
		user.setUploadedBytes(TESTBYTES);
		action(Trial.PERIOD_DAILY);
		assertUserPassed();

		//neither (regular fail for daily reset)
		if (period != Trial.PERIOD_DAILY) {
			user = getJUnitUser();
			action(Trial.PERIOD_DAILY);
			assertUserNeither();
		}
	}

	private void internalTestRegular() throws Exception {
		internalSetUp();
		cal.add(period, 2);
		//pass real period
		user = getJUnitUser();
		user.setUploadedBytesPeriod(period, TESTBYTES);
		action();
		assertUserPassed();

		user = getJUnitUser();
		action();
		assertUserFailed();
	}

	protected void setUp() throws Exception {
		BasicConfigurator.configure();
	}

	protected void tearDown() throws Exception {
	}

	public void testDayBeforeUnique() throws Exception {
		period = Trial.PERIOD_DAILY;
		internalTestBeforeUnique();
	}

	public void testDayUnique() throws Exception {
		period = Trial.PERIOD_DAILY;
		internalTestUnique();
	}

	public void testDayRegular() throws Exception {
		period = Trial.PERIOD_DAILY;
		internalTestRegular();
	}

	public void testDayUniqueAfterUnique() throws Exception {
		period = Trial.PERIOD_DAILY;
		internalTestUniqueAfterUnique();
	}

	public void testMonthBeforeUnique() throws Exception {
		period = Trial.PERIOD_MONTHLY;
		internalTestBeforeUnique();
	}

	public void testMonthUnique() throws Exception {
		period = Trial.PERIOD_MONTHLY;
		internalTestUnique();
	}

	public void testMonthRegular() throws Exception {
		period = Trial.PERIOD_MONTHLY;
		internalTestRegular();
	}

	public void testMonthUniqueAfterUnique() throws Exception {
		period = Trial.PERIOD_MONTHLY;
		internalTestUniqueAfterUnique();
	}

	public void testWeekBeforeUnique() throws Exception {
		period = Trial.PERIOD_WEEKLY;
		internalTestBeforeUnique();
	}

	public void testWeekRegular() throws Exception {
		period = Trial.PERIOD_WEEKLY;
		internalTestRegular();
	}

	public void testWeekUniqueAfterUnique() throws Exception {
		period = Trial.PERIOD_WEEKLY;
		internalTestUniqueAfterUnique();
	}

	public void testWeekUnique() throws Exception {
		period = Trial.PERIOD_WEEKLY;
		internalTestUnique();
	}

	public void testGetCalendarEndOfWeek() {
		Locale.setDefault(Locale.ENGLISH);
		cal = getJUnitCalendar();
		assertEquals(Calendar.SATURDAY, Trial.getCalendarForEndOfPeriod(Trial.PERIOD_WEEKLY).get(Calendar.DAY_OF_WEEK));
		
		Locale.setDefault(new Locale("sv_SE"));
		cal = getJUnitCalendar();
		assertEquals(Calendar.SUNDAY, Trial.getCalendarForEndOfPeriod(Trial.PERIOD_WEEKLY).get(Calendar.DAY_OF_WEEK));
	}
	/**
	 * Sets up trial, cal and user.
	 * period must be set before internalSetUp() is called because of Limit period.
	 */
	private void internalSetUp() throws Exception {
		trial = getJUnitTrial();
		cal = getJUnitCalendar();
		user = getJUnitUser();
	}
}
