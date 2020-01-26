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
package org.drftpd.vfs.index.lucene;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.apache.lucene.store.FSDirectory;
import org.drftpd.GlobalContext;
import org.drftpd.io.PhysicalFile;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;

/**
 * Thread that performs backup operations on the index.
 * @author fr0w
 * @version $Id$
 */
public class LuceneBackupThread  extends Thread {
	private static final Logger logger = LogManager.getLogger(LuceneBackupThread.class);
	
	private static final SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd-HHmmss");
	private static final String BACKUP_DIRNAME = "index.bkp";
	private static final File BACKUP_HOME = new File(BACKUP_DIRNAME);

	private boolean _stop;
	private boolean _isRunning;
	
	protected int _maxNumberBackup;
	private boolean _doBackups;
	private int _backupInterval;
	private long _lastBackup;
	
	private LuceneEngine _engine;

	protected LuceneBackupThread() {
		setName("LuceneBackupThread");
		_engine = (LuceneEngine) GlobalContext.getGlobalContext().getIndexEngine();
	}

	public void run() {
		String[] backups;
		int x;

        while (!_stop) {

            // store a limited amount of backups.
            // the code bellow remove older backups.
            backups = BACKUP_HOME.list();

            if (backups != null) {
                Arrays.sort(backups, String.CASE_INSENSITIVE_ORDER);
                for (x = 0; x < backups.length; x++) {
                    if (BACKUP_HOME.list().length < _maxNumberBackup) {
                        break;
                    }
                    new PhysicalFile(BACKUP_DIRNAME + "/" + backups[x]).deleteRecursive();
                }
            }

            if (_doBackups) {
                // locking the writer object so that noone can use it.
                // this might be useful.
                synchronized (_engine.getWriter()) {
                    setRunning(true);

                    String dateTxt = sdf.format(new Date(System.currentTimeMillis()));
                    File f = new File(BACKUP_DIRNAME + "/" + dateTxt);

                    try {
                        if (!f.mkdirs()) {
                            throw new IOException("Impossible to create backup directory, not enough permissions.");
                        }

                        // creating the destination directory.
                        FSDirectory bkpDirectory = FSDirectory.open(f);

                        for (String file : _engine.getStorage().listAll()) {
                            _engine.getStorage().copy(bkpDirectory, file, file);
                        }

                        logger.debug("A backup of the index was created successfully.");
                        updateLastBackupTime();
                    } catch (IOException e) {
                        logger.error(e, e);
                    }
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
	 * Will force the backup thread to stop.<br>
	 * If there's an operation already running, it will be finished before the thread is stopped.
	 */
	protected void stopBackup() {
		_stop = true;
	}
	
	/**
	 * Set if the thread is currently performing a backup or not.
	 * @param isRunning
	 */
	protected void setRunning(boolean isRunning) {
		_isRunning = isRunning;
	}
	
	/**
	 * @return true if a backup is being performed or false if not.
	 */
	protected boolean isRunning() {
		return _isRunning;
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
	 * @param status
	 */
	protected void setDoBackups(boolean status) {
		_doBackups = status;
	}
	
	/**
	 * Set the backup interval.
	 * @param interval
	 */
	protected void setBackupInterval(int interval) {
		_backupInterval = interval;
	}
	
	/**
	 * Set the max number of backup that are going to be kept.
	 * @param max
	 */
	protected void setMaximumNumberBackup(int max) {
		_maxNumberBackup = max;
	}
}
