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
package org.drftpd.plugins.archive.archivetypes;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Properties;
import java.util.Set;

import org.apache.log4j.Logger;
import org.drftpd.GlobalContext;
import org.drftpd.PluginInterface;
import org.drftpd.PropertyHelper;
import org.drftpd.exceptions.ObjectNotFoundException;
import org.drftpd.master.RemoteSlave;
import org.drftpd.plugins.archive.Archive;
import org.drftpd.plugins.archive.DuplicateArchiveException;
import org.drftpd.plugins.jobmanager.Job;
import org.drftpd.plugins.jobmanager.JobManager;
import org.drftpd.sections.SectionInterface;
import org.drftpd.vfs.DirectoryHandle;
import org.drftpd.vfs.FileHandle;

/**
 * @author zubov
 * @version $Id$
 */
public abstract class ArchiveType {
	private static final Logger logger = Logger.getLogger(ArchiveType.class);

	private long _archiveAfter;

	private DirectoryHandle _directory;

	protected Archive _parent;

	protected SectionInterface _section;

	protected Set<RemoteSlave> _slaveList;

	protected int _numOfSlaves;
	
	protected int _priority;

	/**
	 * Sets _slaveList, _numOfSlaves, _section, _parent, and _archiveAfter Each
	 * implementing ArchiveType still needs to check/validate these figures to
	 * their specific needs After the constructor finishes, _slaveList is
	 * guaranteed to be non-null, but can still be empty
	 * 
	 * @param archive
	 * @param section
	 * @param p
	 */
	public ArchiveType(Archive archive, SectionInterface section, Properties p) {
		_parent = archive;
		_section = section;
		setProperties(p);
	}

	/**
	 * Used to determine a list of slaves dynamically during runtime, only gets
	 * called if _slaveList == null
	 * 
	 * @return
	 */
	public abstract Set<RemoteSlave> findDestinationSlaves();

	public final DirectoryHandle getDirectory() {
		return _directory;
	}

	/**
	 * Returns the oldest LinkedRemoteFile(directory) that needs to be archived
	 * by this type's definition If no such directory exists, it returns null
	 */
	public final DirectoryHandle getOldestNonArchivedDir() {
		ArrayList<DirectoryHandle> oldDirs = new ArrayList<DirectoryHandle>();

		try {
			for (Iterator<DirectoryHandle> iter = getSection()
					.getCurrentDirectory().getDirectoriesUnchecked().iterator(); iter
					.hasNext();) {
				DirectoryHandle lrf = iter.next();
				try {
					_parent.checkPathForArchiveStatus(lrf.getPath());
				} catch (DuplicateArchiveException e1) {
					continue;
				}

				try {
					if (!isArchivedDir(lrf)) {
						if ((System.currentTimeMillis() - lrf.lastModified()) > getArchiveAfter()) {
							oldDirs.add(lrf);
						}
					}
				} catch (IncompleteDirectoryException e) {
					continue;
				} catch (OfflineSlaveException e) {
					continue;
				} catch (FileNotFoundException e) {
					continue;
					// directory was deleted or moved
				}
			}
		} catch (FileNotFoundException e) {
			// section does not exist, no directories to archive
			// list is empty so the rest of the code will handle that
		}

		DirectoryHandle oldestDir = null;
		long oldestDirLM = 0;
		for (Iterator<DirectoryHandle> iter = oldDirs.iterator(); iter
				.hasNext();) {
			DirectoryHandle temp = iter.next();

			if (oldestDir == null) {
				oldestDir = temp;
				try {
					oldestDirLM = oldestDir.lastModified();
				} catch (FileNotFoundException e) {
					oldestDir = null;
					iter.remove();
				}
				continue;
			}
			try {
				if (oldestDirLM > temp.lastModified()) {
					oldestDir = temp;
				}
			} catch (FileNotFoundException e) {
				iter.remove();
				continue;
			}
		}
		if (oldestDir != null) {
			logger.debug(getClass().toString()
					+ " - Returning the oldest directory " + oldestDir);
			return oldestDir;
		}
		logger.debug(getClass().toString() + " - All directories are archived");
		return null;
	}

	/**
	 * if the directory is archived by this type's definition, this method
	 * returns true
	 * @throws FileNotFoundException 
	 */
	protected abstract boolean isArchivedDir(DirectoryHandle directory)
			throws IncompleteDirectoryException, OfflineSlaveException, FileNotFoundException;

	/**
	 * Returns unmodifiable Set<RemoteSlave>.
	 */
	public final Set<RemoteSlave> getRSlaves() {
		return _slaveList == null ? null : Collections
				.unmodifiableSet(_slaveList);
	}
	
	public JobManager getJobManager() {
		for (PluginInterface plugin : GlobalContext.getGlobalContext().getPlugins()) {
			if (plugin instanceof JobManager) {
				return (JobManager) plugin;
			}
		}
		throw new RuntimeException("JobManager is not loaded");
	}

	/**
	 * Adds relevant Jobs to the JobManager and returns an ArrayList of those
	 * Job's
	 * 
	 * @throws FileNotFoundException
	 */
	public ArrayList<Job> send() throws FileNotFoundException {
		ArrayList<Job> jobs = recursiveSend(getDirectory());
		JobManager jm = getJobManager();
		jm.addJobsToQueue(jobs);
		return jobs;
	}

	protected ArrayList<Job> recursiveSend(DirectoryHandle lrf)
			throws FileNotFoundException {
		ArrayList<Job> jobQueue = new ArrayList<Job>();

		for (Iterator<DirectoryHandle> iter = lrf.getDirectoriesUnchecked().iterator(); iter
				.hasNext();) {
			jobQueue.addAll(recursiveSend(iter.next()));
		}
		for (Iterator<FileHandle> iter = lrf.getFilesUnchecked().iterator(); iter
				.hasNext();) {
			FileHandle file = iter.next();
			logger.info("Adding " + file.getPath() + " to the job queue");
			Job job = new Job(file, _priority, _numOfSlaves, getRSlaves());
			jobQueue.add(job);
		}

		return jobQueue;
	}

	protected static final boolean isArchivedToXSlaves(DirectoryHandle lrf,
			int x) throws IncompleteDirectoryException, OfflineSlaveException {
		HashSet<RemoteSlave> slaveSet = null;
		Set<DirectoryHandle> directories = null;
		Set<FileHandle> files = null;
		try {
			directories = lrf.getDirectoriesUnchecked();
			files = lrf.getFilesUnchecked();
		} catch (FileNotFoundException e1) {
			// directory doesn't exist, no files to archive
			return true;
		}

		/*
		 * try { if (!lrf.getSFVStatus().isFinished()) {
		 * logger.debug(lrf.getPath() + " is not complete"); throw new
		 * IncompleteDirectoryException(lrf.getPath() + " is not complete"); } }
		 * catch (FileNotFoundException e) { } catch (IOException e) { } catch
		 * (NoAvailableSlaveException e) { throw new OfflineSlaveException("SFV
		 * is offline", e); }
		 */
		// I don't like this code to begin with, it depends on SFV
		// this should be configurable at least
		for (Iterator<DirectoryHandle> iter = directories.iterator(); iter
				.hasNext();) {
			if (!isArchivedToXSlaves(iter.next(), x)) {
				return false;
			}
		}
		for (Iterator<FileHandle> iter = files.iterator(); iter.hasNext();) {
			FileHandle file = iter.next();
			Collection<RemoteSlave> availableSlaves;
			try {
				if (!file.isAvailable())
					throw new OfflineSlaveException(file.getPath()
							+ " is offline");
				availableSlaves = file.getSlaves();
			} catch (FileNotFoundException e) {
				// can't archive a directory with files that have been moved,
				// we'll come back later
				return true;
			}

			if (slaveSet == null) {
				slaveSet = new HashSet<RemoteSlave>(availableSlaves);
			} else {
				if (!(slaveSet.containsAll(availableSlaves) && availableSlaves
						.containsAll(slaveSet))) {
					return false;
				}
			}
		}

		if (slaveSet == null) { // no files found in directory
			return true;
		}

		for (RemoteSlave rslave : slaveSet) {
			if (!rslave.isAvailable()) {
				throw new OfflineSlaveException(rslave.getName()
						+ " is offline");
			}
		}

		return (slaveSet.size() == x);
	}

	public final boolean isBusy() {
		return (getDirectory() != null);
	}

	protected final long getArchiveAfter() {
		return _archiveAfter;
	}

	public final SectionInterface getSection() {
		return _section;
	}

	/**
	 * Sets standard properties for this ArchiveType
	 */
	private void setProperties(Properties properties) {
		try {
			_archiveAfter = 60000 * Long.parseLong(PropertyHelper.getProperty(
					properties, getSection().getName() + ".archiveAfter"));
		} catch (NullPointerException e) {
			_archiveAfter = 0;
		}
		_numOfSlaves = Integer.parseInt(properties.getProperty(getSection()
				.getName()
				+ ".numOfSlaves", "0"));

		HashSet<RemoteSlave> destSlaves = new HashSet<RemoteSlave>();

		for (int i = 1;; i++) {
			String slavename = null;

			try {
				slavename = PropertyHelper.getProperty(properties, getSection()
						.getName()
						+ ".slavename." + i);
			} catch (NullPointerException e) {
				break; // done
			}

			try {
				RemoteSlave rslave = GlobalContext.getGlobalContext()
						.getSlaveManager().getRemoteSlave(slavename);
				destSlaves.add(rslave);
			} catch (ObjectNotFoundException e) {
				logger.error("Unable to get slave " + slavename
						+ " from the SlaveManager");
			}
		}
		_slaveList = destSlaves;
		_priority = Integer.parseInt(properties.getProperty(getSection()
				.getName()
				+ ".priority", "3"));
	}

	public final void setDirectory(DirectoryHandle lrf) {
		_directory = lrf;
	}

	public final void setRSlaves(Set<RemoteSlave> slaveList) {
		_slaveList = slaveList;
	}

	public final void waitForSendOfFiles(ArrayList<Job> jobQueue) {
		while (true) {
			for (Iterator<Job> iter = jobQueue.iterator(); iter.hasNext();) {
				Job job = iter.next();

				if (job.isDone()) {
					logger.debug("Job " + job + " is done being sent");
					iter.remove();
				}
			}

			try {
				Thread.sleep(10000);
			} catch (InterruptedException e) {
			}

			if (jobQueue.isEmpty()) {
				break;
			}
		}
	}

	public abstract String toString();

	protected String outputSlaves(Collection<RemoteSlave> slaveList) {
		StringBuilder slaveBuilder = new StringBuilder();
		
		for (Iterator<RemoteSlave> iter = slaveList.iterator(); iter.hasNext();) {
			RemoteSlave rslave = iter.next();
			slaveBuilder.append(rslave.getName());

			if (iter.hasNext()) {
				slaveBuilder.append(',');
			} else {
				return slaveBuilder.toString();
			}
		}

		return "Empty";
	}

	public Set<RemoteSlave> getOffOfSlaves(Properties props) {
		Set<RemoteSlave> offOfSlaves = new HashSet<RemoteSlave>();

		for (int i = 1;; i++) {
			String slavename = null;

			try {
				slavename = PropertyHelper.getProperty(props, getSection()
						.getName()
						+ ".offOfSlave." + i);
			} catch (NullPointerException e) {
				break; // done
			}

			try {
				RemoteSlave rslave = GlobalContext.getGlobalContext()
						.getSlaveManager().getRemoteSlave(slavename);
				if (!_slaveList.contains(rslave)) {
					offOfSlaves.add(rslave);
				}
			} catch (ObjectNotFoundException e) {
				logger.error("Unable to get slave " + slavename
						+ " from the SlaveManager");
			}
		}
		return offOfSlaves;

	}
}
