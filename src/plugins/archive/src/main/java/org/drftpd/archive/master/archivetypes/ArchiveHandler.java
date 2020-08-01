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

import java.util.*;

/**
 * @author CyBeR
 * @version $Id$
 */
public class ArchiveHandler implements Runnable {

    protected static final Logger logger = LogManager.getLogger(ArchiveHandler.class);

    private final ArchiveType _archiveType;

    private ArrayList<Job> _jobs;

    private final UUID _uuid;

    public ArchiveHandler(ArchiveType archiveType) {
        _archiveType = archiveType;
        _jobs = new ArrayList<>();
        _uuid = UUID.randomUUID();
        AnnotationProcessor.process(this);
    }

    public ArchiveType getArchiveType() {
        return _archiveType;
    }

    public SectionInterface getSection() {
        return _archiveType.getSection();
    }

    public List<Job> getJobs() {
        return Collections.unmodifiableList(_jobs);
    }

    public UUID getUUID() {
        return _uuid;
    }

    public boolean hasActiveThreadForArchiveTypeAndSection() {
        Archive archive = _archiveType.getParent();
        if (archive == null) {
            logger.error("Parent for archivetype is null, this should not happen");
            return true;
        }
        for (ArchiveHandler ah : archive.getArchiveHandlers()) {
            if (!_archiveType.isManual())
            {
                if (_archiveType.getConfNum() == ah.getArchiveType().getConfNum()) {
                    if (ah.getJobs().size() > 0) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /*
     * Thread for ArchiveHandler
     * This will go through and find the oldest non archived dir, then try and archive it
     * It will also loop X amount of times defined in .repeat from .conf file.
     *
     * This also throws events so they can be caught for sitebot announcing.
     */
    public void run() {
        // Set the name for this thread
        Thread t = Thread.currentThread();
        t.setName("Archive Handler-" + t.getId() + " - " + _archiveType.getClass().getName() + " archiving " + _archiveType.getSection().getName());

        // Check to make sure that we are the only timer based runner for this archive type
        if (hasActiveThreadForArchiveTypeAndSection()) {
            logger.warn("Another timer based ArchiveHandler already exists and is active, so this one is duplicate and not running it");
            if (_archiveType._parent.removeArchiveHandler(this) == null) {
                logger.error("We were unable to remove this ArchiveHandler from the registered ArchiveHandlers");
            }
            t.setName(Archive.ArchiveHandlerThreadFactory.getIdleThreadName(t.getId()));
            return;
        }
        logger.debug("Starting this ArchiveHandler");

        // Loop as often as Repeat demands
        for (int i = 0; i < _archiveType.getRepeat(); i++) {
            try {
                synchronized (_archiveType._parent) {
                    if (_archiveType.getDirectory() == null) {
                        _archiveType.setDirectory(_archiveType.getOldestNonArchivedDir());
                    }

                    if (_archiveType.getDirectory() == null) {
                        logger.debug("No directory found to archive, nothing left to do.");
                        // Do a break here (no return) as that stops the finally from running (ie: deleting the archive handler)
                        break;
                    }
                    try {
                        // Ensure we are not already archiving this request
                        _archiveType._parent.checkPathForArchiveStatus(_archiveType.getDirectory().getPath());
                    } catch (DuplicateArchiveException e) {
                        logger.warn("Directory -- {} -- is already being archived ", _archiveType.getDirectory());
                        // Do a break here (no return) as that stops the finally from running (ie: deleting the archive handler)
                        break;
                    }
                }
                if (!_archiveType.moveReleaseOnly()) {
                    Set<RemoteSlave> destSlaves = _archiveType.findDestinationSlaves();

                    if (destSlaves == null) {
                        _archiveType.setDirectory(null);
                        logger.warn("Unable to allocate destination Slaves, nothing we can do.");
                        break; // no available slaves to use
                    }

                    _jobs = _archiveType.send();
                }

                GlobalContext.getEventService().publish(new ArchiveStartEvent(_archiveType, _jobs));
                long startTime = System.currentTimeMillis();
                if (_jobs.size() > 0) {
                    // This forces the thread to sleep if not all jobs are finished
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
                if (_archiveType._parent.removeArchiveHandler(this) == null) {
                    logger.error("We were unable to remove this ArchiveHandler from the registered ArchiveHandlers");
                }
                _archiveType.setDirectory(null);
            }
        }
        logger.debug("Finished this ArchiveHandler");
        // Give the thread a correct name (Waiting for archive)
        t.setName(Archive.ArchiveHandlerThreadFactory.getIdleThreadName(t.getId()));
    }
}
