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

import org.drftpd.master.slavemanagement.DummyRemoteSlave;
import org.drftpd.master.slavemanagement.RemoteSlave;
import org.drftpd.master.tests.DummyGlobalContext;
import org.drftpd.master.tests.DummySlaveManager;
import org.drftpd.slave.exceptions.ObjectNotFoundException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AssignSlaveTest {
    private final RemoteSlave[] rslaves = {
            new DummyRemoteSlave("slave1"),
            new DummyRemoteSlave("slave2"),
            new DummyRemoteSlave("slave-1")
    };

    @BeforeAll
    static void setUp() {
        AssignSlave.setGlobalContext(new DummyGlobalContext());
        DummyGlobalContext dgctx = (DummyGlobalContext) AssignSlave.getGlobalContext();
        dgctx.setSlaveManager(new DummySlaveManager());
    }

    private ScoreChart apply(String assign) throws ObjectNotFoundException {
        ScoreChart sc = new ScoreChart(Arrays.asList(rslaves));
        AssignSlave.addScoresToChart(AssignSlave.parseAssign(assign), sc);
        return sc;
    }

    @Test
    public void testAllDoesNotSkipLaterEntries() throws Exception {
        ScoreChart sc = apply("all-1000 slave1+1000");
        assertEquals(0, sc.getScoreForSlave(rslaves[0]).getScore());
        assertEquals(-1000, sc.getScoreForSlave(rslaves[1]).getScore());
        assertEquals(-1000, sc.getScoreForSlave(rslaves[2]).getScore());
    }

    @Test
    public void testAllPositive() throws Exception {
        ScoreChart sc = apply("ALL+100 slave2-remove");
        assertEquals(100, sc.getScoreForSlave(rslaves[0]).getScore());
        assertThrows(ObjectNotFoundException.class, () -> sc.getScoreForSlave(rslaves[1]));
        assertEquals(100, sc.getScoreForSlave(rslaves[2]).getScore());
    }

    @Test
    public void testAllRemove() throws Exception {
        for (String assign : new String[]{"all+remove", "all-remove", "all-remove slave1+1000"}) {
            ScoreChart sc = apply(assign);
            assertTrue(sc.isEmpty(), assign);
        }
    }

    @Test
    public void testAllRemoveAfterScores() throws Exception {
        assertTrue(apply("slave1+10 all-remove").isEmpty());
    }

    @Test
    public void testHyphenatedNames() throws Exception {
        ScoreChart sc = apply("slave-1-500");
        assertEquals(0, sc.getScoreForSlave(rslaves[0]).getScore());
        assertEquals(-500, sc.getScoreForSlave(rslaves[2]).getScore());

        sc = apply("slave-1+500");
        assertEquals(0, sc.getScoreForSlave(rslaves[0]).getScore());
        assertEquals(500, sc.getScoreForSlave(rslaves[2]).getScore());

        ScoreChart removed = apply("slave-1-remove");
        assertEquals(2, removed.getSlaveScores().size());
        assertThrows(ObjectNotFoundException.class, () -> removed.getScoreForSlave(rslaves[2]));
    }

    @Test
    public void testSeparatorIndex() {
        assertEquals(7, AssignSlave.findAssignSeparator("slave-1-500"));
        assertEquals(7, AssignSlave.findAssignSeparator("slave-1+500"));
        assertEquals(7, AssignSlave.findAssignSeparator("slave-1-remove"));
        assertEquals(6, AssignSlave.findAssignSeparator("slave1+5"));
        assertEquals(1, AssignSlave.findAssignSeparator("a-1"));
        assertEquals(-1, AssignSlave.findAssignSeparator("slave1"));
        assertEquals(-1, AssignSlave.findAssignSeparator("slave1+abc"));
        assertEquals(-1, AssignSlave.findAssignSeparator("slave1+"));
    }

    @Test
    public void testInvalid() {
        assertThrows(IllegalArgumentException.class, () -> AssignSlave.parseAssign("slave1"));
        assertThrows(IllegalArgumentException.class, () -> AssignSlave.parseAssign("slave1+abc"));
    }

    @Test
    public void testParseCount() throws Exception {
        ArrayList<AssignParser> aps = AssignSlave.parseAssign("all-1000, slave-1+5");
        assertEquals(2, aps.size());
        assertTrue(aps.get(0).allAssigned());
        assertEquals(-1000, aps.get(0).getScore());
        assertEquals("slave-1", aps.get(1).getRSlave().getName());
    }
}
