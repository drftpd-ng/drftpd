package org.drftpd.master.slaveselection.filter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MultiplierTest {

    @Test
    public void testDivide() {
        assertEquals(10F, BandwidthFilter.parseMultiplier("/0.1"));
    }

    @Test
    public void testDivideMultiplyMultiply() {
        assertEquals(0.1F, BandwidthFilter.parseMultiplier("/10*10/10"));
    }

    @Test
    public void testMultiply() {
        assertEquals(100F, BandwidthFilter.parseMultiplier("*100"));
    }

    @Test
    public void testMultiplyDivide() {
        assertEquals(1F, BandwidthFilter.parseMultiplier("/10*10"));
    }

    @Test
    public void testMultiplyMultiplyDivide() {
        assertEquals(10F, BandwidthFilter.parseMultiplier("10*10/10"));
    }

    @Test
    public void testSimple() {
        assertEquals(100F, BandwidthFilter.parseMultiplier("100"));
    }
}
