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
package org.drftpd;

import junit.framework.TestCase;
import junit.framework.TestSuite;


/**
 * @author mog
 * @version $Id: TimeTest.java 690 2004-08-03 20:14:12Z zubov $
 */
public class TimeTest extends TestCase {
    public TimeTest(String name) {
        super(name);
    }

    public static TestSuite suite() {
        return new TestSuite(TimeTest.class);
    }

    public void testParseSeconds() {
        assertEquals(1000, Time.parseTime("1s"));
        assertEquals(1000, Time.parseTime("1S"));
    }

    public void testParseMillis() {
        assertEquals(1, Time.parseTime("1ms"));
    }

    public void testParse() {
        assertEquals(1, Time.parseTime("1"));
    }

    public void testParseMinutes() {
        assertEquals(60000, Time.parseTime("1m"));
    }
}
