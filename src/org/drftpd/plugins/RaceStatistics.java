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

import net.sf.drftpd.FatalException;
import net.sf.drftpd.NoAvailableSlaveException;
import net.sf.drftpd.event.Event;
import net.sf.drftpd.event.FtpListener;
import net.sf.drftpd.event.TransferEvent;
import net.sf.drftpd.master.UploaderPosition;

import org.drftpd.SFVFile;
import org.drftpd.dynamicdata.Key;
import org.drftpd.master.ConnectionManager;
import org.drftpd.remotefile.LinkedRemoteFileInterface;
import org.drftpd.usermanager.NoSuchUserException;
import org.drftpd.usermanager.User;
import org.drftpd.usermanager.UserFileException;

import java.io.FileNotFoundException;
import java.io.IOException;

import java.util.Collection;
import java.util.Iterator;


/**
 * @author zubov
 * @version $Id$
 */
public class RaceStatistics implements FtpListener {
    public static final Key RACESWON = new Key(RaceStatistics.class,
            "racesWon", Integer.class);
    public static final Key RACES = new Key(RaceStatistics.class, "races",
            Integer.class);
    public static final Key RACESLOST = new Key(RaceStatistics.class,
            "racesLost", Integer.class);
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

            // throws IOException, ObjectNotFoundException,
            // NoAvailableSlaveException
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
                // this should not happen, but if it does, it means the user was
                // deleted
                // we want to ignore their stats, but the race still did happen
                continue;
            } catch (UserFileException ex) {
                //if ( ex instanceof CorruptUserFileException )
                // don't add stats to badd users
                continue;
            }

            if (count == 1) {
                //user.addRacesWon();
                user.incrementObjectLong(RACESWON);
            } else if (count == racers.size()) {
                //user.addRacesLost();
                user.incrementObjectLong(RACESLOST);
            } else {
                //user.addRacesParticipated();
                user.incrementObjectLong(RACES);
            }
        }
    }

    public void init(ConnectionManager connectionManager) {
        _cm = connectionManager;
    }

    public void unload() {
    }
}
