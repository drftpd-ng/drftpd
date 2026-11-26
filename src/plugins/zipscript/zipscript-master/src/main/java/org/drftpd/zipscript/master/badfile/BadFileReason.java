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

/**
 * Why a file was flagged as bad. {@link #OTHER} is the extensible one: the event carries a free text
 * detail that is shown in the announce, so other checks (e.g. audio rules) can reuse it.
 */
public enum BadFileReason {
    BAD_CRC("zipscript.badfile.crc"),
    ZERO_BYTE("zipscript.badfile.zero"),
    NOT_IN_SFV("zipscript.badfile.notinsfv"),
    OTHER("zipscript.badfile.other");

    private final String _themeKey;

    BadFileReason(String themeKey) {
        _themeKey = themeKey;
    }

    /**
     * @return the key in badfile.announcer.theme used to format the announce for this reason
     */
    public String getThemeKey() {
        return _themeKey;
    }
}
