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
package org.drftpd.prebw.master;

import java.util.LinkedList;

/**
 * @author lh
 */
public class PreInfos {
    private static PreInfos ref;
    private final LinkedList<PreInfo> _preInfos;

    private PreInfos() {
        _preInfos = new LinkedList<>();
    }

    public static synchronized PreInfos getPreInfosSingleton() {
        if (ref == null)
            // it's ok, we can call this constructor
            ref = new PreInfos();
        return ref;
    }

    public LinkedList<PreInfo> getPreInfos() {
        return _preInfos;
    }

    public void clearPreInfos() {
        _preInfos.clear();
    }
}
