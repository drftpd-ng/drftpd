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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

import net.sf.drftpd.NoAvailableSlaveException;
import net.sf.drftpd.master.RemoteSlave;
import net.sf.drftpd.mirroring.Job;
import net.sf.drftpd.mirroring.JobManager;
import net.sf.drftpd.remotefile.LinkedRemoteFileInterface;

import org.apache.log4j.Logger;
import org.drftpd.mirroring.ArchiveType;

/**
 * @author zubov
 * @version $Id: MoveReleaseToMostFreeSlave.java,v 1.4 2004/04/26 21:41:54 zubov Exp $
 */
public class MoveReleaseToMostFreeSlave extends ArchiveType {
	private static final Logger logger = Logger.getLogger(MoveReleaseToMostFreeSlave.class);

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
	
	private ArrayList recursiveSend(LinkedRemoteFileInterface lrf) {
		ArrayList jobQueue = new ArrayList();
		JobManager jm = _parent.getConnectionManager().getJobManager();
		for (Iterator iter = lrf.getFiles().iterator();
			iter.hasNext();
			) {
			LinkedRemoteFileInterface src =
				(LinkedRemoteFileInterface) iter.next();
			if (src.isFile()) {
				Job job = null;
				logger.info("Adding " + src.getPath() + " to the job queue");
				job = new Job(src, getRSlaves(), this, null, 3);
				jm.addJob(job);
				jobQueue.add(job);
			}
			else jobQueue.addAll(recursiveSend(src));
		}
		return jobQueue;
	}

	public ArrayList send() {
		ArrayList jobQueue = new ArrayList();
		jobQueue.addAll(recursiveSend(getDirectory()));
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

	/**
	 * Returns true if this directory is Archived by this ArchiveType's definition
	 */

	protected boolean isArchivedDir(LinkedRemoteFileInterface lrf)
		throws IncompleteDirectoryException, OfflineSlaveException {
			RemoteSlave singleSlave = null;
			for (Iterator iter = lrf.getFiles().iterator(); iter.hasNext();) {
				LinkedRemoteFileInterface file = (LinkedRemoteFileInterface) iter.next();
				if (file.isDirectory()) {
					if (!isArchivedDir(file))
						return false;
					try {
						if (!file.lookupSFVFile().getStatus().isFinished()) {
							logger.debug(file.getPath() + " is not complete");
							throw new IncompleteDirectoryException(file.getPath() + " is not complete");
						}
					} catch (FileNotFoundException e) {
					} catch (IOException e) {
					} catch (NoAvailableSlaveException e) {
					}
				}
				else {// if (file.isFile())
					Collection availableSlaves = file.getSlaves();
					for (Iterator slaveIter = availableSlaves.iterator(); slaveIter.hasNext();) {
						RemoteSlave slave = (RemoteSlave) slaveIter.next();
						if (!slave.isAvailable()) {
							throw new OfflineSlaveException(slave.getName() + " is offline");
						}
						if (singleSlave == null)
							singleSlave = slave;
						if (singleSlave != slave) {
							return false;
						}
					}
				} 
			}
			return true;
	}

}
