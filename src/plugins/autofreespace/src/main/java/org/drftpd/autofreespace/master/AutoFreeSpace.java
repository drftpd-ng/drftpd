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
package org.drftpd.autofreespace.master;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.bushe.swing.event.annotation.EventSubscriber;
import org.drftpd.autofreespace.master.event.AFSEvent;
import org.drftpd.common.extensibility.PluginInterface;
import org.drftpd.common.util.Bytes;
import org.drftpd.master.GlobalContext;
import org.drftpd.master.event.ReloadEvent;
import org.drftpd.master.exceptions.NoAvailableSlaveException;
import org.drftpd.master.exceptions.SlaveUnavailableException;
import org.drftpd.master.sections.SectionInterface;
import org.drftpd.master.slavemanagement.RemoteSlave;
import org.drftpd.master.vfs.DirectoryHandle;
import org.drftpd.master.vfs.FileHandle;
import org.drftpd.master.vfs.InodeHandle;

import java.io.FileNotFoundException;
import java.util.*;

/**
 * @author Teflon
 * @author Stevezau
 * @author scitz0
 * @version $Id$
 * adapted to drftpd 3.0.0 by Stevezau
 */

public class AutoFreeSpace implements PluginInterface {
    private static final Logger logger = LogManager.getLogger(AutoFreeSpace.class);
    private Timer _timer;

    public void startPlugin() {
        reload();
        // Subscribe to events
        AnnotationProcessor.process(this);
        logger.info("Autofreespace plugin loaded successfully");
    }

    public void stopPlugin(String reason) {
        AnnotationProcessor.unprocess(this);
        _timer.cancel();
        logger.info("Autofreespace plugin unloaded successfully");
    }

    @EventSubscriber
    public void onReloadEvent(ReloadEvent event) {
        logger.info("Received reload event, reloading");
        reload();
    }

    private void reload() {
        if (_timer != null) {
            logger.info("AUTODELETE: Reloading {}", AutoFreeSpace.class.getName());
            _timer.cancel();
        } else
        {
            logger.info("AUTODELETE: Loading {}", AutoFreeSpace.class.getName());
        }

        AutoFreeSpaceSettings.getSettings().reload();
        if (AutoFreeSpaceSettings.getSettings().getMode().equals(AutoFreeSpaceSettings.MODE_DISABLED)) {
            logger.info("AutoFreeSpace plugin is disabled");
            return;
        }
        _timer = new Timer();
        try {
            _timer.schedule(new MrCleanIt(), AutoFreeSpaceSettings.getSettings().getCycleTime(), AutoFreeSpaceSettings.getSettings().getCycleTime());
        } catch (IllegalStateException e) {
            logger.error("Unable to start AutoFreeSpace timer task, reload and try again");
        }
    }

    private static class MrCleanIt extends TimerTask {
        // This contains a list of all releases that (would) have been deleted.
        // Only useful when option "announce.only" is enabled
        // Also this will grow indefinitely and could potentially be a memory hog
        private List<String> checkedReleases;

        // Keep a boolean to make sure we run only one iteration at a time
        private boolean isActive;

        public MrCleanIt() {
            checkedReleases = new ArrayList<>();
            isActive = false;
        }

        public void run() {
            // Make sure we start with a clean list
            if (isActive) {
                logger.warn("Timer tried to start MrCleanIt, but it seems to be running already");
                return;
            }
            isActive = true;
            checkedReleases = new ArrayList<>();
            logger.info("MrCleanIt task started");
            try {
                int slavesCount = 0;
                for (RemoteSlave remoteSlave : GlobalContext.getGlobalContext().getSlaveManager().getAvailableSlaves()) {
                    if (AutoFreeSpaceSettings.getSettings().getExcludeSlaves().contains(remoteSlave.getName())) {
                        logger.debug("Skipping [{}] as it is excluded", remoteSlave.getName());
                        continue;
                    }

                    try {
                        if (AutoFreeSpaceSettings.getSettings().getMode().equals(AutoFreeSpaceSettings.MODE_DATE)) {
                            cleanByDate(remoteSlave);
                        }
                        if (AutoFreeSpaceSettings.getSettings().getMode().equals(AutoFreeSpaceSettings.MODE_SPACE)) {
                            cleanBySpace(remoteSlave);
                        }
                    } catch (SlaveUnavailableException e) {
                        logger.warn("Slave suddenly went offline");
                    }
                    slavesCount++;
                }
                logger.debug("AUTODELETE: Checked [{}] Slaves for free space", slavesCount);
            } catch (NoAvailableSlaveException nase) {
                logger.warn("AUTODELETE: No slaves online, no point in running the cleaning procedure");
            }
            logger.info("MrCleanIt task finished");
            isActive = false;
        }

        /**
         * Function to delete data on slave purely based on date, with a minimum per section (wipeAfter)
         * @param remoteSlave The slave to check for items to be deleted
         */
        private void cleanByDate(RemoteSlave remoteSlave) throws SlaveUnavailableException {
            int deletedCount = 0;
            int maxIterations = AutoFreeSpaceSettings.getSettings().getMaxIterations();
            try {
                while (deletedCount < maxIterations) {
                    InodeHandle oldestRelease = getOldestRelease(remoteSlave);
                    if (oldestRelease == null) {
                        logger.warn("Could not find oldest release for slave [{}]. Not cleaning", remoteSlave.getName());
                        break;
                    }

                    GlobalContext.getEventService().publishAsync(new AFSEvent(oldestRelease, remoteSlave));
                    if (AutoFreeSpaceSettings.getSettings().getOnlyAnnounce()) {
                        logger.warn("AUTODELETE: (OnlyAnnounce) Would have deleted {}", oldestRelease.getName());
                        checkedReleases.add(oldestRelease.getName());
                    } else {
                        logger.info("AUTODELETE: Removing {}", oldestRelease.getName());
                        oldestRelease.deleteUnchecked(); // Throws the FileNotFoundException
                    }
                    deletedCount++;
                }
            } catch(FileNotFoundException e) {
                logger.error("AUTODELETE: Deleted [{}] releases on slave {} before we ran into an unexpected exception: {}", deletedCount, remoteSlave.getName(), e.getCause());
            }
            if (deletedCount > 0) {
                if (deletedCount >= maxIterations) {
                    logger.warn("AUTODELETE: deleted count [{}] matched maximum iterations [{}], cycleTime or max iterations might need a tweak", deletedCount, maxIterations);
                }
                logger.debug("AUTODELETE: Deleted [{}] releases on slave {}", deletedCount, remoteSlave.getName());
            } else {
                logger.warn("AUTODELETE: Found 0 oldest releases for slave {}", remoteSlave.getName());
            }
        }

        /**
         * Function to delete data on slave purely based on free space
         * @param remoteSlave The slave to check for items to be deleted
         */
        private void cleanBySpace(RemoteSlave remoteSlave) throws SlaveUnavailableException {
            long freespace = remoteSlave.getSlaveStatus().getDiskSpaceAvailable();
            long freespaceMinimum = AutoFreeSpaceSettings.getSettings().getMinFreeSpace();

            if (freespace >= AutoFreeSpaceSettings.getSettings().getMinFreeSpace()) {
                logger.debug("AUTODELETE: Space over limit for slave {} will not clean: {}>={}", remoteSlave.getName(), Bytes.formatBytes(freespace), Bytes.formatBytes(AutoFreeSpaceSettings.getSettings().getMinFreeSpace()));
                return;
            }

            logger.info("AUTODELETE: Space under limit for {}, will clean: {}<{}", remoteSlave.getName(), Bytes.formatBytes(freespace), Bytes.formatBytes(AutoFreeSpaceSettings.getSettings().getMinFreeSpace()));
            GlobalContext.getEventService().publishAsync(new AFSEvent(null, remoteSlave));
            if (AutoFreeSpaceSettings.getSettings().getOnlyAnnounce()) {
                return;
            }

            int deletedCount = 0;
            int maxIterations = AutoFreeSpaceSettings.getSettings().getMaxIterations();
            while (deletedCount < maxIterations) {

                if (freespace <= freespaceMinimum) {
                    logger.info("freespace [{}] reached the desired minimum [{}], stopping iteration", freespace, freespaceMinimum);
                    break;
                }

                long freespaceSaved = freespace;

                try {
                    InodeHandle oldestRelease = getOldestRelease(remoteSlave);
                    if (oldestRelease == null) {
                        logger.debug("AUTODELETE: oldestRelease is null. Stopping iteration");
                        break;
                    }

                    GlobalContext.getEventService().publishAsync(new AFSEvent(oldestRelease, remoteSlave));
                    if (AutoFreeSpaceSettings.getSettings().getOnlyAnnounce()) {
                        logger.warn("AUTODELETE: (OnlyAnnounce) Would have deleted {}", oldestRelease.getName());
                        checkedReleases.add(oldestRelease.getName());
                    } else {
                        logger.info("AUTODELETE: Removing {}", oldestRelease.getName());
                        oldestRelease.deleteUnchecked(); // Throws the FileNotFoundException
                        freespace = remoteSlave.getSlaveStatus().getDiskSpaceAvailable();
                        logger.info("AUTODELETE: Removed {}, cleared {} on {}", oldestRelease.getName(), Bytes.formatBytes(remoteSlave.getSlaveStatus().getDiskSpaceAvailable() - freespace), remoteSlave.getName());
                    }
                    deletedCount++;
                } catch (FileNotFoundException e) {
                    logger.error("AUTODELETE: Deleted [{}] releases on slave {} before we ran into an unexpected exception: {}", deletedCount, remoteSlave.getName(), e.getCause());
                    break;
                }

                if (freespaceSaved == freespace) {
                    if (!AutoFreeSpaceSettings.getSettings().getOnlyAnnounce()) {
                        logger.warn("AUTODELETE: We tried to clean slave {}, but free space has not changed. Stopping iteration", remoteSlave.getName());
                        break;
                    }
                }
            }
            if (deletedCount > 0) {
                if (deletedCount >= maxIterations) {
                    logger.warn("AUTODELETE: deleted count [{}] matched maximum iterations [{}], cycleTime or max iterations might need a tweak", deletedCount, maxIterations);
                }
                logger.debug("AUTODELETE: Deleted [{}] releases on slave {}", deletedCount, remoteSlave.getName());
            } else {
                logger.warn("AUTODELETE: Found 0 oldest releases to clean for slave {}", remoteSlave.getName());
            }
        }

        private boolean checkInvalidName(String name) {
            for (String regex : AutoFreeSpaceSettings.getSettings().getExcludeFiles()) {
                if (name.matches(regex)) {
                    return true;
                }
            }
            return false;
        }

        private InodeHandle getOldestFile(DirectoryHandle dir, RemoteSlave slave) throws FileNotFoundException {

            Collection<InodeHandle> collection = dir.getInodeHandlesUnchecked();

            if (collection.isEmpty()) {
                logger.debug("AUTODELETE: Empty section: {}, skipping", dir.getName());
                return null; //empty section, just ignore
            }

            TreeSet<InodeHandle> sortedCollection = new TreeSet<>(new AgeComparator());
            sortedCollection.addAll(collection);

            for (InodeHandle inode : sortedCollection) {
                if (checkInvalidName(inode.getName())) {
                    continue;
                }
                try {
                    if (gotFilesOn(inode, slave)) {
                        return inode;
                    }
                } catch (NoAvailableSlaveException e) {
                    logger.warn("AUTODELETE: No slave available", e.getCause());
                }
            }
            logger.debug("AUTODELETE: Could not find a valid release to delete in section {}", dir.getName());
            return null;
        }

        private boolean gotFilesOn(InodeHandle inode, RemoteSlave slave)throws NoAvailableSlaveException, FileNotFoundException {

            if (inode.isFile()) {
                return ((FileHandle) inode).getAvailableSlaves().contains(slave);
            } else if (inode.isDirectory()) {
                for (FileHandle file : ((DirectoryHandle) inode).getAllFilesRecursiveUnchecked()) {
                    if (file.getAvailableSlaves().contains(slave)) {
                        return true;
                    }
                }
            }

            return false;
        }

        private InodeHandle getOldestRelease(RemoteSlave slave) {
            InodeHandle oldest = null;

            // Loop over all sections
            for (SectionInterface si : GlobalContext.getGlobalContext().getSectionManager().getSections()) {

                // We are only interested in sections we have a config for
                AutoFreeSpaceSettings.Section section = AutoFreeSpaceSettings.getSettings().getSections().get(si.getName());
                if (section == null) {
                    logger.debug("Skipping section [{}] as no configuration exists", si.getName());
                    continue;
                }

                logger.debug("AUTODELETE: Getting oldest release in section {}", si.getName());
                try {
                    InodeHandle file = getOldestFile(si.getBaseDirectory(), slave);

                    // Quickly skip if this sections does not have anything
                    if (file == null) {
                        continue;
                    }

                    logger.debug("AUTODELETE: Oldest file in section {}: {}", si.getName(), file.getName());

                    long age = System.currentTimeMillis() - file.creationTime();
                    long _wipeAfter = section.getWipeAfter();

                    // (Optionally) set newest oldest if oldest is null or the newly found file is older than oldest already is
                    if (oldest == null || file.creationTime() < oldest.creationTime()) {
                        boolean update = false;
                        if (AutoFreeSpaceSettings.getSettings().getMode().equals(AutoFreeSpaceSettings.MODE_DATE)) {
                            if (age > _wipeAfter) {
                                update = true;
                            }
                        }
                        if (AutoFreeSpaceSettings.getSettings().getMode().equals(AutoFreeSpaceSettings.MODE_SPACE)) {
                            update = true;
                        }
                        if (update) {
                            // Guard for announce.only setting
                            if (!checkedReleases.contains(file.getName())) {
                                if (oldest == null) {
                                    logger.debug("AUTODELETE: Oldest file: {}. Found in section {}", file.getName(), si.getName());
                                } else {
                                    logger.debug("AUTODELETE: New oldest file: {} (previous oldest: {}). Found in section {}", file.getName(), oldest.getName(), si.getName());
                                }
                                oldest = file;
                            }
                        }
                    }
                } catch (FileNotFoundException e) {
                    logger.warn("AUTODELETE: File missing", e.getCause());
                }
            }

            return oldest;
        }

        private static class AgeComparator implements Comparator<InodeHandle> {

            // Compare two InodeHandle.
            public final int compare(InodeHandle a, InodeHandle b) {
                long aLong;
                long bLong;
                try {
                    aLong = a.creationTime();
                    bLong = b.creationTime();
                } catch (FileNotFoundException e) {
                    logger.warn("AUTODELETE: File missing when comparing age", e.getCause());
                    return 0;
                }
                int result = Long.compare(aLong, bLong);
                if (result == 0) {
                    return a.getName().compareTo(b.getName());
                }

                return result;
            }
        }
    }
}
