package org.drftpd.slaveselection.filter;

import junit.framework.TestCase;

public class MultiplierTest extends TestCase {
    public MultiplierTest(String fName) {
        super(fName);
    }

    public void testDivide() {
        assertEquals(10F, BandwidthFilter.parseMultiplier("/0.1"), 0F);
    }

    public void testDivideMultiplyMultiply() {
        assertEquals(0.1F, BandwidthFilter.parseMultiplier("/10*10/10"), 0F);
    }

    public void testMultiply() {
        assertEquals(100F, BandwidthFilter.parseMultiplier("*100"), 0F);
    }

    public void testMultiplyDivide() {
        assertEquals(1F, BandwidthFilter.parseMultiplier("/10*10"), 0F);
    }

    public void testMultiplyMultiplyDivide() {
        assertEquals(10F, BandwidthFilter.parseMultiplier("10*10/10"), 0F);
    }

    public void testSimple() {
        assertEquals(100F, BandwidthFilter.parseMultiplier("100"), 0F);
    }
}
