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
package net.sf.drftpd.master.usermanager;

import junit.framework.TestCase;

import net.sf.drftpd.event.Event;
import net.sf.drftpd.event.UserEvent;
import net.sf.drftpd.master.ConnectionManager;
import net.sf.drftpd.util.CalendarUtils;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Logger;

import org.drftpd.tests.DummyUser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.List;


/**
 * @author mog
 * @version $Id: AbstractUserTest.java,v 1.5 2004/08/03 20:13:59 zubov Exp $
 */
public class AbstractUserTest extends TestCase {
    private static final Logger logger = Logger.getLogger(AbstractUserTest.class);

    /**
     * Mon Dec 15 23:59:59 CET 2003
     */
    private static final long RESETTIME = 1071519356421L;
    private CM _cm;
    private Calendar _resetCal;
    private DummyUser _user;

    public AbstractUserTest(String fName) {
        super(fName);
    }

    private static void verifyEvents(ArrayList eventNames, ArrayList events) {
        logger.debug(events);
i1: 
        for (Iterator i1 = events.iterator(); i1.hasNext();) {
            UserEvent event1 = (UserEvent) i1.next();
            String eventName2 = null;

            for (Iterator i2 = eventNames.iterator(); i2.hasNext();) {
                eventName2 = (String) i2.next();

                if (event1.getCommand().equals(eventName2)) {
                    i2.remove();

                    //if(eventNames.size() == 0) return;
                    continue i1;
                }
            }

            if (eventName2 == null) {
                throw new RuntimeException(
                    "no eventNames to look for when looking for " +
                    event1.getCommand());
            }

            throw new RuntimeException(eventName2 +
                " was expected but wasn't found in " + events);
        }

        if (eventNames.size() != 0) {
            throw new RuntimeException("The events " + eventNames +
                " were not in the list of expected events (" + events + ")");
        }
    }

    private static void verifyEvents(List tmpEventNames, ArrayList events) {
        verifyEvents(new ArrayList(tmpEventNames), events);
    }

    public void resetSetUp() {
        _cm = new CM();

        _user = new DummyUser("junit", null);
        _user.setLastReset(RESETTIME - 10000);

        _resetCal = Calendar.getInstance();
        _resetCal.setTimeInMillis(RESETTIME);
    }

    public void setUp() {
        BasicConfigurator.configure();
    }

    public void testResetDay() throws UserFileException {
        resetSetUp();
        _resetCal.add(Calendar.DAY_OF_MONTH, 1);
        CalendarUtils.floorAllLessThanDay(_resetCal);
        logger.debug("resetDay lastreset " + new Date(_user.getLastReset()));
        logger.debug("resetDay date " + _resetCal.getTime());
        _user.reset(_cm, _resetCal);
        verifyEvents(Arrays.asList(new String[] { "RESETDAY" }), _cm.events);
    }

    public void testResetMonth() throws UserFileException {
        resetSetUp();
        _resetCal.add(Calendar.MONTH, 1);
        CalendarUtils.floorDayOfMonth(_resetCal);
        CalendarUtils.floorAllLessThanDay(_resetCal);
        logger.debug("resetMonth lastreset " + new Date(_user.getLastReset()));
        logger.debug("resetMonth date " + _resetCal.getTime());
        _user.reset(_cm, _resetCal);
        verifyEvents(Arrays.asList(
                new String[] { "RESETDAY", "RESETWEEK", "RESETMONTH" }),
            _cm.events);
    }

    public void testResetNone1() throws UserFileException {
        resetSetUp();
        CalendarUtils.ceilAllLessThanDay(_resetCal);
        _user.reset(_cm, _resetCal);
    }

    public void testResetNone2() throws UserFileException {
        resetSetUp();
        CalendarUtils.floorAllLessThanDay(_resetCal);
        _user.reset(_cm, _resetCal);
    }

    public void testResetWeek() throws UserFileException {
        resetSetUp();
        _resetCal.add(Calendar.WEEK_OF_MONTH, 1);
        CalendarUtils.floorDayOfWeek(_resetCal);
        CalendarUtils.floorAllLessThanDay(_resetCal);
        logger.debug("resetWeek lastreset " + new Date(_user.getLastReset()));
        logger.debug("resetWeek date " + _resetCal.getTime());
        _user.reset(_cm, _resetCal);
        verifyEvents(Arrays.asList(new String[] { "RESETDAY", "RESETWEEK" }),
            _cm.events);
    }

    private static class CM extends ConnectionManager {
        public ArrayList events = new ArrayList();

        public void dispatchFtpEvent(Event event) {
            events.add(event);
        }
    }
}
