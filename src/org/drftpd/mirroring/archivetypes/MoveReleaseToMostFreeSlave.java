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
import java.util.Iterator;

import org.drftpd.mirroring.ArchiveType;

import net.sf.drftpd.NoAvailableSlaveException;
import net.sf.drftpd.master.RemoteSlave;
import net.sf.drftpd.mirroring.Job;
import net.sf.drftpd.mirroring.JobManager;
import net.sf.drftpd.remotefile.LinkedRemoteFile;
import net.sf.drftpd.remotefile.LinkedRemoteFileInterface;

/*
 * @author zubov
 * @version $Id
 */
public class MoveReleaseToMostFreeSlave extends ArchiveType {

	public MoveReleaseToMostFreeSlave() {
		super();
	}

	public void cleanup(ArrayList jobList) {
		if (getRSlaves().isEmpty() || getRSlaves().size() > 1) {
			throw new IllegalStateException("getRSlaves() can only have one RemoteSlave in it");
		}
		RemoteSlave destSlave = (RemoteSlave) getRSlaves().get(0);
		for (Iterator iter = jobList.iterator(); iter.hasNext();) {
			Job job = (Job) iter.next();
			job.getFile().deleteOthers(destSlave);
		}
	}

	public ArrayList findDestinationSlaves() {
		ArrayList slaveList = new ArrayList();
		slaveList.add(
			_parent
				.getConnectionManager()
				.getSlaveManager()
				.findLargestFreeSlave());
		return slaveList;
	}

	public LinkedRemoteFileInterface getOldestNonArchivedDir() {
		if (_parent.checkExclude(getSection())) {
			return null;
		}
		return getOldestNonArchivedDir(getSection().getFile());
	}

	private LinkedRemoteFileInterface getOldestNonArchivedDir(LinkedRemoteFileInterface lrf) {
		if (lrf.getDirectories().size() == 0) {
			if (System.currentTimeMillis() - lrf.lastModified()
				< _parent.getArchiveAfter()) {
				return null;
			}
			Collection files = lrf.getFiles();
			if (files.size() == 0) {
				return null;
			}
			try {
				if (lrf.lookupSFVFile().getStatus().getMissing() > 0) {
					logger.info(lrf.getPath() + "is not complete");
					return null;
				}
			} catch (Exception e) {
				//assuming complete
			}
			ArrayList slaveList = new ArrayList();
			for (Iterator iter = files.iterator(); iter.hasNext();) {
				LinkedRemoteFile temp = (LinkedRemoteFile) iter.next();
				try {
					if (!temp
						.getAvailableSlaves()
						.containsAll(temp.getSlaves())) {
						//						logger.debug(lrf.getPath() + " contains " + temp.getName() + " which is on an offline slave, will not archive it");
						return null;
					}
				} catch (NoAvailableSlaveException e1) {
					return null;
				}
				for (Iterator iter2 = temp.getSlaves().iterator();
					iter2.hasNext();
					) {
					RemoteSlave tempSlave = (RemoteSlave) iter2.next();
					if (!slaveList.contains(tempSlave))
						slaveList.add(tempSlave);
				}
			}
			if (slaveList.size() == 1)
				return null;
			return lrf;
		}
		ArrayList oldDirs = new ArrayList();
		for (Iterator iter = lrf.getDirectories().iterator();
			iter.hasNext();
			) {
			LinkedRemoteFileInterface temp =
				getOldestNonArchivedDir(
					(LinkedRemoteFileInterface) iter.next());
			// if temp == null all directories are archived
			if (temp != null)
				oldDirs.add(temp);
		}
		LinkedRemoteFile oldestDir = null;
		for (Iterator iter = oldDirs.iterator(); iter.hasNext();) {
			LinkedRemoteFile temp = (LinkedRemoteFile) iter.next();
			if (oldestDir == null) {
				oldestDir = temp;
				continue;
			}
			if (oldestDir.lastModified() > temp.lastModified()) {
				oldestDir = temp;
			}
		}
		return oldestDir;
	}

	public ArrayList send() {
		ArrayList jobQueue = new ArrayList();
		JobManager jm = _parent.getConnectionManager().getJobManager();
		for (Iterator iter = getDirectory().getFiles().iterator();
			iter.hasNext();
			) {
			LinkedRemoteFileInterface src =
				(LinkedRemoteFileInterface) iter.next();
			Job job = null;
			logger.info("Adding " + src.getPath() + " to the job queue");
			job = new Job(src, getRSlaves(), this, null, 3);
			jm.addJob(job);
			jobQueue.add(job);
		}
		return jobQueue;
	}

	public void waitForSendOfFiles(ArrayList jobQueue) {
		if (getRSlaves().isEmpty() || getRSlaves().size() > 1) {
			throw new IllegalStateException("getRSlaves() can only have one RemoteSlave in it");
		}
		RemoteSlave destSlave = (RemoteSlave) getRSlaves().get(0);
		while (true) {
			for (Iterator iter = jobQueue.iterator(); iter.hasNext();) {
				Job job = (Job) iter.next();
				if (job.isDone()) {
					logger.debug(
						"File "
							+ job.getFile().getPath()
							+ " is done being sent");
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

}
