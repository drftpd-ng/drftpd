/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 *
 * DrFTPD is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * DrFTPD; if not, write to the Free Software Foundation, Inc., 59 Temple Place,
 * Suite 330, Boston, MA 02111-1307 USA
 */
package org.drftpd.mirroring.archivetypes;

import net.sf.drftpd.NoAvailableSlaveException;
import net.sf.drftpd.ObjectNotFoundException;
import net.sf.drftpd.mirroring.Job;
import net.sf.drftpd.mirroring.JobManager;

import org.apache.log4j.Logger;

import org.drftpd.PropertyHelper;
import org.drftpd.master.RemoteSlave;
import org.drftpd.mirroring.ArchiveType;
import org.drftpd.plugins.Archive;

import org.drftpd.remotefile.LinkedRemoteFileInterface;
import org.drftpd.sections.SectionInterface;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Properties;

/**
 * @author zubov
 * @version $Id$
 */
public class StripeFilesOffSpecificSlaves extends ArchiveType {
    private static final Logger logger = Logger.getLogger(StripeFilesOffSpecificSlaves.class);
    private HashSet<RemoteSlave> _destSlaves;
    private HashSet<RemoteSlave> _offOfSlaves;
    int _numOfSlaves = 1;

    public StripeFilesOffSpecificSlaves(Archive archive,
        SectionInterface section, Properties props) {
        super(archive, section, props);
        _offOfSlaves = new HashSet<RemoteSlave>();

        for (int i = 1;; i++) {
            String slavename = null;

            try {
                slavename = PropertyHelper.getProperty(props,
                        getSection().getName() + ".offOfSlave." + i);
            } catch (NullPointerException e) {
                break; // done
            }

            try {
                _offOfSlaves.add(_parent.getConnectionManager()
                                        .getGlobalContext().getSlaveManager()
                                        .getRemoteSlave(slavename));
            } catch (ObjectNotFoundException e) {
                logger.debug("Unable to get slave " + slavename +
                    " from the SlaveManager");
            }
        }

        if (_offOfSlaves.isEmpty()) {
            throw new NullPointerException(
                "Cannot continue, 0 slaves found to move off StripeFilesOffSpecificSlave for for section " +
                getSection().getName());
        }

        _numOfSlaves = Integer.parseInt(PropertyHelper.getProperty(props,
                    getSection().getName() + ".numOfSlaves"));

        if (_numOfSlaves < 1) {
            throw new IllegalArgumentException(
                "numOfSlaves has to be > 0 for section " + section.getName());
        }

        _destSlaves = new HashSet<RemoteSlave>();

        for (int i = 1;; i++) {
            String slavename = null;

            try {
                slavename = PropertyHelper.getProperty(props,
                        getSection().getName() + ".slavename." + i);
            } catch (NullPointerException e) {
                break; // done
            }

            try {
                RemoteSlave rslave = _parent.getConnectionManager()
                                            .getGlobalContext().getSlaveManager()
                                            .getRemoteSlave(slavename);

                if (!_offOfSlaves.contains(rslave)) {
                    _destSlaves.add(rslave);
                }
            } catch (ObjectNotFoundException e) {
                logger.debug("Unable to get slave " + slavename +
                    " from the SlaveManager");
            }
        }

        if (_destSlaves.isEmpty()) {
            _destSlaves = null; // used as a flag for dynamic slaves
        } else {
            if (_destSlaves.size() < _numOfSlaves) {
                throw new IllegalStateException(
                    "Cannot continue, numOfSlave cannot be less than the # of slaveName's");
            }
        }
    }

    public void cleanup(ArrayList jobList) {
        for (Iterator iter = jobList.iterator(); iter.hasNext();) {
            Job job = (Job) iter.next();
            job.getFile().deleteOthers(getRSlaves());
        }
    }

    public HashSet<RemoteSlave> findDestinationSlaves() {
        if (_destSlaves != null) {
            return _destSlaves;
        }

        HashSet<RemoteSlave> availableSlaves = new HashSet<RemoteSlave>(_parent.getConnectionManager()
                                                     .getGlobalContext()
                                                     .getSlaveManager()
                                                     .getSlaves());
        availableSlaves.removeAll(_offOfSlaves);

        if (availableSlaves.isEmpty()) {
            return null;
        }

        return availableSlaves;
    }

    protected boolean isArchivedDir(LinkedRemoteFileInterface lrf)
        throws IncompleteDirectoryException, OfflineSlaveException {
        for (Iterator iter = lrf.getFiles().iterator(); iter.hasNext();) {
            LinkedRemoteFileInterface file = (LinkedRemoteFileInterface) iter.next();

            if (file.isDirectory()) {
                if (!isArchivedDir(file)) {
                    return false;
                }
            } else {
                try {
                    for (Iterator iter2 = file.getAvailableSlaves().iterator();
                            iter2.hasNext();) {
                        RemoteSlave rslave = (RemoteSlave) iter2.next();

                        if (_offOfSlaves.contains(rslave)) {
                            return false;
                        }
                    }
                } catch (NoAvailableSlaveException e) {
                    throw new OfflineSlaveException(
                        "There were no available slaves for " + file.getPath());
                }
            }
        }

        return true;
    }

    public ArrayList send() {
        return recursiveSend(getDirectory());
    }

    private ArrayList<Job> recursiveSend(LinkedRemoteFileInterface lrf) {
        ArrayList<Job> jobQueue = new ArrayList<Job>();
        JobManager jm = _parent.getConnectionManager().getJobManager();

        for (Iterator iter = lrf.getFiles().iterator(); iter.hasNext();) {
            LinkedRemoteFileInterface file = (LinkedRemoteFileInterface) iter.next();

            if (file.isDirectory()) {
                jobQueue.addAll(recursiveSend(file));
            } else {
                logger.info("Adding " + file.getPath() +
                    " to the job queue with numOfSlaves = " + _numOfSlaves);

                Job job = new Job(file, getRSlaves(), 3, _numOfSlaves);
                jm.addJobToQueue(job);
                jobQueue.add(job);
            }
        }

        return jobQueue;
    }

    public String toString() {
        return "StripeFilesOffSpecificSlaves=[directory=[" +
        getDirectory().getPath() + "]dest=[" + outputSlaves(getRSlaves()) +
        "]offOfSlaves=[" + outputSlaves(_offOfSlaves) + "]numOfSlaves=[" +
        _numOfSlaves + "]]";
    }
}
