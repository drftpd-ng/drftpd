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
package org.drftpd.master.slaveselection.filter.maxtransfers;

import org.drftpd.master.tests.DummySlaveManager;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class MaxtransfersPerslaveFilterTest {
    @Test
    public void testParseAssign() throws Exception {
        ArrayList<MaxtransfersPerslaveFilter.AssignSlave> l = MaxtransfersPerslaveFilter.parseAssign(
                "slave1+10 slave-1-5 slave-2+7", new DummySlaveManager());
        assertEquals(3, l.size());
        assertEquals("slave1", l.get(0).getRSlave().getName());
        assertEquals(10, l.get(0).getMaxtransfers());
        assertEquals("slave-1", l.get(1).getRSlave().getName());
        assertEquals(5, l.get(1).getMaxtransfers());
        assertEquals("slave-2", l.get(2).getRSlave().getName());
        assertEquals(7, l.get(2).getMaxtransfers());
    }

    @Test
    public void testInvalid() {
        assertThrows(IllegalArgumentException.class,
                () -> MaxtransfersPerslaveFilter.parseAssign("slave1", new DummySlaveManager()));
        assertThrows(IllegalArgumentException.class,
                () -> MaxtransfersPerslaveFilter.parseAssign("slave1+remove", new DummySlaveManager()));
    }
}
