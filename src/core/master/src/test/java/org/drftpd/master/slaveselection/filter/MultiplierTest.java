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
package org.drftpd.master.slaveselection.filter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MultiplierTest {

    @Test
    public void testDivide() {
        assertEquals(10F, BandwidthFilter.parseMultiplier("/0.1"));
    }

    @Test
    public void testDivideMultiplyMultiply() {
        assertEquals(0.1F, BandwidthFilter.parseMultiplier("/10*10/10"));
    }

    @Test
    public void testMultiply() {
        assertEquals(100F, BandwidthFilter.parseMultiplier("*100"));
    }

    @Test
    public void testMultiplyDivide() {
        assertEquals(1F, BandwidthFilter.parseMultiplier("/10*10"));
    }

    @Test
    public void testMultiplyMultiplyDivide() {
        assertEquals(10F, BandwidthFilter.parseMultiplier("10*10/10"));
    }

    @Test
    public void testSimple() {
        assertEquals(100F, BandwidthFilter.parseMultiplier("100"));
    }
}
