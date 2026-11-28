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
package org.drftpd.request.master.metadata;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RequestDataTest {

    private static RequestData sample() {
        RequestData data = new RequestData();
        data.addRequest(new RequestEntry("a", "alice", "REQUEST-by.", 1L));
        data.addRequest(new RequestEntry("b", "Bob", "REQUEST-by.", 2L));
        data.addRequest(new RequestEntry("c", "alice", "REQUEST-by.", 3L));
        return data;
    }

    @Test
    public void snapshotCanBeIteratedWhileRemoving() {
        RequestData data = sample();
        for (RequestEntry entry : data.getRequestsSnapshot()) {
            data.delRequest(entry);
        }
        assertEquals(0, data.getRequests().size());
    }

    @Test
    public void snapshotOfEmptyData() {
        RequestData data = new RequestData();
        data.setRequests(null);
        assertEquals(0, data.getRequestsSnapshot().size());
    }
}
