package org.drftpd.zipscript.master;

import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AnnounceThresholdsTest {

    private static final long MB = 1024L * 1024L;

    /** The decision as SFVAnnouncer made it before #582. */
    private static boolean oldHalfway(int size, int missing) {
        int halfway = (int) Math.floor((double) size / 2);
        return size >= 4 && missing <= halfway;
    }

    private static boolean newHalfway(AnnounceThresholds t, int size, int missing) {
        return missing <= AnnounceThresholds.halfwayMissing(size)
                && t.halfwayAllowed(size, 0L, () -> 1);
    }

    @Test
    public void defaultsReproduceOldHalfway() {
        AnnounceThresholds t = AnnounceThresholds.fromProperties(new Properties());
        for (int size = 1; size <= 10; size++) {
            assertEquals((int) Math.floor((double) size / 2), AnnounceThresholds.halfwayMissing(size));
            for (int missing = 0; missing <= size; missing++) {
                assertEquals(oldHalfway(size, missing), newHalfway(t, size, missing), size + "/" + missing);
            }
        }
    }

    @Test
    public void halfwayMinFiles() {
        Properties p = new Properties();
        p.setProperty("halfway.min.files", "2");
        AnnounceThresholds t = AnnounceThresholds.fromProperties(p);
        assertFalse(t.halfwayAllowed(1, 0L, () -> 1));
        assertTrue(t.halfwayAllowed(2, 0L, () -> 1));
        assertTrue(t.halfwayAllowed(3, 0L, () -> 1));

        p.setProperty("halfway.min.files", "8");
        t = AnnounceThresholds.fromProperties(p);
        assertFalse(t.halfwayAllowed(7, 0L, () -> 1));
        assertTrue(t.halfwayAllowed(8, 0L, () -> 1));
    }

    @Test
    public void halfwayMinSizeInMb() {
        Properties p = new Properties();
        p.setProperty("halfway.min.size", "100");
        AnnounceThresholds t = AnnounceThresholds.fromProperties(p);
        assertFalse(t.halfwayAllowed(10, 100 * MB - 1, () -> 1));
        assertTrue(t.halfwayAllowed(10, 100 * MB, () -> 1));
        // 0 = off
        assertTrue(AnnounceThresholds.defaults().halfwayAllowed(10, 0L, () -> 1));
    }

    @Test
    public void announceNoRace() {
        Properties p = new Properties();
        p.setProperty("announce.norace", "false");
        AnnounceThresholds t = AnnounceThresholds.fromProperties(p);
        assertFalse(t.halfwayAllowed(10, 0L, () -> 1));
        assertTrue(t.halfwayAllowed(10, 0L, () -> 2));
        assertFalse(t.completeAllowed(() -> 1));
        assertTrue(t.completeAllowed(() -> 2));

        // default: single uploader still announced, racers not even counted
        AnnounceThresholds d = AnnounceThresholds.defaults();
        assertTrue(d.completeAllowed(() -> {
            throw new AssertionError("racers must not be evaluated");
        }));
    }

    @Test
    public void invalidValuesFallBackToDefaults() {
        Properties p = new Properties();
        p.setProperty("halfway.min.files", "abc");
        p.setProperty("halfway.min.size", "-5");
        AnnounceThresholds t = AnnounceThresholds.fromProperties(p);
        assertFalse(t.halfwayAllowed(3, 0L, () -> 1));
        assertTrue(t.halfwayAllowed(4, 0L, () -> 1));
    }
}
