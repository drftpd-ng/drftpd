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
package org.drftpd.usermanager;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Iterator;

import junit.framework.TestCase;
import net.sf.drftpd.event.Event;
import net.sf.drftpd.event.UserEvent;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Logger;
import org.drftpd.tests.DummyGlobalContext;
import org.drftpd.tests.DummyUser;


/**
 * @author mog
 * @version $Id$
 */
public class AbstractUserTest extends TestCase {
    private static final Logger logger = Logger.getLogger(AbstractUserTest.class);

    /**
     * Mon Dec 15 23:59:59 CET 2003
     */
    private static final long RESETTIME = 1071519356421L;
    private GCtx _gctx;
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

    public void resetSetUp() {
        _gctx = new GCtx();

        _user = new DummyUser("junit", null);
        _user.setLastReset(RESETTIME - 10000);

        _resetCal = Calendar.getInstance();
        _resetCal.setTimeInMillis(RESETTIME);
    }

    public void setUp() {
        BasicConfigurator.configure();
    }

    private static class GCtx extends DummyGlobalContext {
        public ArrayList<Event> events = new ArrayList<Event>();

        public void dispatchFtpEvent(Event event) {
            events.add(event);
        }
    }
}
