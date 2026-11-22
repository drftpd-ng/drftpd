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
package org.drftpd.speedtestnet.common;

import org.drftpd.common.network.AsyncResponse;

/**
 * Slave answer to a "speedTestServers" request: the raw JSON returned by speedtest.net
 * (null if the slave could not fetch it). Parsing is done on the master.
 */
@SuppressWarnings("serial")
public class AsyncResponseSpeedTestServers extends AsyncResponse {
    private final String _json;

    public AsyncResponseSpeedTestServers(String index, String json) {
        super(index);
        _json = json;
    }

    public String getJson() {
        return _json;
    }

    public String toString() {
        return getClass().getName();
    }
}
