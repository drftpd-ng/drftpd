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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import org.apache.log4j.Logger;
import org.drftpd.commands.TransferStatistics;

import net.sf.drftpd.FatalException;
import net.sf.drftpd.event.Event;
import net.sf.drftpd.event.FtpListener;
import net.sf.drftpd.event.TransferEvent;


/**
 * @see http://www.wu-ftpd.org/man/xferlog.html
 * @author mog
 * @version $Id$
 */
public class XferLog extends FtpListener {
    /**
     * xferlog.log - Contains all the upload/download information for all files
              transferred (if logging of that is enabled). The format is the
              following: current time, transfer time, user's hostname, number
              of bytes sent, filename, 'a' if transfer was in ASCII mode or
              'b' if BINARY, _ (meaningless), 'i' if incoming (user uploading)
              or 'o' if outgoing (user downloading), 'r' (no meaning), user's
              name, user's group, 1 if user had ident or 0 if not, user's ident

                  current-time   transfer-time   remote-host    file-
              size    filename    transfer-type   special-action-
              flag   direction    access-mode    username    ser?
              vice-name    authentication-method   authenticated-
              user-id   completion-status

    example lines:
    Mon Aug 11 14:03:30 2003 20 hostname 15000000 /path/to/file b _ i r user group 0 *
    Mon Aug 11 14:03:31 2003 33 hostname 15000000 /path/to/file b _ i r user group 1 user
    Mon Aug 11 14:03:44 2003 13 hostname 15000000 /path/to/file b _ i r user group 0 *
     */
    public static SimpleDateFormat DATE_FMT = new SimpleDateFormat("EEE MMM d HH:mm:ss yyyy",
            Locale.ENGLISH);
    private PrintStream _out;
    private static final Logger logger = Logger.getLogger(XferLog.class);

    public XferLog() {
        super();
        new File("logs").mkdirs();

        try {
            //APPEND
            _out = new PrintStream(new FileOutputStream("logs/xferlog", true));
        } catch (IOException e) {
            throw new FatalException(e);
        }
    }

    public void actionPerformed(Event event) {
        if (event instanceof TransferEvent) {
            actionPerformed((TransferEvent) event);
        }
    }

    public void actionPerformed(TransferEvent event) {
        char direction;

        if (event.getCommand().equals("STOR")) {
            direction = 'i';
        } else if (event.getCommand().equals("RETR")) {
            direction = 'o';
        } else {
            return;
        }

        char transferType;

        if (event.getType() == 'I') { // IMAGE
            transferType = 'b';
        } else if (event.getType() == 'A') { // ASCII
            transferType = 'a';
        } else {
            throw new FatalException("Invalid transfer type");
        }

        //char completed = event.isComplete() ? 'c' : 'i';
        // all transfers are noted as complete
        char completed = 'c';
        try {
			_out.println(DATE_FMT.format(new Date(event.getTime())) + " " +
			    (event.getTransferFile().getXfertime() / 1000) + " " +
			    event.getPeer().getHostName() + " " +
			    event.getTransferFile().getSize() + " " +
			    event.getTransferFile().getPath() + " " + transferType + " _ " +
			    direction + " r " + event.getUser().getName() + " " +
			    event.getUser().getGroup() +
			    " 0 * " // authentication-method   authenticated-user-id
			     +completed);
		} catch (FileNotFoundException e) {
			logger.error("File " + event.getTransferFile().getPath() + " is unable to be found immediately after transfer");
		}
    }
}
