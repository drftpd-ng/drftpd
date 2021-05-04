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
package org.drftpd.master.cron;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.drftpd.master.GlobalContext;
import org.drftpd.master.config.ConfigInterface;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

/**
 * @author zubov
 * @version $Id$
 */
public class TimeManager {

    private static final Logger logger = LogManager.getLogger(TimeManager.class);

    private static final long MINUTE = 60 * 1000L;
    private static final long HOUR = MINUTE * 60;

    private final ArrayList<TimeEventInterface> _timedEvents;

    TimerTask _processHour = new TimerTask() {
        public void run() {
            doReset(Calendar.getInstance());
        }
    };

    public TimeManager() {
        this(Calendar.getInstance());
    }

    public TimeManager(Calendar cal) {
        _timedEvents = new ArrayList<>();
        Timer timer = GlobalContext.getGlobalContext().getTimer();
        // setup the next time we need to run an event
        // roll the calendar to the next Hour
        cal.add(Calendar.HOUR, 1);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        timer.scheduleAtFixedRate(_processHour, cal.getTime(), HOUR);
        logger.info("TimeManager scheduled the next reset to be at {}", cal.getTime());
    }

    public void doReset(Calendar cal) {
        logger.debug("doReset called - {}", cal.toString());
        // Check if EuropeanCalendar and change if needed
        if (isEuropeanCalendar()) {
            cal.setFirstDayOfWeek(Calendar.MONDAY);
        }

        int dayOfMonth = cal.get(Calendar.DAY_OF_MONTH);
        int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK);
        int hourOfDay = cal.get(Calendar.HOUR_OF_DAY);
        int minuteOfHour = cal.get(Calendar.MINUTE);
        int monthOfYear = cal.get(Calendar.MONTH);

        if (minuteOfHour != 0) {
            throw new IllegalArgumentException("This thread needs to be run within the first minute of the hour, time is - " + cal.getTime());
        }

        if (hourOfDay == 0) {
            // we have started a new day
            if (dayOfWeek == cal.getFirstDayOfWeek()) {
                // we have started a new week
                doMethodOnTimeEvents("resetWeek", cal.getTime());
            }
            if (dayOfMonth == 1) {
                // we have started a new month
                if (monthOfYear == 0) { // january is the 0 month, I dunno...
                    doMethodOnTimeEvents("resetYear", cal.getTime());
                    return;
                }
                doMethodOnTimeEvents("resetMonth", cal.getTime());
                return;
            }
            doMethodOnTimeEvents("resetDay", cal.getTime());
            return;
        }
        doMethodOnTimeEvents("resetHour", cal.getTime());
    }

    private void doMethodOnTimeEvents(String methodName, Date d) {
        List<TimeEventInterface> tempList;
        synchronized (this) {
            tempList = new ArrayList<>(_timedEvents);
        }
        Class<?>[] classArg = new Class<?>[1];
        classArg[0] = Date.class;
        Date[] dateArg = new Date[1];
        dateArg[0] = d;
        for (TimeEventInterface event : tempList) {
            try {
                Method m = TimeEventInterface.class.getDeclaredMethod(methodName,
                        classArg);
                m.invoke(event, d);
            } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                logger.error("{} does not properly implement TimeEventInterface", event.getClass().getName(), e);
            } catch (RuntimeException e) {
                logger.error("{} had an error processing {}", event.getClass().getName(), methodName, e);
            }
        }
    }

    /*
     * Checks conf file to see if european calendar is being used.
     */
    public boolean isEuropeanCalendar() {
        ConfigInterface config = GlobalContext.getConfig();
        // Complain about this situation as it should not happen!
        if (config == null) {
            logger.error("Config from GlobalContext is null, this should not be possible!");
            return false;
        }
        return config.getMainProperties().getProperty("european.cal", "false").equalsIgnoreCase("true");
    }

    public void processTimeEventsSinceDate(Date date) {
        processTimeEventsBetweenDates(date, new Date(System.currentTimeMillis()));
    }

    /**
     * Should be called on startup after the appropriate TimeEventInterfaces have been added
     *
     * @param oldDate The start date
     * @param newDate The end date
     */
    public void processTimeEventsBetweenDates(Date oldDate, Date newDate) {
        Calendar oldCal = Calendar.getInstance();
        Calendar newCal = Calendar.getInstance();
        oldCal.setTime(oldDate);
        newCal.setTime(newDate);
        while (true) {
            if (oldCal.after(newCal)) {
                return;
            }
            doReset(oldCal);
            oldCal.add(Calendar.HOUR, 1);
        }
    }

    public synchronized void addTimeEvent(TimeEventInterface event) {
        _timedEvents.add(event);
    }

    public synchronized void removeTimeEvent(TimeEventInterface event) {
        _timedEvents.remove(event);
    }
}
