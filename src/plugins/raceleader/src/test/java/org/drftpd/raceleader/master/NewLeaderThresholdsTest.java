package org.drftpd.raceleader.master;

import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NewLeaderThresholdsTest {

    private static final long MB = 1024L * 1024L;

    @Test
    public void defaultsReproduceOldBehaviour() {
        NewLeaderThresholds t = NewLeaderThresholds.fromProperties(new Properties());
        assertEquals(1800000L, t.getExpireMillis());
        // every change of leader is announced, whoever has more files
        for (int files = 0; files <= 10; files++) {
            for (int prev = 0; prev <= 10; prev++) {
                assertTrue(t.leaderChangeAllowed(files, 0L, prev));
            }
        }
    }

    @Test
    public void expire() {
        Properties p = new Properties();
        p.setProperty("newleader.expire", "10");
        assertEquals(600000L, NewLeaderThresholds.fromProperties(p).getExpireMillis());
        p.setProperty("newleader.expire", "0");
        assertEquals(1800000L, NewLeaderThresholds.fromProperties(p).getExpireMillis());
        p.setProperty("newleader.expire", "x");
        assertEquals(1800000L, NewLeaderThresholds.fromProperties(p).getExpireMillis());
    }

    @Test
    public void filesAhead() {
        Properties p = new Properties();
        p.setProperty("newleader.files.ahead", "2");
        NewLeaderThresholds t = NewLeaderThresholds.fromProperties(p);
        assertFalse(t.leaderChangeAllowed(5, 0L, 5));
        assertFalse(t.leaderChangeAllowed(6, 0L, 5));
        assertTrue(t.leaderChangeAllowed(7, 0L, 5));
        assertTrue(t.leaderChangeAllowed(2, 0L, 0));
    }

    @Test
    public void minSizeInMb() {
        Properties p = new Properties();
        p.setProperty("newleader.min.size", "50");
        NewLeaderThresholds t = NewLeaderThresholds.fromProperties(p);
        assertFalse(t.leaderChangeAllowed(3, 50 * MB - 1, 1));
        assertTrue(t.leaderChangeAllowed(3, 50 * MB, 1));
    }
}
