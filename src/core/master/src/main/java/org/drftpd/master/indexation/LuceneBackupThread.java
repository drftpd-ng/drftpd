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
package org.drftpd.master.indexation;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.drftpd.common.io.PhysicalFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;

/**
 * Thread that performs backup operations on the index.
 *
 * @author fr0w
 * @version $Id$
 */
public class LuceneBackupThread extends Thread {
    private static final Logger logger = LogManager.getLogger(LuceneBackupThread.class);

    private static final SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd-HHmmss");
    private static final String BACKUP_DIRNAME = "index.bkp";
    private static final File BACKUP_HOME = new File(BACKUP_DIRNAME);
    protected int _maxNumberBackup;
    private boolean _stop;
    private boolean _isRunning;
    private boolean _doBackups;
    private int _backupInterval;
    private long _lastBackup;

    private final LuceneEngine _engine;

    protected LuceneBackupThread(LuceneEngine engine) {
        setName("LuceneBackupThread");
        _engine = engine;
    }

    public void run() {
        while (!_stop) {
            if (_doBackups) {
                setRunning(true);

                String dateTxt = sdf.format(new Date(System.currentTimeMillis()));

                try {
                    // the engine commits and snapshots the commit point while copying its files,
                    // indexing goes on meanwhile and the backup is a consistent index.
                    // No backup is made while the index is empty or partial (rebuild required or running).
                    if (_engine.backupTo(Paths.get(BACKUP_DIRNAME, dateTxt))) {
                        logger.debug("A backup of the index was created successfully.");
                        updateLastBackupTime();
                        removeOldBackups();
                    } else {
                        logger.debug("Index backup skipped, the index is being rebuilt.");
                    }
                } catch (IOException | RuntimeException e) {
                    logger.error("Unable to backup the index", e);
                }
            }

            try {
                synchronized (this) {
                    setRunning(false);
                    wait(_backupInterval);
                }
            } catch (InterruptedException e) {
            }
        }
    }

    /**
     * Keeps the {@link #_maxNumberBackup} most recent backups, only called after a new backup
     * succeeded so that old backups are never rotated out by a skipped or failed one.
     */
    private void removeOldBackups() {
        String[] backups = BACKUP_HOME.list();
        if (backups == null) {
            return;
        }
        Arrays.sort(backups, String.CASE_INSENSITIVE_ORDER);
        for (int x = 0; x < backups.length - _maxNumberBackup; x++) {
            new PhysicalFile(BACKUP_DIRNAME + "/" + backups[x]).deleteRecursive();
        }
    }

    /**
     * Will force the backup thread to stop.<br>
     * If there's an operation already running, it will be finished before the thread is stopped.
     */
    protected void stopBackup() {
        _stop = true;
    }

    /**
     * @return true if a backup is being performed or false if not.
     */
    protected boolean isRunning() {
        return _isRunning;
    }

    /**
     * Set if the thread is currently performing a backup or not.
     *
     * @param isRunning
     */
    protected void setRunning(boolean isRunning) {
        _isRunning = isRunning;
    }

    /**
     * The last backup will be set to System.currentTimeMillis()
     */
    protected void updateLastBackupTime() {
        _lastBackup = System.currentTimeMillis();
    }

    /**
     * @return the timestamp from the last backup.
     */
    protected long getLastBackup() {
        return _lastBackup;
    }

    /**
     * Set whether backup should run
     *
     * @param status
     */
    protected void setDoBackups(boolean status) {
        _doBackups = status;
    }

    /**
     * Set the backup interval.
     *
     * @param interval
     */
    protected void setBackupInterval(int interval) {
        _backupInterval = interval;
    }

    /**
     * Set the max number of backup that are going to be kept.
     *
     * @param max
     */
    protected void setMaximumNumberBackup(int max) {
        _maxNumberBackup = max;
    }
}
