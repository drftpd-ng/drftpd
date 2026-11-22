/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * DrFTPD is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with DrFTPD; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.drftpd.master.slaveselection.filter;

import org.drftpd.common.misc.CaseInsensitiveHashMap;
import org.drftpd.common.vfs.InodeHandleInterface;
import org.drftpd.master.exceptions.FatalException;
import org.drftpd.master.slavemanagement.RemoteSlave;
import org.drftpd.master.usermanager.User;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

public class FilterChainTest {

    /**
     * Does nothing, only remembers the number it was configured with.
     */
    public static class RecordingFilter extends Filter {
        final int _number;

        public RecordingFilter(int i, Properties p) {
            super(i, p);
            _number = i;
        }

        public void process(ScoreChart scorechart, User user, InetAddress peer, char direction,
                            InodeHandleInterface inode, RemoteSlave sourceSlave) {
        }
    }

    static CaseInsensitiveHashMap<String, Class<? extends Filter>> filtersMap() {
        CaseInsensitiveHashMap<String, Class<? extends Filter>> map = new CaseInsensitiveHashMap<>();
        map.put("recording", RecordingFilter.class);
        return map;
    }

    static Properties props(int... numbers) {
        Properties p = new Properties();
        for (int i : numbers) {
            p.setProperty(i + ".filter", "recording");
        }
        return p;
    }

    private static List<Integer> loadedNumbers(FilterChain fc) {
        List<Integer> numbers = new ArrayList<>();
        for (Filter f : fc.getFilters()) {
            numbers.add(((RecordingFilter) f)._number);
        }
        return numbers;
    }

    @Test
    public void testConsecutive() {
        FilterChain fc = new FilterChain("test.conf", filtersMap(), props(1, 2, 3));
        assertEquals(Arrays.asList(1, 2, 3), loadedNumbers(fc));
        assertTrue(FilterChain.isConsecutive(FilterChain.getFilterNumbers(props(1, 2, 3))));
    }

    @Test
    public void testGapIsLoadedWithWarning() {
        Properties p = props(1, 2, 4);
        FilterChain fc = new FilterChain("test.conf", filtersMap(), p);
        assertEquals(Arrays.asList(1, 2, 4), loadedNumbers(fc));
        // a non consecutive numbering is what triggers the warning
        assertFalse(FilterChain.isConsecutive(FilterChain.getFilterNumbers(p)));
    }

    @Test
    public void testOutOfOrderLoadsInNumericOrder() {
        Properties p = new Properties();
        p.setProperty("10.filter", "recording");
        p.setProperty("2.filter", "recording");
        p.setProperty("1.filter", "recording");
        p.setProperty("2.assign", "not a filter key");
        p.setProperty("x.filter", "recording");
        FilterChain fc = new FilterChain("test.conf", filtersMap(), p);
        assertEquals(Arrays.asList(1, 2, 10), loadedNumbers(fc));
    }

    @Test
    public void testLeadingZeroIsIgnored() {
        Properties p = props(1);
        p.setProperty("02.filter", "recording");
        FilterChain fc = new FilterChain("test.conf", filtersMap(), p);
        assertEquals(Arrays.asList(1), loadedNumbers(fc));
    }

    @Test
    public void testEmpty() {
        FilterChain fc = new FilterChain("test.conf", filtersMap(), new Properties());
        assertTrue(fc.getFilters().isEmpty());
    }

    @Test
    public void testUnknownFilterIsFatal() {
        Properties p = props(1);
        p.setProperty("2.filter", "nosuchfilter");
        assertThrows(FatalException.class, () -> new FilterChain("test.conf", filtersMap(), p));
    }
}
