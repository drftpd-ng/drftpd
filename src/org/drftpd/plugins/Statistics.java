/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * DrFTPD is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with DrFTPD; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package org.drftpd.plugins;

import net.sf.drftpd.event.Event;
import net.sf.drftpd.event.FtpListener;
import net.sf.drftpd.event.TransferEvent;
import net.sf.drftpd.event.UserEvent;

import org.drftpd.dynamicdata.Key;


/**
 * @author mog
 * @version $Id$
 */
public class Statistics extends FtpListener {
    public static final Key LOGINS = new Key(Statistics.class, "logins",
            Integer.class);

    public void actionPerformed(Event event) {
        if (event.getCommand().equals("LOGIN")) {
            UserEvent uevent = (UserEvent) event;
            uevent.getUser().getKeyedMap().incrementObjectInt(LOGINS, 1);
        }

        if (event.getCommand().equals("STOR")) {
            TransferEvent tevent = (TransferEvent) event;

            //tevent.getTime()
        }
    }
}
