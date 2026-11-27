package org.drftpd.master.slavemanagement;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RemoteSlaveRemergeModeTest {

    @Test
    void absentModeIsNotInstantAndDoesNotThrow() {
        // partial.remerge.mode missing from master.conf used to NPE in makeAvailableAfterRemerge()
        assertFalse(RemoteSlave.isInstantRemerge(null));
    }

    @Test
    void instantIsCaseInsensitive() {
        assertTrue(RemoteSlave.isInstantRemerge("instant"));
        assertTrue(RemoteSlave.isInstantRemerge("INSTANT"));
    }

    @Test
    void otherModesAreNotInstant() {
        assertFalse(RemoteSlave.isInstantRemerge("off"));
        assertFalse(RemoteSlave.isInstantRemerge("connect"));
        assertFalse(RemoteSlave.isInstantRemerge("disconnect"));
        assertFalse(RemoteSlave.isInstantRemerge(""));
    }
}
