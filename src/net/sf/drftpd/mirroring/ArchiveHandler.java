/*
 * Created on Dec 5, 2003
 *
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

/**
 * @author zubov
 * @version $Id: ArchiveHandler.java,v 1.9 2004/01/14 02:35:36 mog Exp $
 */
public class ArchiveHandler extends Thread {

	private static final Logger logger = Logger.getLogger(ArchiveHandler.class);
	private DirectoryFtpEvent _dirEvent;
	private Archive _parent;

	public ArchiveHandler(DirectoryFtpEvent dirEvent, Archive archive) {
		reload();
		_dirEvent = dirEvent;
		_parent = archive;
	}
	//	private void deleteFilesFromSlave(
	//		ArrayList jobQueue,
	//		RemoteSlave destSlave) {
	//		for (Iterator iter = jobQueue.iterator(); iter.hasNext();) {
	//			Job job = (Job) iter.next();
	//			job.getFile().removeSlave(destSlave);
	//			Slave slave = null;
	//			try {
	//				slave = destSlave.getSlave();
	//			} catch (NoAvailableSlaveException ex) {
	//				logger.info("slave not available for deletion");
	//				continue;
	//			}
	//			try {
	//				slave.delete(job.getFile().getName());
	//			} catch (RemoteException e1) {
	//				logger.info(
	//					"Was not able to delete file "
	//						+ job.getFile()
	//						+ " from slave "
	//						+ destSlave.getName());
	//			} catch (IOException e1) {
	//				logger.fatal(
	//					"IOException deleting file on slave " + destSlave.getName(),
	//					e1);
	//				continue;
	//			}
	//			iter.remove();
	//		}
	//	}
	private RemoteSlave findDestinationSlave(LinkedRemoteFile lrf) {
		if (_parent.isArchiveToFreeSlave()) {
			return _parent
				.getConnectionManager()
				.getSlaveManager()
				.findLargestFreeSlave();
		}
		ArrayList slaveList = new ArrayList();
		for (Iterator iter = lrf.getFiles().iterator(); iter.hasNext();) {
			Collection tempSlaveList =
				((LinkedRemoteFile) iter.next()).getSlaves();
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
			//System.out.println(x + ": slave = " + rslave.getName() + ", " + slaveCount);
		}
		return highSlave;
	}

	/**
	 * @deprecated
	 */
	private LinkedRemoteFile getOldestDirectoryOnSlave(
		LinkedRemoteFile lrf,
		RemoteSlave rslave) {
		if (lrf.getDirectories().size() == 0) {
			Collection files = lrf.getFiles();
			if (files.size() == 0) {
				logger.info(lrf.getPath() + " does not have any files in it");
				return null;
			}
			if (_parent.getArchivingList().contains(lrf.getPath())) {
				logger.info(
					lrf.getPath()
						+ " is already being handled by another ArchiveHandler");
				return null;
			}
			try {
				if (lrf.lookupSFVFile().getStatus().getMissing() > 0) {
					logger.info(
						lrf.getPath()
							+ " does not have all files complete, will not use it to make space on "
							+ rslave.getName());
					return null;
				}
			} catch (Exception e) {
				logger.info(
					lrf.getPath() + " exception in lookupSFVFile() ",
					e);
				return null;
			}
			for (Iterator iter = files.iterator(); iter.hasNext();) {
				if (((LinkedRemoteFile) iter.next()).hasSlave(rslave))
					return lrf;
			}
			return null;
		}
		ArrayList oldDirs = new ArrayList();
		for (Iterator iter = lrf.getDirectories().iterator();
			iter.hasNext();
			) {
			LinkedRemoteFile temp =
				getOldestDirectoryOnSlave(
					(LinkedRemoteFile) iter.next(),
					rslave);
			// if temp == null, there are no good files to move in this directory tree
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
		if (lrf.getDirectories().size() == 0) {
			if (System.currentTimeMillis() - lrf.lastModified()
				< _parent.getArchiveAfter()) {
				//logger.debug(lrf.getPath() + " is too young to archive");
				return null;
			}
			Collection files = lrf.getFiles();
			if (files.size() == 0) {
				logger.info(
					lrf.getPath()
						+ " does not have any files in it, it is already archived");
				return null;
			}
			if (_parent.getArchivingList().contains(lrf.getPath())) {
				logger.info(
					lrf.getPath()
						+ " is already being handled by another ArchiveHandler");
				return null;
			}
			try {
				if (lrf.lookupSFVFile().getStatus().getOffline() > 0) {
					logger.info(
						lrf.getPath()
							+ " does not have all files online, will not archive it");
					return null;
				}
				if (lrf.lookupSFVFile().getStatus().getMissing() > 0) {
					logger.info(
						lrf.getPath()
							+ " does not have all files complete, will not archive it");
					return null;
				}
			} catch (Exception e) {
				logger.info(
					lrf.getPath() + " exception in lookupSFVFile() ",
					e);
				return null;
			}
			ArrayList slaveList = new ArrayList();
			for (Iterator iter = files.iterator(); iter.hasNext();) {
				LinkedRemoteFile temp = (LinkedRemoteFile) iter.next();
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
			LinkedRemoteFile temp =
				getOldestNonArchivedDir((LinkedRemoteFile) iter.next());
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
				// for testing			if (oldestDir.lastModified() < temp.lastModified()) {
				oldestDir = temp;
			}
		}
		return oldestDir;
	}
	private void reload() {
		Properties props = new Properties();
		try {
			props.load(new FileInputStream("archive.conf"));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		// archive slave list
	}
	/* (non-Javadoc)
	 * @see java.lang.Runnable#run()
	 */
	public void run() {
		LinkedRemoteFile oldDir = _dirEvent.getDirectory();
		LinkedRemoteFile root = oldDir.getRoot();
		JobManager jm = _parent.getConnectionManager().getJobManager();
		//		if (_parent.getMoveFullSlaves() > 0) {
		//			try {
		//				while (true) {
		//					RemoteSlave smallSlave =
		//						_parent
		//							.getConnectionManager()
		//							.getSlaveManager()
		//							.findSmallestFreeSlave();
		//					if (smallSlave.getStatus().getDiskSpaceAvailable()
		//						> _parent.getMoveFullSlaves()) {
		//						break;
		//						// all done
		//					}
		//					LinkedRemoteFile tempDir =
		//						getOldestDirectoryOnSlave(root, smallSlave);
		//					ArrayList jobQueue = new ArrayList();
		//					for (Iterator iter = oldDir.getFiles().iterator();
		//						iter.hasNext();
		//						) {
		//						LinkedRemoteFile src = (LinkedRemoteFile) iter.next();
		//						AbstractJob job = null;
		//						if (!src.getSlaves().contains(smallSlave)) {
		//							ArrayList tempList = new ArrayList();
		//							tempList.add(smallSlave);
		//							logger.info(
		//								"Adding "
		//									+ src.getPath()
		//									+ " to the job queue");
		//							job = new AbstractJob(src, tempList, this, null, 3);
		//							jm.addJob(job);
		//							jobQueue.add(job);
		//						}
		//					}
		//					ArrayList tempQueue = (ArrayList) jobQueue.clone();
		//					sendFiles(tempQueue, smallSlave, jm);
		//					deleteFilesFromSlave(jobQueue, smallSlave);
		//				}
		//			} catch (RemoteException e) {
		//				logger.info(
		//					"Could not evaluate the size on the slaves, skipping the getMoveFullSlaves setting",
		//					e);
		//			} catch (NoAvailableSlaveException e) {
		//				// done
		//			}
		//		}
		synchronized (_parent) {
			oldDir = getOldestNonArchivedDir(root);
			if (oldDir == null)
				return; //everything is archived
			_parent.addToArchivingList(oldDir.getPath());
		}
		logger.debug("The oldest Directory is " + oldDir.getPath());
		RemoteSlave slave = findDestinationSlave(oldDir);
		logger.debug("findDestinationSlave() returned " + slave.getName());
		logger.debug("The slave to archive to is " + slave.getName());
		ArrayList jobQueue = new ArrayList();
		for (Iterator iter = oldDir.getFiles().iterator(); iter.hasNext();) {
			LinkedRemoteFile src = (LinkedRemoteFile) iter.next();
			Job job = null;
			//if (!src.getSlaves().contains(slave)) {
			// I don't care if it's already on that slave, the JobManager will take care of it
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
	 * waits until the LinkedRemoteFiles in the ArrayList jobQueue are sent and deletes them from the non-archived slave
	 */
	private void waitForSendOfFiles(
		ArrayList jobQueue,
		RemoteSlave destSlave) {
		try {
			while (true) {
				for (Iterator iter = jobQueue.iterator(); iter.hasNext();) {
					Job job = (Job) iter.next();
					if (job.isDone()) {
						logger.debug(
							"File "
								+ job.getFile().getPath()
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