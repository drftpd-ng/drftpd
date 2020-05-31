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
package org.drftpd.master.permissions.stats;

import org.drftpd.master.permissions.GlobPathPermission;

import java.util.Collection;
import java.util.regex.PatternSyntaxException;

public class CreditLimitPathPermission extends GlobPathPermission {
    private final int _direction;
    private final String _period;
    private final long _bytes;

    /**
     * @param pattern
     * @param direction
     * @param period
     * @param bytes
     * @param users
     * @throws PatternSyntaxException
     */
    public CreditLimitPathPermission(String pattern, int direction, String period, long bytes, Collection<String> users)
            throws PatternSyntaxException {
        super(pattern, users);
        _direction = direction;
        _period = period;
        _bytes = bytes;
    }

    public int getDirection() {
        return _direction;
    }

    public String getPeriod() {
        return _period;
    }

    public long getBytes() {
        return _bytes;
    }
}
