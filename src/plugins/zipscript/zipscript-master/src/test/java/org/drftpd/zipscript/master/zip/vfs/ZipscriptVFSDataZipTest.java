package org.drftpd.zipscript.master.zip.vfs;

import org.drftpd.zipscript.common.zip.DizInfo;
import org.drftpd.zipscript.master.zip.vfs.ZipscriptVFSDataZip.DizReadResult.Status;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZipscriptVFSDataZipTest {

    private static DizInfo diz(boolean valid) {
        DizInfo info = new DizInfo();
        info.setValid(valid);
        return info;
    }

    @Test
    void completeZipIsEligibleCaseInsensitive() {
        assertTrue(ZipscriptVFSDataZip.isEligibleZip("a.zip", true, false, 10, 5));
        assertTrue(ZipscriptVFSDataZip.isEligibleZip("a.ZIP", true, false, 10, 5));
    }

    @Test
    void uploadingZipIsNotEligible() {
        assertFalse(ZipscriptVFSDataZip.isEligibleZip("a.zip", true, true, 10, 5));
    }

    @Test
    void unfinishedTransferZipIsNotEligible() {
        assertFalse(ZipscriptVFSDataZip.isEligibleZip("a.zip", true, false, 10, -1));
    }

    @Test
    void emptyNonFileOrNonZipIsNotEligible() {
        assertFalse(ZipscriptVFSDataZip.isEligibleZip("a.zip", true, false, 0, 5));
        assertFalse(ZipscriptVFSDataZip.isEligibleZip("a.zip", false, false, 10, 5));
        assertFalse(ZipscriptVFSDataZip.isEligibleZip("a.rar", true, false, 10, 5));
        assertFalse(ZipscriptVFSDataZip.isEligibleZip("a.zip.nfo", true, false, 10, 5));
    }

    @Test
    void validDizIsFound() {
        assertEquals(Status.FOUND, ZipscriptVFSDataZip.decideStatus(true, diz(true)));
    }

    @Test
    void readButInvalidDizIsNotFound() {
        assertEquals(Status.NOT_FOUND, ZipscriptVFSDataZip.decideStatus(true, diz(false)));
        assertEquals(Status.NOT_FOUND, ZipscriptVFSDataZip.decideStatus(true, null));
    }

    @Test
    void nothingReadIsUnavailable() {
        // all eligible zips unreadable, or no eligible zip at all (e.g. only uploading ones)
        assertEquals(Status.UNAVAILABLE, ZipscriptVFSDataZip.decideStatus(false, null));
    }
}
