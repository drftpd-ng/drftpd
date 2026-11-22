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
package org.drftpd.master.commands.misc;

import org.drftpd.master.commands.CommandResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MiscOptsTest {

    private static void check(int code, String arg) {
        CommandResponse r = Misc.optsResponse(arg);
        assertEquals(code, r.getCode(), "OPTS " + arg);
    }

    @Test
    public void utf8On() {
        check(200, "UTF8 ON");
        check(200, "utf8 on");
        check(200, "UTF8\tON");
        check(200, "  UTF8   On ");
    }

    @Test
    public void utf8Off() {
        check(504, "UTF8 OFF");
    }

    @Test
    public void invalid() {
        check(501, null);
        check(501, "");
        check(501, "   ");
        check(501, "UTF8");
        check(501, "UTF8 MAYBE");
        check(501, "UTF8 ON EXTRA");
        check(501, "MLST type;size;");
    }
}
