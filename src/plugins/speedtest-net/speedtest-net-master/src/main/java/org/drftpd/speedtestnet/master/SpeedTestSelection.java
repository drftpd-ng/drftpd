/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * DrFTPD is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with DrFTPD; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package org.drftpd.speedtestnet.master;

/**
 * Decides where the speedtest.net servers for a slave come from.
 */
final class SpeedTestSelection {

    /** Slave property (SITE SLAVE name SET speedtest.selection slave|geo) overriding server.selection. */
    static final String SLAVE_KEY = "speedtest.selection";

    enum Source {
        /** Ask the slave for the servers nearest to it (limited list). */
        SLAVE_NEAREST,
        /** Use the full server list downloaded by the master. */
        FULL_LIST
    }

    private SpeedTestSelection() {
    }

    /** @return true if the global server.selection value means 'slave'; anything else is 'geo'. */
    static boolean isSlaveMode(String globalValue) {
        return globalValue != null && "slave".equalsIgnoreCase(globalValue.trim());
    }

    /**
     * @param globalSlaveMode global server.selection is 'slave'
     * @param slaveSetting    per-slave speedtest.selection property, may be null/empty/unknown (follow global)
     * @param requestedServerId explicit server ID, 0 for none
     */
    static Source decide(boolean globalSlaveMode, String slaveSetting, int requestedServerId) {
        if (requestedServerId != 0) {
            // A specific server may be outside the few nearest ones, only the full list can resolve it
            return Source.FULL_LIST;
        }
        boolean slaveMode = globalSlaveMode;
        if (slaveSetting != null) {
            String v = slaveSetting.trim();
            if (v.equalsIgnoreCase("slave")) {
                slaveMode = true;
            } else if (v.equalsIgnoreCase("geo")) {
                slaveMode = false;
            }
        }
        return slaveMode ? Source.SLAVE_NEAREST : Source.FULL_LIST;
    }
}
