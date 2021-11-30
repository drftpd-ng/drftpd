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

import java.io.Serializable;

public class RequestEntry implements Serializable {
    private String _user;
    private String _name;
    private String _prefix;
    private long _creationTime;

    @SuppressWarnings("unused")
    public RequestEntry() {}

    public RequestEntry(String name, String user, String prefix, long creationTime) {
        _name = name;
        _user = user;
        _prefix = prefix;
        _creationTime = creationTime;
    }

    public void setUser(String user) {
        _user = user;
    }

    public String getUser() {
        return _user;
    }

    public void setPrefix(String prefix) {
        _prefix = prefix;
    }

    public String getPrefix() {
        return _prefix;
    }

    public void setName(String name) {
        _name = name;
    }

    public String getName() {
        return _name;
    }

    public void setCreationTime(long creationTime) {
        _creationTime = creationTime;
    }

    public long getCreationTime() {
        return _creationTime;
    }

    public String getDirectoryName() {
        return getPrefix() + getUser() + "-" + getName();
    }

    public String getFilledDirectoryName(String prefix) {
        return prefix + getUser() + "-" + getName();
    }
}
