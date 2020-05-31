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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


/**
 * @author mog
 * @version $Id$
 */
public class Event {
    protected static final Logger logger = LogManager.getLogger(Event.class);
    private String _command;
    private long _time;

    public Event(String command) {
        super();
        _command = command;
    }

    public Event(String command, long time) {
        this(command);
        _time = time;
    }

    public String getCommand() {
        return _command;
    }

    public void setCommand(String command) {
        _command = command;
    }

    public long getTime() {
        return _time;
    }
}
