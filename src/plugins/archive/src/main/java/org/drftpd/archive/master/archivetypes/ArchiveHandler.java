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
package org.drftpd.archive.master.archivetypes;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.drftpd.archive.master.Archive;
import org.drftpd.archive.master.DuplicateArchiveException;
import org.drftpd.archive.master.event.ArchiveFailedEvent;
import org.drftpd.archive.master.event.ArchiveFinishEvent;
import org.drftpd.archive.master.event.ArchiveStartEvent;
import org.drftpd.jobs.master.Job;
import org.drftpd.master.GlobalContext;
import org.drftpd.master.sections.SectionInterface;
import org.drftpd.master.slavemanagement.RemoteSlave;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;

/**
 * @author CyBeR
 * @version $Id$
 */
public class ArchiveHandler implements Runnable {
    protected static final Logger logger = LogManager.getLogger(ArchiveHandler.class);

    private final ArchiveType _archiveType;

    private ArrayList<Job> _jobs = null;

    public ArchiveHandler(ArchiveType archiveType) {
        _archiveType = archiveType;
        AnnotationProcessor.process(this);
    }

    public ArchiveType getArchiveType() {
        return _archiveType;
    }

    public SectionInterface getSection() {
        return _archiveType.getSection();
    }

    public ArrayList<Job> getJobs() {
        if (_jobs == null) {
            return (ArrayList<Job>) Collections.<Job>emptyList();
        }
        return new ArrayList<>(_jobs);
    }

    /*
     * Thread for ArchiveHandler
     * This will go through and find the oldest non archived dir, then try and archive it
     * It will also loop X amount of times defined in .repeat from .conf file.
     *
     * This also throws events so they can be caught for sitebot announcing.
     */
    public void run() {
        Thread t = Thread.currentThread();
        t.setName("Archive Handler-" + t.getId() + " - " + _archiveType.getClass().getName() + " archiving " + _archiveType.getSection().getName());
        // Prevent spawning more than 1 active threads
        ArrayList<Thread> threadArrayList = getThreadByName(this.getName());
        if (threadArrayList.size() > 1) {
            for (Thread t : threadArrayList) {
                if (t.isAlive())
                    return; // A thread is already running lets skip this cycle
            }
        }

        long curtime = System.currentTimeMillis();
        for (int i = 0; i < _archiveType.getRepeat(); i++) {
            /*
            // We do not care about this if we manually archive it should always do it
            if ((System.currentTimeMillis() - curtime) > _archiveType._parent.getCycleTime()) {
                //don't want to double archive stuff...so we to check and make sure
                return;
            }
            */
            try {
                synchronized (_archiveType._parent) {
                    if (_archiveType.getDirectory() == null) {
                        _archiveType.setDirectory(_archiveType.getOldestNonArchivedDir());
                    }

                    if (_archiveType.getDirectory() == null) {
                        logger.debug("No directory found to archive, nothing left to do.");
                        return;
                    }
                }
                if (!_archiveType.moveReleaseOnly()) {
                    Set<RemoteSlave> destSlaves = _archiveType.findDestinationSlaves();

                    if (destSlaves == null) {
                        _archiveType.setDirectory(null);
                        logger.warn("Unable to allocate destination Slaves, nothing we can do.");
                        return; // no available slaves to use
                    }

                    _jobs = _archiveType.send();
                }

                GlobalContext.getEventService().publish(new ArchiveStartEvent(_archiveType, _jobs));
                long startTime = System.currentTimeMillis();
                if (_jobs != null) {
                    _archiveType.waitForSendOfFiles(_jobs);
                }

                if (!_archiveType.moveRelease(getArchiveType().getDirectory())) {
                    _archiveType.addFailedDir(getArchiveType().getDirectory().getPath());
                    GlobalContext.getEventService().publish(new ArchiveFailedEvent(_archiveType, startTime, "Failed To Move Directory"));
                    logger.error("Failed to Archiving {} (Failed To Move Directory)", getArchiveType().getDirectory().getPath());
                } else {
                    GlobalContext.getEventService().publish(new ArchiveFinishEvent(_archiveType, startTime));
                    logger.info("Done archiving {}", getArchiveType().getDirectory().getPath());
                }
            } catch (Exception e) {
                logger.warn("Caught an unexpected exception while trying to archive", e);
            } finally {
                if (!_archiveType._parent.removeArchiveHandler(this)) {
                    logger.error("We were unable to remove this ArchiveHandler from the registered ArchiveHandlers");
                }
                _archiveType.setDirectory(null);
            }
        }
        // Give the thread a correct name (Waiting for archive)
        t.setName(Archive.ArchiveHandlerThreadFactory.getIdleThreadName(t.getId()));
    }
}
