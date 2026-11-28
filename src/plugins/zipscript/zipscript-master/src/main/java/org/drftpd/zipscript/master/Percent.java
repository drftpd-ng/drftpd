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
package org.drftpd.zipscript.master;

/**
 * Percentage calculation for the race output that does not fail when the total is zero, which is the
 * case for an .sfv without a valid entry or a .diz without a file count.
 */
public final class Percent {

    private Percent() {
    }

    /**
     * @return {@code part * 100 / total} rounded down, or 0 when {@code total} is not positive
     */
    public static int of(long part, long total) {
        if (total <= 0) {
            return 0;
        }
        return (int) ((part * 100L) / total);
    }
}
