package org.drftpd.archive.master.archivetypes;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for issue #276 (item 4): MoveReleaseOffSlavesToMostFreeSlaves must only move files that
 * are on an offofslave and must keep their copies on the other slaves.
 */
public class OffSlaveSelectionTest {

    private static final Set<String> OFF = Set.of("SlaveTEST");
    private static final Set<String> FREE = Set.of("Slave3");

    @Test
    void fileOnOffSlaveNeedsMoving() {
        assertTrue(OffSlaveSelection.needsMoving(Set.of("SlaveTEST", "Slave1"), OFF));
    }

    @Test
    void fileNotOnOffSlaveIsLeftAlone() {
        assertFalse(OffSlaveSelection.needsMoving(Set.of("Slave1", "Slave2", "Slave3"), OFF));
        assertNull(OffSlaveSelection.jobDestination(Set.of("Slave1", "Slave2", "Slave3"), OFF, FREE));
    }

    @Test
    void fileOnlyOnOffSlaveGoesToFreeSlave() {
        assertEquals(Set.of("Slave3"), OffSlaveSelection.jobDestination(Set.of("SlaveTEST"), OFF, FREE));
    }

    @Test
    void copiesOnOtherSlavesAreKept() {
        // the Job deletes from every slave not in this set, so Slave1 and Slave2 have to be in it
        assertEquals(Set.of("Slave1", "Slave2", "Slave3"),
                OffSlaveSelection.jobDestination(Set.of("SlaveTEST", "Slave1", "Slave2"), OFF, FREE));
    }

    @Test
    void offSlaveIsNeverInDestination() {
        Set<String> dest = OffSlaveSelection.jobDestination(Set.of("SlaveTEST", "Slave1", "Slave2", "Slave3"), OFF, FREE);
        assertEquals(Set.of("Slave1", "Slave2", "Slave3"), dest);
        assertFalse(dest.contains("SlaveTEST"));
    }

    @Test
    void multipleOffSlavesAndFreeSlaves() {
        Set<String> dest = OffSlaveSelection.jobDestination(Set.of("A", "B", "C"), Set.of("A", "B"), Set.of("D", "E"));
        assertEquals(Set.of("C", "D", "E"), dest);
    }

    @Test
    void noFreeSlaveMeansNothingIsMoved() {
        assertNull(OffSlaveSelection.jobDestination(Set.of("SlaveTEST"), OFF, Set.of()));
        assertNull(OffSlaveSelection.jobDestination(Set.of("SlaveTEST", "Slave1"), OFF, Set.of()));
    }
}
