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
package org.drftpd.master.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;


/**
 * @author mog
 * @version $Id$
 */
public class TimeTest {

    @Test
    public void testParseSeconds() {
        assertEquals(1000, Time.parseTime("1s"));
        assertEquals(1000, Time.parseTime("1S"));
    }

    @Test
    public void testParseMillis() {
        assertEquals(1, Time.parseTime("1ms"));
    }

    @Test
    public void testParse() {
        assertEquals(1, Time.parseTime("1"));
    }

    @Test
    public void testParseMinutes() {
        assertEquals(60000, Time.parseTime("1m"));
    }
}
