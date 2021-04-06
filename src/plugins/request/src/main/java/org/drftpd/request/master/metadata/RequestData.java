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

import org.drftpd.common.dynamicdata.Key;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class RequestData implements Serializable {

    public static final Key<org.drftpd.request.master.metadata.RequestData> REQUESTS = new Key<>(org.drftpd.request.master.metadata.RequestData.class, "requests");

    private List<RequestEntry> _requests;

    public RequestData() {
        _requests = new ArrayList<>();
    }

    public List<RequestEntry> getRequests() {
        return _requests;
    }

    public void setRequests(List<RequestEntry> requests) {
        _requests = requests;
    }

    public void addRequest(RequestEntry request) {
        if (_requests == null) {
            _requests = new ArrayList<>();
        }
        _requests.add(request);
    }

    public void delRequest(RequestEntry request) {
        _requests.remove(request);
    }

    public String toString() {
        if (_requests == null) {
            return "";
        }

        StringBuilder output = new StringBuilder();
        int cnt = 1;
        for (RequestEntry request : _requests) {
            output.append("Request #").append(cnt++).append(" - ").append(request.getName()).append("\n");
        }
        return output.toString();
    }
}
