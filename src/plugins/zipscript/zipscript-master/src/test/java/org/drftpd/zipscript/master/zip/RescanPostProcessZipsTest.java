package org.drftpd.zipscript.master.zip;

import org.drftpd.zipscript.master.zip.RescanPostProcessZips.DizAction;
import org.drftpd.zipscript.master.zip.RescanPostProcessZips.DizReadResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RescanPostProcessZipsTest {

    @Test
    void zipExtensionIsCaseInsensitive() {
        assertTrue(RescanPostProcessZips.isZip("a.zip"));
        assertTrue(RescanPostProcessZips.isZip("a.ZIP"));
        assertTrue(RescanPostProcessZips.isZip("a.Zip"));
        assertFalse(RescanPostProcessZips.isZip("a.zip.nfo"));
        assertFalse(RescanPostProcessZips.isZip("a.rar"));
    }

    @Test
    void noZipsMeansNothingToDo() {
        assertEquals(DizAction.NONE, RescanPostProcessZips.decideRefresh(false, false));
        assertEquals(DizAction.NONE, RescanPostProcessZips.decideRefresh(false, true));
    }

    @Test
    void noAvailableSlaveKeepsPreviousDiz() {
        assertEquals(DizAction.KEEP, RescanPostProcessZips.decideRefresh(true, false));
    }

    @Test
    void availableSlaveAllowsRefresh() {
        assertEquals(DizAction.REFRESH, RescanPostProcessZips.decideRefresh(true, true));
    }

    @Test
    void foundDizIsSet() {
        assertEquals(DizAction.SET, RescanPostProcessZips.decideResult(DizReadResult.FOUND));
    }

    @Test
    void missingDizIsDroppedNotRestored() {
        assertEquals(DizAction.DROP, RescanPostProcessZips.decideResult(DizReadResult.NOT_FOUND));
    }

    @Test
    void unavailableSlavesKeepPreviousDiz() {
        assertEquals(DizAction.KEEP, RescanPostProcessZips.decideResult(DizReadResult.UNAVAILABLE));
    }
}
