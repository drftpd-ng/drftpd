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
package org.drftpd.mirroring.archivetypes;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Properties;

import net.sf.drftpd.NoAvailableSlaveException;
import net.sf.drftpd.mirroring.Job;
import net.sf.drftpd.mirroring.JobManager;

import org.apache.log4j.Logger;
import org.drftpd.GlobalContext;
import org.drftpd.PropertyHelper;
import org.drftpd.master.RemoteSlave;
import org.drftpd.mirroring.ArchiveType;
import org.drftpd.remotefile.LinkedRemoteFileInterface;
import org.drftpd.sections.SectionInterface;


/**
 * @author zubov
 */
public class ConstantMirroring extends ArchiveType {
    private static final Logger logger = Logger.getLogger(ConstantMirroring.class);
    private int _numOfSlaves;

    public ConstantMirroring(Archive archive, SectionInterface section,
        Properties p) {
        super(archive, section, p);
        _numOfSlaves = Integer.parseInt(PropertyHelper.getProperty(p,
                    section.getName() + ".numOfSlaves"));

        if (_numOfSlaves < 2) {
            throw new IllegalArgumentException(
                "numOfSlaves has to be > 1 for section " + section.getName());
        }
    }

    public void cleanup(ArrayList jobList) {
        recursiveCleanup(getDirectory());
    }

    private void recursiveCleanup(LinkedRemoteFileInterface lrf) {
        for (Iterator iter = new ArrayList(lrf.getFiles()).iterator();
                iter.hasNext();) {
            LinkedRemoteFileInterface src = (LinkedRemoteFileInterface) iter.next();

            if (src.isLink()) {
                continue;
            }

            if (src.isFile()) {
                Collection slaves = new ArrayList(src.getSlaves());

                if (slaves.isEmpty()) {
                    // couldn't mirror file, it's deleted
                    src.delete();

                    continue;
                }

                Iterator offlineSlaveIter = slaves.iterator();

                while ((src.getSlaves().size() > _numOfSlaves) &&
                        offlineSlaveIter.hasNext()) { // remove offline slaves until size is okay

                    RemoteSlave slave = (RemoteSlave) offlineSlaveIter.next();

                    if (!slave.isAvailable()) {
                        src.removeSlave(slave);
                        slave.simpleDelete(src.getPath());
                    }

                    offlineSlaveIter.remove();
                }

                slaves = new ArrayList(src.getSlaves());

                Iterator onlineSlaveIter = slaves.iterator();

                while ((slaves.size() > _numOfSlaves) &&
                        onlineSlaveIter.hasNext()) { // remove online slaves until size is okay

                    RemoteSlave slave = (RemoteSlave) onlineSlaveIter.next();
                    slave.simpleDelete(src.getPath());
                    src.removeSlave(slave);
                    onlineSlaveIter.remove();
                }
            } else { // src.isDirectory()
                recursiveCleanup(src);
            }
        }
    }

    public HashSet<RemoteSlave> findDestinationSlaves() {
        return new HashSet<RemoteSlave>(GlobalContext.getGlobalContext()
                                  .getSlaveManager().getSlaves());
    }

    protected boolean isArchivedDir(LinkedRemoteFileInterface lrf)
        throws IncompleteDirectoryException, OfflineSlaveException {
        for (Iterator iter = lrf.getFiles().iterator(); iter.hasNext();) {
            LinkedRemoteFileInterface src = (LinkedRemoteFileInterface) iter.next();

            if (src.isLink()) {
                continue;
            }

            if (src.isFile()) {
                Collection onlineSlaves;

                try {
                    onlineSlaves = src.getAvailableSlaves();
                } catch (NoAvailableSlaveException e) {
                    continue; // can't archive this file but maybe others have a chance
                }

                if (onlineSlaves.size() != _numOfSlaves) {
                    return false;
                }
            } else if (src.isDirectory()) {
                return isArchivedDir(src);
            }
        }

        return true;
    }

    /**
     * Adds relevant Jobs to the JobManager and returns an ArrayList of those Job's
     */
    public ArrayList send() {
        return recursiveSend(getDirectory());
    }

    private ArrayList recursiveSend(LinkedRemoteFileInterface lrf) {
        ArrayList jobQueue = new ArrayList();
        JobManager jm = GlobalContext.getGlobalContext().getJobManager();

        for (Iterator iter = lrf.getFiles().iterator(); iter.hasNext();) {
            LinkedRemoteFileInterface src = (LinkedRemoteFileInterface) iter.next();

            if (src.isFile()) {
                logger.info("Adding " + src.getPath() + " to the job queue");

                Job job = new Job(src, getRSlaves(), 3, _numOfSlaves);
                jm.addJobToQueue(job);
                jobQueue.add(job);
            } else {
                jobQueue.addAll(recursiveSend(src));
            }
        }

        return jobQueue;
    }

    public String toString() {
        return "ConstantMirroring=[directory=[" + getDirectory().getPath() +
        "]dest=[" + outputSlaves(getRSlaves()) + "]numOfSlaves=[" +
        _numOfSlaves + "]]";
    }
}
