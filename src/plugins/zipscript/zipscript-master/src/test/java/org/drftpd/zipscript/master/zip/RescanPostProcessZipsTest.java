package org.drftpd.zipscript.master.zip;

import org.drftpd.zipscript.master.zip.RescanPostProcessZips.DizAction;
import org.drftpd.zipscript.master.zip.vfs.ZipscriptVFSDataZip.DizReadResult.Status;
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
    void foundDizIsSet() {
        assertEquals(DizAction.SET, RescanPostProcessZips.decideResult(Status.FOUND));
    }

    @Test
    void missingDizIsDroppedNotRestored() {
        assertEquals(DizAction.DROP, RescanPostProcessZips.decideResult(Status.NOT_FOUND));
    }

    @Test
    void unavailableSlavesKeepPreviousDiz() {
        assertEquals(DizAction.KEEP, RescanPostProcessZips.decideResult(Status.UNAVAILABLE));
    }
}
