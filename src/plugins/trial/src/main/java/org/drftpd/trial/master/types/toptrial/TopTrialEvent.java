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
package org.drftpd.trial.master.types.toptrial;

import org.drftpd.master.usermanager.User;

import java.util.ArrayList;

/**
 * @author CyBeR
 * @version $Id: TopTrialEvent.java 1925 2009-06-15 21:46:05Z tdsoul $
 */
public class TopTrialEvent {

    private final String _name;
    private final ArrayList<User> _users;
    private final int _period;
    private final String _periodstr;
    private final int _keep;
    private final long _min;

    public TopTrialEvent(String name, ArrayList<User> users, int period, int keep, long min) {
        _name = name;
        _users = users;
        _period = period;
        _keep = keep;
        _min = min;

        switch (_period) {
            case 1 -> _periodstr = "MONTHUP";
            case 2 -> _periodstr = "WKUP";
            case 3 -> _periodstr = "DAYUP";
            default -> _periodstr = "UNKNOWN";
        }
    }

    public String getName() {
        return _name;
    }

    public ArrayList<User> getUsers() {
        return _users;
    }

    public int getPeriod() {
        return _period;
    }

    public String getPeriodStr() {
        return _periodstr;
    }

    public int getKeep() {
        return _keep;
    }

    public long getMin() {
        return _min;
    }
}