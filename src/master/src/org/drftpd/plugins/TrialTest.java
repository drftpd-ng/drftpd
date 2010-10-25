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


import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Logger;

import org.drftpd.Bytes;
import org.drftpd.event.UserEvent;
import org.drftpd.tests.DummyUser;
import org.drftpd.util.CalendarUtils;

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
    private Calendar _cal;
    private int _period;
    private Trial _trial;
    private DummyUser _user;

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
        _trial.actionPerformed(getUserEvent(period));
    }

    private void action() {
        action(_period);
    }

    private void assertUserFailed() {
        assertTrue(_user.getGroups().toString(), _user.isMemberOf("FAiLED"));
        assertFalse(_user.getGroups().toString(), _user.isMemberOf("PASSED"));
    }

    private void assertUserNeither() {
        assertEquals(_user.getGroups().toString(), 0, _user.getGroups().size());
    }

    private void assertUserPassed() {
        assertTrue(_user.getGroups().toString(), _user.isMemberOf("PASSED"));
        assertFalse(_user.getGroups().toString(), _user.isMemberOf("FAiLED"));
    }

    private Calendar getJUnitCalendar() {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(RESETTIME);
        CalendarUtils.ceilAllLessThanDay(cal);

        return cal;
    }

    private Trial getJUnitTrial() throws Exception {
        Properties p = new Properties();
        p.setProperty("1.period", Trial.getPeriodName2(_period));
        p.setProperty("1.fail", "chgrp FAiLED");
        p.setProperty("1.pass", "chgrp PASSED");
        p.setProperty("1.quota", "" + TESTBYTES);
        p.setProperty("1.name", Trial.getPeriodName(_period));
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
        user.setLastReset(_cal.getTimeInMillis());

        return user;
    }

    private UserEvent getUserEvent(int period) {
        return new UserEvent(_user, getCommandFromPeriod(period),
            _cal.getTimeInMillis());
    }

    private void internalTestBeforeUnique() throws Exception {
        //before unique period is over
        internalSetUp();
        action(Trial.PERIOD_DAILY);
        assertUserNeither();
    }

    private void internalTestUnique() throws Exception {
        internalSetUp();
        _cal.add(_period, 1);

        //fail
        _user = getJUnitUser();
        action(Trial.PERIOD_DAILY);
        assertUserFailed();

        //pass
        _user = getJUnitUser();
        _user.setUploadedBytes(TESTBYTES);
        action(Trial.PERIOD_DAILY);
        assertUserPassed();
    }

    private void internalTestUniqueAfterUnique() throws Exception {
        internalSetUp();
        _cal.add(_period, 1);

        Calendar calOld = (Calendar) _cal.clone();
        _cal.add(_period, 1);

        //fail
        _user = getJUnitUser();
        _user.setLastReset(calOld.getTimeInMillis());
        action(Trial.PERIOD_DAILY);
        assertUserFailed();

        //pass
        _user = getJUnitUser();
        _user.setLastReset(calOld.getTimeInMillis());
        _user.setUploadedBytes(TESTBYTES);
        action(Trial.PERIOD_DAILY);
        assertUserPassed();

        //neither (regular fail for daily reset)
        if (_period != Trial.PERIOD_DAILY) {
            _user = getJUnitUser();
            action(Trial.PERIOD_DAILY);
            assertUserNeither();
        }
    }

    private void internalTestRegular() throws Exception {
        internalSetUp();
        _cal.add(_period, 2);

        //pass real period
        _user = getJUnitUser();
        _user.setUploadedBytesForPeriod(_period, TESTBYTES);
        action();
        assertUserPassed();

        _user = getJUnitUser();
        action();
        assertUserFailed();
    }

    protected void setUp() throws Exception {
        BasicConfigurator.configure();
    }

    protected void tearDown() throws Exception {
    }

    public void testDayBeforeUnique() throws Exception {
        _period = Trial.PERIOD_DAILY;
        internalTestBeforeUnique();
    }

    public void testDayUnique() throws Exception {
        _period = Trial.PERIOD_DAILY;
        internalTestUnique();
    }

    public void testDayRegular() throws Exception {
        _period = Trial.PERIOD_DAILY;
        internalTestRegular();
    }

    public void testDayUniqueAfterUnique() throws Exception {
        _period = Trial.PERIOD_DAILY;
        internalTestUniqueAfterUnique();
    }

    public void testMonthBeforeUnique() throws Exception {
        _period = Trial.PERIOD_MONTHLY;
        internalTestBeforeUnique();
    }

    public void testMonthUnique() throws Exception {
        _period = Trial.PERIOD_MONTHLY;
        internalTestUnique();
    }

    public void testMonthRegular() throws Exception {
        _period = Trial.PERIOD_MONTHLY;
        internalTestRegular();
    }

    public void testMonthUniqueAfterUnique() throws Exception {
        _period = Trial.PERIOD_MONTHLY;
        internalTestUniqueAfterUnique();
    }

    public void testWeekBeforeUnique() throws Exception {
        _period = Trial.PERIOD_WEEKLY;
        internalTestBeforeUnique();
    }

    public void testWeekRegular() throws Exception {
        _period = Trial.PERIOD_WEEKLY;
        internalTestRegular();
    }

    public void testWeekUniqueAfterUnique() throws Exception {
        _period = Trial.PERIOD_WEEKLY;
        internalTestUniqueAfterUnique();
    }

    public void testWeekUnique() throws Exception {
        _period = Trial.PERIOD_WEEKLY;
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
        _trial = getJUnitTrial();
        _cal = getJUnitCalendar();
        Logger.getLogger(TrialTest.class).debug("cal = " + _cal.getTime());
        _user = getJUnitUser();
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
