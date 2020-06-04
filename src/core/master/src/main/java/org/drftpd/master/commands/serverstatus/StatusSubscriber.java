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
package org.drftpd.master.commands.serverstatus;

import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.bushe.swing.event.annotation.EventSubscriber;
import org.drftpd.common.dynamicdata.Key;
import org.drftpd.common.dynamicdata.KeyedMap;
import org.drftpd.master.event.SlaveEvent;

public class StatusSubscriber {
    private static StatusSubscriber _subscriber = null;

    private StatusSubscriber() {
        // Subscribe to events
        AnnotationProcessor.process(this);
    }

    /**
     * Checks if this subscriber is already listening to events, otherwise, initialize it.
     */
    public static void checkSubscription() {
        if (_subscriber == null) {
            _subscriber = new StatusSubscriber();
        }
    }

    /**
     * Remove the reference to the current subscriber so that it can be GC'ed.
     */
    private static void nullify() {
        _subscriber = null;
    }

    @EventSubscriber
    public void onSlaveEvent(SlaveEvent event) {
        KeyedMap<Key<?>, Object> keyedMap = event.getRSlave().getTransientKeyedMap();
        if (event.getCommand().equals("ADDSLAVE")) {
            keyedMap.setObject(ServerStatus.CONNECTTIME, System.currentTimeMillis());
        } else if (event.getCommand().equals("DELSLAVE")) {
            keyedMap.remove(ServerStatus.CONNECTTIME);
        }
    }
}
