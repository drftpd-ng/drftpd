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
package org.drftpd.master.event;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.drftpd.master.usermanager.User;

/**
 * Dispatched for LOGIN, LOGOUT and RELOAD.
 * <p>
 * Subclassed for events that are paired with a user object.
 *
 * @author mog
 * @version $Id$
 */
public class ConnectionEvent extends Event {

    @JsonIgnore
    private final transient User _user;

    public ConnectionEvent(User user, String command) {
        this(user, command, System.currentTimeMillis());
    }

    public ConnectionEvent(User user, String command, long time) {
        super(command, time);
        _user = user;
    }

    public User getUser() {
        return _user;
    }

    public String toString() {
        return getClass().getName() + "[user=" + getUser() + ",cmd="
                + getCommand() + "]";
    }
}
