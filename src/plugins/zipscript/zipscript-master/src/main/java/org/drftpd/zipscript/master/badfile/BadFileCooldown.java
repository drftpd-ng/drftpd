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
package org.drftpd.zipscript.master.badfile;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Remembers when a file was last announced so a client that keeps retrying the same upload does not flood
 * the channel. Memory is bounded: when the table is full the oldest entry is forgotten.
 */
final class BadFileCooldown {

    private static final int MAX_ENTRIES = 1000;

    private final long _cooldownMillis;

    private final Map<String, Long> _last = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Long> eldest) {
            return size() > MAX_ENTRIES;
        }
    };

    BadFileCooldown(long cooldownMillis) {
        _cooldownMillis = cooldownMillis;
    }

    /**
     * @param key one file, e.g. directory path + "/" + file name
     * @param now current time in milliseconds
     * @return true if the file may be announced now (and starts a new cooldown), false while it is cooling down
     */
    synchronized boolean tryAnnounce(String key, long now) {
        Long last = _last.get(key);
        if (last != null && now - last < _cooldownMillis) {
            return false;
        }
        // remove first so the entry moves to the end (newest) of the insertion order
        _last.remove(key);
        _last.put(key, now);
        return true;
    }
}
