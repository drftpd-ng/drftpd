package org.drftpd.zipscript.master;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PercentTest {

    @Test
    void zeroTotalGivesZero() {
        assertEquals(0, Percent.of(0, 0));
        assertEquals(0, Percent.of(3, 0));
    }

    @Test
    void negativeTotalGivesZero() {
        assertEquals(0, Percent.of(1, -1));
    }

    @Test
    void roundsDown() {
        assertEquals(33, Percent.of(1, 3));
        assertEquals(50, Percent.of(1, 2));
        assertEquals(100, Percent.of(5, 5));
        assertEquals(0, Percent.of(0, 4));
    }

    @Test
    void largeValuesDoNotOverflow() {
        assertEquals(50, Percent.of(Integer.MAX_VALUE / 2 + 1L, Integer.MAX_VALUE));
    }
}
