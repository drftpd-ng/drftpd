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

import net.sf.drftpd.Checksum;
import net.sf.drftpd.event.Event;
import net.sf.drftpd.event.FtpListener;
import net.sf.drftpd.event.TransferEvent;

import org.drftpd.commands.UserManagment;
import org.drftpd.master.ConnectionManager;

import java.io.FileNotFoundException;
import java.io.IOException;


/**
 * Inspired by glftpd's post_check script.
 *
 * Docs shamelessly stolen from glftpd.docs:
 *
 * The following environment variables can be used by external scripts:
 *<pre>
 * $USER                Username.
 * $TAGLINE        User tagline.
 * $GROUP                User group.
 * $RATIO                User ratio.
 * $SPEED                Speed in K/s. This is exported after every upload/download.
 * $SPEEDBPS        Speed in B/s (DrFTPD specific)
 * $HOST                User's ident@ip (Dr specific: ident not included yet)
 * $SECTION        The name of the section user is currently in
 * </pre>
 *
 * The post_check script receives 3 parameters from glftpd:
 * <ul>
 *<li>$1 - the virtual path name of file uploaded
 *<li>$2 - the virtual directory path the file was uploaded to
 *<li>$3 - the CRC code of that file (if calc_crc was enabled, or 000000 otherwise)
 *<li>$4 - the size of the file (DrFTPD specific)
 *</ul>
 *
 * @author mog
 * @version $Id$
 */
public class Exec implements FtpListener {
    private ConnectionManager _cm;

    public void actionPerformed(Event event) {
        if (!(event instanceof TransferEvent) ||
                !event.getCommand().equals("STOR")) {
            return;
        }

        TransferEvent uevent = (TransferEvent) event;
        String[] env = {
                "USER=" + uevent.getUser().getName(),
                "TAGLINE=" +
                uevent.getUser().getObjectString(UserManagment.TAGLINE),
                "GROUP=" + uevent.getUser().getGroup(),
                "RATIO=" +
                uevent.getUser().getObjectFloat(UserManagment.RATIO),
                "SPEED=" + (uevent.getDirectory().getXferspeed() / 1000),
                "SPEEDBPS=" + (uevent.getDirectory().getXferspeed()),
                "HOST=@" + uevent.getConn().getClientAddress(),
                
                "SECTION=" +
                _cm.getGlobalContext().getSectionManager().lookup(uevent.getDirectory()
                                                                        .getPath())
            };
        String[] cmd;

        try {
            cmd = new String[] {
                    "/path/to/executeable", uevent.getDirectory().getPath(),
                    uevent.getDirectory().getParent(),
                    Checksum.formatChecksum(uevent.getDirectory()
                                                  .getCheckSumCached()),
                    "" + uevent.getDirectory().length()
                };
        } catch (FileNotFoundException e) {
            throw new RuntimeException("No parent dir for file", e);
        }

        try {
            Runtime.getRuntime().exec(cmd, env);
        } catch (IOException e) {
            throw new RuntimeException("Error executing external script", e);
        }
    }

    public void unload() {
    }

    public void init(ConnectionManager connectionManager) {
        _cm = connectionManager;
    }
}
