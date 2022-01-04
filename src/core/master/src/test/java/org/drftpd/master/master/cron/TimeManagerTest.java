/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * DrFTPD is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with DrFTPD; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.drftpd.master.master.cron;

import org.drftpd.master.cron.TimeEventInterface;
import org.drftpd.master.cron.TimeManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author zubov
 * @version $Id$
 */
public class TimeManagerTest {

    TimeManager _tm = null;
    int _lastReset = 0;
    boolean _resetWeek = false;
    int _hoursReset = 0;
    int _daysReset = 0;
    int _monthsReset = 0;
    int _yearsReset = 0;
    int _weeksReset = 0;

    @BeforeEach
    void setUp() {
        _tm = new TimeManager(Calendar.getInstance());
        _tm.addTimeEvent(new TimeTester());
        _tm.addTimeEvent(new TimeTesterAdder());
    }

    @Test
    public void testDoReset() throws ParseException {
        Calendar cal = Calendar.getInstance();
        SimpleDateFormat df = new SimpleDateFormat("MM/dd/yy HH:mm");
        Date date;
        // parseFormat = MM/dd/yy HH:mm

        // test year & week
        _resetWeek = false;
        _lastReset = Calendar.ERA; // used as a un-set value
        date = df.parse("1/1/08 00:00"); // tuesday, so never gonna be the First the of Week
        System.out.println("Testing date - " + date);
        cal.setTime(date);
        _tm.doReset(cal);
        assertEquals(Calendar.YEAR, _lastReset);
        assertFalse(_resetWeek);

        // test month & week
        _resetWeek = false;
        _lastReset = Calendar.ERA; // used as a un-set value
        date = df.parse("2/1/07 00:00");
        System.out.println("Testing date - " + date);
        cal.setTime(date);
        _tm.doReset(cal);
        assertEquals(Calendar.MONTH, _lastReset);
        assertFalse(_resetWeek);

        // test day & week
        _resetWeek = false;
        _lastReset = Calendar.ERA; // used as a un-set value
        date = df.parse("2/3/07 00:00");
        System.out.println("Testing date - " + date);
        cal.setTime(date);
        _tm.doReset(cal);
        assertEquals(Calendar.DAY_OF_MONTH, _lastReset);
        assertFalse(_resetWeek);

        // test hour & week
        _resetWeek = false;
        _lastReset = Calendar.ERA; // used as a un-set value
        date = df.parse("1/1/07 2:00");
        System.out.println("Testing date - " + date);
        cal.setTime(date);
        _tm.doReset(cal);
        assertEquals(Calendar.HOUR, _lastReset);
        assertFalse(_resetWeek);

    }

    @Test
    public void testProcessTimeEventsSinceDate() throws ParseException {
        SimpleDateFormat df = new SimpleDateFormat("MM/dd/yy HH:mm");
        Date oldDate = df.parse("12/25/06 3:00");
        Date newDate = df.parse("2/17/07 19:00");
        _tm.processTimeEventsBetweenDates(oldDate, newDate);
        assertEquals(1, _yearsReset);
        assertEquals(2, _monthsReset);
        assertEquals(7, _weeksReset);
        assertEquals(54, _daysReset);
        assertEquals(1313, _hoursReset);
    }

    public class TimeTester implements TimeEventInterface {

        public void resetDay(Date d) {
            _lastReset = Calendar.DAY_OF_MONTH;
        }

        public void resetHour(Date d) {
            _lastReset = Calendar.HOUR;
        }

        public void resetMonth(Date d) {
            _lastReset = Calendar.MONTH;
        }

        public void resetWeek(Date d) {
            _resetWeek = true;
        }

        public void resetYear(Date d) {
            _lastReset = Calendar.YEAR;
        }
    }

    public class TimeTesterAdder implements TimeEventInterface {

        public void resetDay(Date d) {
            _daysReset++;
            resetHour(d);
        }

        public void resetHour(Date d) {
            _hoursReset++;
        }

        public void resetMonth(Date d) {
            _monthsReset++;
            resetDay(d);
        }

        public void resetWeek(Date d) {
            _weeksReset++;
        }

        public void resetYear(Date d) {
            _yearsReset++;
            resetMonth(d);
        }
    }
}
