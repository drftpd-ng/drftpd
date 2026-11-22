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

import org.drftpd.master.event.ReloadEvent;
import org.drftpd.master.exceptions.FatalException;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

public class SlaveSelectionManagerTest {

    private static final String[] TYPES = {"down", "up", "jobup", "jobdown"};

    private final Map<String, Properties> _configs = new HashMap<>();

    private SlaveSelectionManager newManager() throws Exception {
        for (String type : TYPES) {
            _configs.put("slaveselection-" + type + ".conf", FilterChainTest.props(1));
        }
        return new SlaveSelectionManager(cfg ->
                new FilterChain(cfg, FilterChainTest.filtersMap(), _configs.get(cfg)));
    }

    @Test
    public void testReloadReplacesAllChains() throws Exception {
        SlaveSelectionManager ssm = newManager();
        Map<String, FilterChain> old = new HashMap<>();
        for (String type : TYPES) {
            old.put(type, ssm.getFilterChain(type));
        }
        ssm.onReloadEvent(new ReloadEvent());
        for (String type : TYPES) {
            assertNotSame(old.get(type), ssm.getFilterChain(type), type);
        }
    }

    @Test
    public void testFailingReloadKeepsOldChains() throws Exception {
        SlaveSelectionManager ssm = newManager();
        Map<String, FilterChain> old = new HashMap<>();
        for (String type : TYPES) {
            old.put(type, ssm.getFilterChain(type));
        }

        // down loads fine first, then up fails
        _configs.put("slaveselection-down.conf", FilterChainTest.props(1, 2));
        Properties broken = FilterChainTest.props(1);
        broken.setProperty("2.filter", "nosuchfilter");
        _configs.put("slaveselection-up.conf", broken);

        ssm.onReloadEvent(new ReloadEvent());

        for (String type : TYPES) {
            assertSame(old.get(type), ssm.getFilterChain(type), type + " chain was replaced");
        }
        assertEquals(1, ssm.getFilterChain("down").getFilters().size());
    }

    @Test
    public void testStartupFailureIsFatal() {
        for (String type : TYPES) {
            _configs.put("slaveselection-" + type + ".conf", FilterChainTest.props(1));
        }
        Properties broken = FilterChainTest.props(1);
        broken.setProperty("2.filter", "nosuchfilter");
        _configs.put("slaveselection-jobdown.conf", broken);
        assertThrows(FatalException.class, () -> new SlaveSelectionManager(cfg ->
                new FilterChain(cfg, FilterChainTest.filtersMap(), _configs.get(cfg))));
    }
}
