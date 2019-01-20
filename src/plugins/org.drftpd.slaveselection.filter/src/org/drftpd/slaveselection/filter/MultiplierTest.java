package org.drftpd.slaveselection.filter;

import junit.framework.TestCase;
import org.junit.Assert;

public class MultiplierTest extends TestCase {
    public MultiplierTest(String fName) {
        super(fName);
    }

    public void testDivide() {
        Assert.assertEquals(10F, BandwidthFilter.parseMultiplier("/0.1"), 0F);
    }

    public void testDivideMultiplyMultiply() {
        Assert.assertEquals(0.1F, BandwidthFilter.parseMultiplier("/10*10/10"), 0F);
    }

    public void testMultiply() {
        Assert.assertEquals(100F, BandwidthFilter.parseMultiplier("*100"), 0F);
    }

    public void testMultiplyDivide() {
        Assert.assertEquals(1F, BandwidthFilter.parseMultiplier("/10*10"), 0F);
    }

    public void testMultiplyMultiplyDivide() {
        Assert.assertEquals(10F, BandwidthFilter.parseMultiplier("10*10/10"), 0F);
    }

    public void testSimple() {
        Assert.assertEquals(100F, BandwidthFilter.parseMultiplier("100"), 0F);
    }
}
