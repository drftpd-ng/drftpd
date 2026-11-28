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

import org.drftpd.master.cron.SafeTimerTask;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.bushe.swing.event.annotation.EventSubscriber;
import org.drftpd.autofreespace.master.event.AFSEvent;
import org.drftpd.common.extensibility.PluginInterface;
import org.drftpd.common.util.Bytes;
import org.drftpd.master.GlobalContext;
import org.drftpd.master.event.MessageEvent;
import org.drftpd.master.event.ReloadEvent;
import org.drftpd.master.exceptions.NoAvailableSlaveException;
import org.drftpd.master.exceptions.SlaveUnavailableException;
import org.drftpd.master.sections.SectionInterface;
import org.drftpd.master.slavemanagement.RemoteSlave;
import org.drftpd.master.vfs.DirectoryHandle;
import org.drftpd.master.vfs.FileHandle;
import org.drftpd.master.vfs.InodeHandle;
import org.drftpd.master.vfs.ObjectNotValidException;

import java.io.FileNotFoundException;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * @author Teflon
 * @author Stevezau
 * @author scitz0
 * @version $Id$
 * adapted to drftpd 3.0.0 by Stevezau
 */

public class AutoFreeSpace implements PluginInterface {
    private static final Logger logger = LogManager.getLogger(AutoFreeSpace.class);
    private Timer _timer = new Timer();
    private MrCleanIt _cleanTask = new MrCleanIt();

    public void startPlugin() {
        reload();
        // Subscribe to events
        AnnotationProcessor.process(this);
        logger.info("Autofreespace plugin loaded successfully");
    }

    public void stopPlugin(String reason) {
        AnnotationProcessor.unprocess(this);
        _cleanTask.stop();
        _timer.cancel();
        logger.info("Autofreespace plugin unloaded successfully");
    }

    @EventSubscriber
    public void onReloadEvent(ReloadEvent event) {
        logger.info("Received reload event, reloading");
        reload();
    }

    @EventSubscriber
    public void onMessageEvent(MessageEvent event) {
        // Only interested in the shutdown event
        if (Objects.equals(event.getCommand(), "SHUTDOWN")) {
            logger.info("Received shutdown event, shutting down AutoFreeSpace timer and task");
            _cleanTask.stop();
            _timer.cancel();
        }
    }

    private void reload() {
        if (_timer != null) {
            logger.info("AUTODELETE: Reloading {}", AutoFreeSpace.class.getName());
            _cleanTask.stop();
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
        _cleanTask = new MrCleanIt();
        try {
            _timer.schedule(_cleanTask, AutoFreeSpaceSettings.getSettings().getCycleTime(), AutoFreeSpaceSettings.getSettings().getCycleTime());
        } catch (IllegalStateException e) {
            logger.error("Unable to start AutoFreeSpace timer task, reload and try again");
        }
    }

    /**
     * Expands a path such as {@code /MP3/*} into every existing directory it matches.
     * A {@code *} component matches all subdirectories at that level; any other component must exist exactly.
     *
     * @param children returns the subdirectories of a directory (empty if none)
     * @param child    returns the named subdirectory, or {@code null} if it does not exist
     */
    static <T> List<T> expandPath(String path, T root, Function<T, Collection<T>> children, BiFunction<T, String, T> child) {
        List<T> current = Collections.singletonList(root);
        for (String part : path.split("/")) {
            if (part.isEmpty()) {
                continue;
            }
            List<T> next = new ArrayList<>();
            for (T dir : current) {
                if (part.equals("*")) {
                    next.addAll(children.apply(dir));
                } else {
                    T found = child.apply(dir, part);
                    if (found != null) {
                        next.add(found);
                    }
                }
            }
            if (next.isEmpty()) {
                return Collections.emptyList();
            }
            current = next;
        }
        return current;
    }

    /**
     * Drops the root directory from the candidate base directories: its children are the top level directories
     * (whole sections, PRE, group dirs), never releases. This covers a section whose base directory is the root.
     */
    static <T> List<T> withoutRoot(List<T> dirs, Predicate<T> isRoot) {
        List<T> result = new ArrayList<>(dirs.size());
        for (T dir : dirs) {
            if (isRoot.test(dir)) {
                logger.warn("AUTODELETE: Skipping the root directory, it can't be used as a base directory");
            } else {
                result.add(dir);
            }
        }
        return result;
    }

    private static class MrCleanIt extends SafeTimerTask {
        // This contains a list of all releases that (would) have been deleted.
        // Only useful when option "announce.only" is enabled
        // Also this will grow indefinitely and could potentially be a memory hog
        private List<String> checkedReleases;

        // Keep a boolean to make sure we run only one iteration at a time
        private boolean isActive;

        private boolean isRunning;

        public MrCleanIt() {
            checkedReleases = new ArrayList<>();
            isActive = false;
            isRunning = true;
        }

        public void stop() {
            isRunning = false;
        }

        protected void safeRun() {
            // Make sure we start with a clean list
            if (!isRunning) {
                logger.error("Invoked task but we should not be running, stopping");
                return;
            }
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
                    if (!isRunning) {
                        logger.info("Stopping loop as we should not be running");
                        break;
                    }
                    if (AutoFreeSpaceSettings.getSettings().getExcludeSlaves().contains(remoteSlave.getName())) {
                        logger.debug("Skipping [{}] as it is excluded", remoteSlave.getName());
                        continue;
                    }
                    // With partial.remerge.mode=instant a slave is available while still remerging,
                    // and its files are not all in the VFS yet: skip it until the remerge finishes.
                    if (remoteSlave.isRemerging()) {
                        logger.debug("Skipping [{}] as it is currently remerging", remoteSlave.getName());
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
                while (isRunning && deletedCount < maxIterations) {
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

            if (freespace > freespaceMinimum) {
                logger.debug("AUTODELETE: Space over limit for slave {}, will not clean: {}>={}", remoteSlave.getName(), Bytes.formatBytes(freespace), Bytes.formatBytes(AutoFreeSpaceSettings.getSettings().getMinFreeSpace()));
                return;
            }

            logger.info("AUTODELETE: Space under limit for slave {}, will clean: {}<{}", remoteSlave.getName(), Bytes.formatBytes(freespace), Bytes.formatBytes(AutoFreeSpaceSettings.getSettings().getMinFreeSpace()));
            GlobalContext.getEventService().publishAsync(new AFSEvent(null, remoteSlave));
            if (AutoFreeSpaceSettings.getSettings().getOnlyAnnounce()) {
                return;
            }

            int deletedCount = 0;
            int maxIterations = AutoFreeSpaceSettings.getSettings().getMaxIterations();
            while (isRunning && deletedCount < maxIterations) {

                if (freespace > freespaceMinimum) {
                    logger.info("freespace [{}] reached the desired minimum [{}], stopping iteration", Bytes.formatBytes(freespace), Bytes.formatBytes(freespaceMinimum));
                    break;
                }

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
                        // Measure before deleting: afterwards the files are gone from the VFS
                        long sizeOnSlave = getSizeOnSlave(oldestRelease, remoteSlave);
                        logger.info("AUTODELETE: Removing {}", oldestRelease.getName());
                        oldestRelease.deleteUnchecked(); // Throws the FileNotFoundException
                        freespace = remoteSlave.getSlaveStatus().getDiskSpaceAvailable();
                        logger.info("AUTODELETE: Removed {}, cleared {} on {}", oldestRelease.getName(), Bytes.formatBytes(sizeOnSlave), remoteSlave.getName());
                        if (sizeOnSlave == 0) {
                            logger.error("AUTODELETE: Removed {} but it held 0 bytes on slave {}, nothing was freed. Stopping iteration", oldestRelease.getName(), remoteSlave.getName());
                            deletedCount++;
                            break;
                        }
                    }
                    deletedCount++;
                } catch (FileNotFoundException e) {
                    logger.error("AUTODELETE: Deleted [{}] releases on slave {} before we ran into an unexpected exception: {}", deletedCount, remoteSlave.getName(), e.getCause());
                    break;
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

        /**
         * Returns the total size of the files of inode that are stored on slave.
         */
        private long getSizeOnSlave(InodeHandle inode, RemoteSlave slave) {
            long size = 0;
            try {
                if (inode.isFile()) {
                    if (((FileHandle) inode).getAvailableSlaves().contains(slave)) {
                        size = inode.getSize();
                    }
                } else if (inode.isDirectory()) {
                    for (FileHandle file : ((DirectoryHandle) inode).getAllFilesRecursiveUnchecked()) {
                        try {
                            if (file.getAvailableSlaves().contains(slave)) {
                                size += file.getSize();
                            }
                        } catch (NoAvailableSlaveException | FileNotFoundException e) {
                            // File not on any online slave or already gone: it frees nothing here
                        }
                    }
                }
            } catch (NoAvailableSlaveException | FileNotFoundException e) {
                // Inode already gone or not on any online slave
            }
            return size;
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

            Collection<AutoFreeSpaceSettings.Section> entries = AutoFreeSpaceSettings.getSettings().getSections().values();
            if (AutoFreeSpaceSettings.getSettings().getWipeAllSections()) {
                // Opt-in (wipe.all.sections=true, space mode, no entry written): consider every section
                for (SectionInterface si : GlobalContext.getGlobalContext().getSectionManager().getSections()) {
                    DirectoryHandle baseDir = si.getBaseDirectory();
                    if (baseDir.isRoot()) {
                        continue; // never treat the top level directories themselves as releases
                    }
                    oldest = pickOlder(oldest, baseDir, new AutoFreeSpaceSettings.Section(0, si.getName(), null, 0), slave);
                }
                return oldest;
            }

            // Loop over all configured entries (sections and paths)
            for (AutoFreeSpaceSettings.Section section : entries) {
                for (DirectoryHandle baseDir : withoutRoot(getBaseDirectories(section), DirectoryHandle::isRoot)) {
                    oldest = pickOlder(oldest, baseDir, section, slave);
                }
            }

            return oldest;
        }

        /**
         * Returns the directories whose releases are candidates for this entry: the section's base directory,
         * or every directory matching the configured path (each {@code *} level matches all subdirectories).
         */
        private List<DirectoryHandle> getBaseDirectories(AutoFreeSpaceSettings.Section section) {
            if (section.getPath() == null) {
                SectionInterface si = GlobalContext.getGlobalContext().getSectionManager().getSection(section.getName());
                if (!si.getName().equalsIgnoreCase(section.getName())) {
                    logger.debug("Skipping section [{}] as it no longer exists", section.getName());
                    return Collections.emptyList();
                }
                return Collections.singletonList(si.getBaseDirectory());
            }
            List<DirectoryHandle> dirs = expandPath(section.getPath(), GlobalContext.getGlobalContext().getRoot(),
                    dir -> {
                        try {
                            return dir.getDirectoriesUnchecked();
                        } catch (FileNotFoundException e) {
                            return Collections.emptySet();
                        }
                    },
                    (dir, name) -> {
                        try {
                            DirectoryHandle child = dir.getDirectoryUnchecked(name);
                            return child.exists() ? child : null;
                        } catch (FileNotFoundException | ObjectNotValidException e) {
                            return null;
                        }
                    });
            if (dirs.isEmpty()) {
                logger.debug("No directory matches path [{}]", section.getPath());
            }
            return dirs;
        }

        /** Returns whichever is older: the current candidate, or the oldest eligible release in baseDir. */
        private InodeHandle pickOlder(InodeHandle oldest, DirectoryHandle baseDir, AutoFreeSpaceSettings.Section section, RemoteSlave slave) {
            String where = baseDir.getPath();
            logger.debug("AUTODELETE: Getting oldest release in {}", where);
            try {
                InodeHandle file = getOldestFile(baseDir, slave);

                // Quickly skip if this directory does not have anything
                if (file == null) {
                    return oldest;
                }

                logger.debug("AUTODELETE: Oldest file in {}: {}", where, file.getName());

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
                                logger.debug("AUTODELETE: Oldest file: {}. Found in {}", file.getName(), where);
                            } else {
                                logger.debug("AUTODELETE: New oldest file: {} (previous oldest: {}). Found in {}", file.getName(), oldest.getName(), where);
                            }
                            oldest = file;
                        }
                    }
                }
            } catch (FileNotFoundException e) {
                logger.warn("AUTODELETE: File missing", e.getCause());
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
