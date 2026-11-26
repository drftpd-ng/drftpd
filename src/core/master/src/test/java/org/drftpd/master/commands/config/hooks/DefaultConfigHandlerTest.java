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
package org.drftpd.master.commands.config.hooks;

import org.drftpd.common.dynamicdata.Key;
import org.drftpd.common.dynamicdata.KeyedMap;
import org.drftpd.master.permissions.MessagePathPermission;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.StringTokenizer;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DefaultConfigHandlerTest {

    private static StringTokenizer missingFileDirective() {
        return new StringTokenizer("/* no-such-file-for-test.txt *");
    }

    @Test
    public void missingMessageFileAddsNoList() {
        KeyedMap<Key<?>, Object> map = new KeyedMap<>();
        new DefaultConfigHandler().handleMsgPath(missingFileDirective(), map);
        // the directive is dropped: not even an empty list is created for it
        assertNull(map.getObject(DefaultConfigHandler.MSGPATH, null));
    }

    @Test
    public void missingMessageFileAddsNoNullEntry() {
        KeyedMap<Key<?>, Object> map = new KeyedMap<>();
        ArrayList<MessagePathPermission> list = new ArrayList<>();
        map.setObject(DefaultConfigHandler.MSGPATH, list);
        new DefaultConfigHandler().handleMsgPath(missingFileDirective(), map);
        // an earlier msgpath directive already created the list: nothing, in particular no null, is added to it
        assertSame(list, map.getObject(DefaultConfigHandler.MSGPATH, null));
        assertTrue(list.isEmpty());
    }
}
