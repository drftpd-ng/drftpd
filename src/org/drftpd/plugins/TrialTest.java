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

import junit.framework.TestCase;
import junit.framework.TestSuite;

import net.sf.drftpd.event.UserEvent;
import net.sf.drftpd.util.CalendarUtils;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Logger;

import org.drftpd.Bytes;
import org.drftpd.tests.DummyUser;

import java.util.Calendar;
import java.util.Locale;
import java.util.Properties;


/**
 * @author mog
 * @version $Id$
 */
public class TrialTest extends TestCase {
    /**
     * Mon Dec 15 23:59:59 CET 2003
     */
    private static final long RESETTIME = 1071519356421L;
    private static final long TESTBYTES = Bytes.parseBytes("100m");
    private Calendar cal;
    private int period;
    private Trial trial;
    private DummyUser user;

    public TrialTest(String fName) {
        super(fName);
    }

    public static TestSuite suite() {
        //TestSuite suite = new TestSuite();
        //suite.addTest(new TrialTest("testMonth"));
        //return suite;
        return new TestSuite(TrialTest.class);
    }

    private void action(int period) {
        trial.actionPerformed(getUserEvent(period));
    }

    private void action() {
        action(period);
    }

    private void assertUserFailed() {
        assertTrue(user.getGroups().toString(), user.isMemberOf("FAiLED"));
        assertFalse(user.getGroups().toString(), user.isMemberOf("PASSED"));
    }

    private void assertUserNeither() {
        assertEquals(user.getGroups().toString(), 0, user.getGroups().size());
    }

    private void assertUserPassed() {
        assertTrue(user.getGroups().toString(), user.isMemberOf("PASSED"));
        assertFalse(user.getGroups().toString(), user.isMemberOf("FAiLED"));
    }

    private Calendar getJUnitCalendar() {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(RESETTIME);
        CalendarUtils.ceilAllLessThanDay(cal);

        return cal;
    }

    private Trial getJUnitTrial() throws Exception {
        Properties p = new Properties();
        p.setProperty("1.period", Trial.getPeriodName2(period));
        p.setProperty("1.fail", "chgrp FAiLED");
        p.setProperty("1.pass", "chgrp PASSED");
        p.setProperty("1.quota", "" + TESTBYTES);
        p.setProperty("1.name", Trial.getPeriodName(period));
        p.setProperty("1.perm", "*");

        Trial trial = new Trial();
        trial.reload(p);

        return trial;
    }

    /**
     * Returns a fresh user object.
     * @return a fresh user object.
     */
    private DummyUser getJUnitUser() {
        DummyUser user = new DummyUser("junit", RESETTIME);
        user.setLastReset(cal.getTimeInMillis());

        return user;
    }

    private UserEvent getUserEvent(int period) {
        return new UserEvent(user, getCommandFromPeriod(period),
            cal.getTimeInMillis());
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
        user.setUploadedBytesForPeriod(period, TESTBYTES);
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
        assertEquals(Calendar.SATURDAY,
            Trial.getCalendarForEndOfPeriod(Trial.PERIOD_WEEKLY).get(Calendar.DAY_OF_WEEK));

        //Locale.setDefault(new Locale("sv", "SE"));
        Locale.setDefault(Locale.FRANCE);
        assertEquals(Calendar.SUNDAY,
            Trial.getCalendarForEndOfPeriod(Trial.PERIOD_WEEKLY).get(Calendar.DAY_OF_WEEK));
    }

    /**
     * Sets up trial, cal and user.
     * period must be set before internalSetUp() is called because of Limit period.
     */
    private void internalSetUp() throws Exception {
        trial = getJUnitTrial();
        cal = getJUnitCalendar();
        Logger.getLogger(TrialTest.class).debug("cal = " + cal.getTime());
        user = getJUnitUser();
    }

    public static String getCommandFromPeriod(int period) {
        switch (period) {
        case Trial.PERIOD_DAILY:
            return "RESETDAY";

        case Trial.PERIOD_MONTHLY:
            return "RESETMONTH";

        case Trial.PERIOD_WEEKLY:
            return "RESETWEEK";

        default:
            throw new RuntimeException();
        }
    }
}
