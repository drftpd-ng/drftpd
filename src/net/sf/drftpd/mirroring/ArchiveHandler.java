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
package net.sf.drftpd.mirroring;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Properties;
import org.apache.log4j.Logger;
import net.sf.drftpd.event.DirectoryFtpEvent;
import net.sf.drftpd.event.listeners.Archive;
import net.sf.drftpd.master.RemoteSlave;
import net.sf.drftpd.remotefile.LinkedRemoteFile;
import net.sf.drftpd.remotefile.LinkedRemoteFileInterface;

/**
 * @author zubov
 * @version $Id: ArchiveHandler.java,v 1.18 2004/03/15 13:53:05 zubov Exp $
 */
public class ArchiveHandler extends Thread {
	private static final Logger logger = Logger.getLogger(ArchiveHandler.class);
	private DirectoryFtpEvent _dirEvent;
	private Archive _parent;
	public ArchiveHandler(Archive archive) {
		_parent = archive;
		setName("ArchiveHandler for unknown");
	}
	private RemoteSlave findDestinationSlave(LinkedRemoteFileInterface lrf) {
		if (_parent.isArchiveToFreeSlave()) {
			return _parent.getConnectionManager().getSlaveManager()
					.findLargestFreeSlave();
		}
		ArrayList slaveList = new ArrayList();
		for (Iterator iter = lrf.getFiles().iterator(); iter.hasNext();) {
			Collection tempSlaveList = ((LinkedRemoteFileInterface) iter.next())
					.getSlaves();
			slaveList.addAll(tempSlaveList);
		}
		Collections.sort(slaveList);
		RemoteSlave highSlave = null;
		int highSlaveCount = 0;
		RemoteSlave slave = null;
		int slaveCount = 0;
		int x = 0;
		for (Iterator iter = slaveList.iterator(); iter.hasNext();) {
			RemoteSlave rslave = (RemoteSlave) iter.next();
			if (highSlave == null) {
				highSlave = rslave;
				slave = rslave;
			}
			if (slave == rslave)
				slaveCount += 1;
			else {
				if (highSlaveCount < slaveCount) {
					highSlaveCount = slaveCount;
					highSlave = slave;
				}
				slaveCount = 1;
				slave = rslave;
			}
			x++;
		}
		return highSlave;
	}
	/**
	 * @deprecated
	 */
	private LinkedRemoteFile getOldestDirectoryOnSlave(LinkedRemoteFile lrf,
			RemoteSlave rslave) {
		if (lrf.getDirectories().size() == 0) {
			Collection files = lrf.getFiles();
			if (files.size() == 0) {
				logger.info(lrf.getPath() + " does not have any files in it");
				return null;
			}
			if (_parent.getArchivingList().contains(lrf.getPath())) {
				logger
						.info(lrf.getPath()
								+ " is already being handled by another ArchiveHandler");
				return null;
			}
			try {
				if (lrf.lookupSFVFile().getStatus().getMissing() > 0) {
					logger
							.info(lrf.getPath()
									+ " does not have all files complete, will not use it to make space on "
									+ rslave.getName());
					return null;
				}
			} catch (Exception e) {
				logger
						.info(lrf.getPath() + " exception in lookupSFVFile() ",
								e);
				return null;
			}
			for (Iterator iter = files.iterator(); iter.hasNext();) {
				if (((LinkedRemoteFileInterface) iter.next()).hasSlave(rslave))
					return lrf;
			}
			return null;
		}
		ArrayList oldDirs = new ArrayList();
		for (Iterator iter = lrf.getDirectories().iterator(); iter.hasNext();) {
			LinkedRemoteFile temp = getOldestDirectoryOnSlave(
					(LinkedRemoteFile) iter.next(), rslave);
			// if temp == null, there are no good files to move in this
			// directory tree
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
	private LinkedRemoteFile getOldestNonArchivedDir(LinkedRemoteFile lrf) {
		if (_parent.checkExclude(lrf)) {
			logger.debug(lrf.getPath() + " is excluded");
			return null;
		}
		if (_parent.getArchivingList().contains(lrf.getPath())) {
			logger.debug(lrf.getPath()
					+ " is already being handled by another ArchiveHandler");
			return null;
		}
		if (lrf.getDirectories().size() == 0) {
			if (System.currentTimeMillis() - lrf.lastModified() < _parent
					.getArchiveAfter()) {
				return null;
			}
			Collection files = lrf.getFiles();
			if (files.size() == 0) {
				logger
						.debug(lrf.getPath()
								+ " does not have any files in it, it is already archived");
				return null;
			}
			try {
				if (lrf.lookupSFVFile().getStatus().getOffline() > 0) {
					logger
							.debug(lrf.getPath()
									+ " does not have all files online, will not archive it");
					return null;
				}
				if (lrf.lookupSFVFile().getStatus().getMissing() > 0) {
					logger
							.info(lrf.getPath()
									+ " does not have all files complete, will not archive it");
					return null;
				}
			} catch (Exception e) {
				//assuming complete
			}
			ArrayList slaveList = new ArrayList();
			for (Iterator iter = files.iterator(); iter.hasNext();) {
				LinkedRemoteFile temp = (LinkedRemoteFile) iter.next();
				for (Iterator iter2 = temp.getSlaves().iterator(); iter2
						.hasNext();) {
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
		for (Iterator iter = lrf.getDirectories().iterator(); iter.hasNext();) {
			LinkedRemoteFile temp = getOldestNonArchivedDir((LinkedRemoteFile) iter
					.next());
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
	/**
	 * @deprecated
	 */
	private void reload() {
		Properties props = new Properties();
		try {
			props.load(new FileInputStream("conf/archive.conf"));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	public void run() {
		LinkedRemoteFile root = _parent.getConnectionManager().getRoot();
		LinkedRemoteFile oldDir;
		JobManager jm = _parent.getConnectionManager().getJobManager();
		synchronized (_parent) {
			oldDir = getOldestNonArchivedDir(root);
			if (oldDir == null)
				return; //everything is archived
			setName("ArchiveHandler for " + oldDir.getPath());
			_parent.addToArchivingList(oldDir.getPath());
		}
		logger.debug("The oldest Directory is " + oldDir.getPath());
		RemoteSlave slave = findDestinationSlave(oldDir);
		logger.debug("The slave to archive to is " + slave.getName());
		ArrayList jobQueue = new ArrayList();
		for (Iterator iter = oldDir.getFiles().iterator(); iter.hasNext();) {
			LinkedRemoteFileInterface src = (LinkedRemoteFileInterface) iter.next();
			Job job = null;
			//if (!src.getSlaves().contains(slave)) {
			// I don't care if it's already on that slave, the JobManager will
			// take care of it
			ArrayList tempList = new ArrayList();
			tempList.add(slave);
			logger.info("Adding " + src.getPath() + " to the job queue");
			job = new Job(src, tempList, this, null, 3);
			jm.addJob(job);
			jobQueue.add(job);
			//}
		}
		waitForSendOfFiles(jobQueue, slave);
		_parent.removeFromArchivingList(oldDir.getPath());
		logger.debug("Done archiving " + oldDir.getPath());
	}
	/**
	 * waits until the LinkedRemoteFiles in the ArrayList jobQueue are sent and
	 * deletes them from the non-archived slave
	 */
	private void waitForSendOfFiles(ArrayList jobQueue, RemoteSlave destSlave) {
		try {
			while (true) {
				for (Iterator iter = jobQueue.iterator(); iter.hasNext();) {
					Job job = (Job) iter.next();
					if (job.isDone()) {
						logger.debug("File " + job.getFile().getPath()
								+ " is done being sent");
						job.getFile().deleteOthers(destSlave);
						iter.remove();
					}
				}
				try {
					sleep(10000);
				} catch (InterruptedException e) {
				}
				if (jobQueue.isEmpty()) {
					break;
				}
			}
		} catch (Exception e) {
			logger.debug("Exception in waitForSendOfFiles()", e);
		}
	}
}