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
package net.sf.drftpd.event.listeners;

import net.sf.drftpd.FatalException;
import net.sf.drftpd.NoAvailableSlaveException;
import net.sf.drftpd.SFVFile;
import net.sf.drftpd.event.Event;
import net.sf.drftpd.event.FtpListener;
import net.sf.drftpd.event.TransferEvent;
import net.sf.drftpd.master.ConnectionManager;
import net.sf.drftpd.master.UploaderPosition;
import net.sf.drftpd.remotefile.LinkedRemoteFileInterface;

import org.drftpd.plugins.SiteBot;

import org.drftpd.usermanager.NoSuchUserException;
import org.drftpd.usermanager.User;
import org.drftpd.usermanager.UserFileException;

import java.io.FileNotFoundException;
import java.io.IOException;

import java.util.Collection;
import java.util.Iterator;


/**
 * @author zubov
 * @version $Id: RaceStatistics.java,v 1.16 2004/11/03 16:46:38 mog Exp $
 */
public class RaceStatistics implements FtpListener {
    private ConnectionManager _cm;

    public RaceStatistics() {
    }

    public void actionPerformed(Event event) {
        if (!event.getCommand().equals("STOR")) {
            return;
        }

        TransferEvent direvent = (TransferEvent) event;
        LinkedRemoteFileInterface dir;

        try {
            dir = direvent.getDirectory().getParentFile();
        } catch (FileNotFoundException e) {
            throw new FatalException(e);
        }

        SFVFile sfvfile;

        try {
            sfvfile = dir.lookupSFVFile();

            // throws IOException, ObjectNotFoundException, NoAvailableSlaveException
        } catch (FileNotFoundException ex) {
            // can't save stats with no sfv file
            return;
        } catch (NoAvailableSlaveException e) {
            // can't save stats with no sfv file
            return;
        } catch (IOException e) {
            // can't save stats with no sfv file
            return;
        }

        if (!sfvfile.hasFile(direvent.getDirectory().getName())) {
            return;
        }

        //COMPLETE
        if (sfvfile.getStatus().isFinished()) {
            return;
        }

        Collection racers = SiteBot.userSort(sfvfile.getFiles(), "bytes", "high");

        if (racers.size() <= 1) {
            return; // no race
        }

        int count = 1;

        for (Iterator iter = racers.iterator(); iter.hasNext(); count++) {
            UploaderPosition racer = (UploaderPosition) iter.next();
            User user;

            try {
                user = _cm.getGlobalContext().getUserManager().getUserByName(racer.getUsername());
            } catch (NoSuchUserException ex) {
                // this should not happen, but if it does, it means the user was deleted
                // we want to ignore their stats, but the race still did happen
                continue;
            } catch (UserFileException ex) {
                //if ( ex instanceof CorruptUserFileException )
                // don't add stats to badd users
                continue;
            }

            if (count == 1) {
                user.addRacesWon();
            } else if (count == racers.size()) {
                user.addRacesLost();
            } else {
                user.addRacesParticipated();
            }
        }
    }

    /* (non-Javadoc)
     * @see net.sf.drftpd.Initializeable#init(net.sf.drftpd.master.ConnectionManager)
     */
    public void init(ConnectionManager connectionManager) {
        _cm = connectionManager;
    }

    public void unload() {
    }
}
