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
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import net.sf.drftpd.FatalException;
import net.sf.drftpd.NoAvailableSlaveException;
import net.sf.drftpd.SlaveUnavailableException;
import net.sf.drftpd.master.ConnectionManager;
import net.sf.drftpd.master.RemoteSlave;
import net.sf.drftpd.master.config.FtpConfig;
import net.sf.drftpd.remotefile.LinkedRemoteFileInterface;
import org.apache.log4j.Logger;
/**
 * @author zubov
 * @version $Id: JobManager.java,v 1.36 2004/03/03 04:48:08 zubov Exp $
 */
public class JobManager implements Runnable {
	private static final Logger logger = Logger.getLogger(JobManager.class);
	private ConnectionManager _cm;
	private boolean _isStopped = false;
	private ArrayList _jobList = new ArrayList();
	private boolean _useCRC;
	private Thread thread;
	private int _sleepSeconds;
	/**
	 * Keeps track of all jobs and controls them
	 */
	public JobManager(ConnectionManager cm) throws IOException {
		_cm = cm;
		reload();
	}
	public void startJobs() {
		if (thread != null) {
			stopJobs();
			thread.interrupt();
			while(thread.isAlive()) {
				logger.debug("thread is still alive");
				Thread.yield();
			}
		}
		_isStopped = false;
		thread = new Thread(this,"JobTransferStarter");
		thread.start();
	}
	
	public void stopJobs() {
		_isStopped = true;
	}
	
	public boolean isStopped() {
		return _isStopped;
	}

	public synchronized void addJob(Job job) {
		for (Iterator iter = _jobList.iterator(); iter.hasNext();) {
			Job temp = (Job) iter.next();
			if (temp.getFile() == job.getFile()) {
				temp.addSlaves(job.getDestinationSlaves());
				return;
			}
		}
		_jobList.add(job);
		Collections.sort(_jobList, new JobComparator());
	}
	/**
	 * Gets all jobs.
	 * 
	 * @return All jobs.
	 */
	public synchronized List getAllJobs() {
		return Collections.unmodifiableList(_jobList);
	}
	/**
	 * Get all jobs for a specific LinkedRemoteFile.
	 * 
	 * @param lrf
	 *            The LinkedRemoteFile to return all jobs for.
	 * @return A <code>java.util.List</code> of all jobs for the specific
	 *         LinkedRemoteFile
	 */
	public synchronized List getAllJobs(LinkedRemoteFileInterface lrf) {
		ArrayList tempList = new ArrayList();
		for (Iterator iter = _jobList.iterator(); iter.hasNext();) {
			Job tempJob = (Job) iter.next();
			if (tempJob.getFile() == lrf) {
				tempList.add(tempJob);
			}
		}
		return tempList;
	}
	/**
	 * Get all jobs where Job#getSource() is source
	 * 
	 * @param source
	 *            The source of all objects to get.
	 * @return List of all <code>Job</code> s
	 */
	public synchronized List getAllJobs(Object source) {
		ArrayList tempList = new ArrayList();
		for (Iterator iter = _jobList.iterator(); iter.hasNext();) {
			Job tempJob = (Job) iter.next();
			if (tempJob.getSource().equals(source))
				tempList.add(tempJob);
		}
		return tempList;
	}
	/**
	 * Gets the next job suitable for the slave
	 * @deprecated
	 */
	public synchronized Job getNextJob(RemoteSlave slave) {
		Job jobToReturn = null;
		for (Iterator iter = _jobList.iterator(); iter.hasNext();) {
			Job tempJob = (Job) iter.next();
			if (tempJob.getFile().isDeleted() || tempJob.isDone()) {
				iter.remove();
				continue;
			}
			Collection availableSlaves = null;
			try {
				availableSlaves = tempJob.getFile().getAvailableSlaves();
			} catch (NoAvailableSlaveException e) {
				continue; // can't transfer what isn't online
			}
			if (jobToReturn == null) {
				if (tempJob.getDestinationSlaves().contains(null)) {
					// mirror job
					if (!availableSlaves.contains(slave)) {
						System.out.println("jobToReturn is set to " + tempJob);
						jobToReturn = tempJob;
					} else
						System.out.println("slave already has file " + tempJob);
				} else
					System.out.println(
						"job did not contain null as a destination slave "
							+ tempJob);
			}
			if (tempJob.getDestinationSlaves().contains(slave)) {
				if (availableSlaves.contains(slave)) {
					tempJob.removeDestinationSlave(slave);
					if (tempJob.isDone())
						//removeJob(tempJob);
						iter.remove();
					continue;
				}
				//removeJob(tempJob);
				iter.remove();
				return tempJob;
			}
		}
		if (jobToReturn != null) {
			// outside of jobList iterator, allowed to remove
			removeJob(jobToReturn);
		}
		return jobToReturn;
	}
	public synchronized Job getNextJob(List busySlaves) {
		for (Iterator iter = _jobList.iterator(); iter.hasNext();) {
			Job tempJob = (Job) iter.next();
			if (tempJob.getFile().isDeleted() || tempJob.isDone()) {
				iter.remove();
				continue;
			}
			Collection availableSlaves = null;
			try {
				availableSlaves = tempJob.getFile().getAvailableSlaves();
			} catch (NoAvailableSlaveException e) {
				continue; // can't transfer what isn't online
			}
			for (Iterator iter2 = availableSlaves.iterator();
				iter2.hasNext();
				) {
				if (!busySlaves.contains((RemoteSlave) iter2.next())) {
					return tempJob;
				}
			}
		}
		return null;
	}

//	public void init(ConnectionManager mgr) {
//		_cm = mgr;
//		Collection slaveList;
//		try {
//			slaveList = _cm.getSlaveManager().getAvailableSlaves();
//		} catch (NoAvailableSlaveException e) {
//			return;
//		}
//		for (Iterator iter = slaveList.iterator(); iter.hasNext();) {
//			RemoteSlave rslave = (RemoteSlave) iter.next();
//			JobManagerThread newThread = new JobManagerThread(rslave, this);
//			_threadList.add(newThread);
//			newThread.start();
//		}
//	}
	/**
	 * Returns true if the file was sent okay
	 */
	public boolean processJob() {
		ArrayList busySlavesDown = new ArrayList();
		Job job = null;
		RemoteSlave sourceSlave = null;
		RemoteSlave destSlave = null;
		long time;
		long difference;
		synchronized (this) {
			Collection availableSlaves;
			try {
				availableSlaves = _cm.getSlaveManager().getAvailableSlaves();
			} catch (NoAvailableSlaveException e1) {
				return false;
				// can't transfer with no slaves
			}
			while (!busySlavesDown.containsAll(availableSlaves)) {
				job = getNextJob(busySlavesDown);
				if (job == null) {
					logger.debug(
						"There are no jobs to process for non busy slaves - "
							+ busySlavesDown);
					return false;
				}
				try {
					sourceSlave = _cm.getSlaveManager().getSlaveSelectionManager().getASlaveForJobDownload(job.getFile());
					break; // we have a download slave!
				} catch (NoAvailableSlaveException e) {
					busySlavesDown.addAll(job.getFile().getSlaves());
				}
			}
			if (sourceSlave == null) {
				logger.debug(
					"JobManager was unable to find a suitable job for transfer");
				return false;
			}
			try {
				destSlave = _cm.getSlaveManager().getSlaveSelectionManager().getASlaveForJobUpload(job.getFile());
			} catch (NoAvailableSlaveException e) {
				return false;
			}
			time = System.currentTimeMillis();
			difference = 0;
			removeJob(job);
		}
		// job is not deleted and is out of the jobList, we are ready to
		// process
		logger.info(
			"Sending "
				+ job.getFile().getName()
				+ " from "
				+ sourceSlave.getName()
				+ " to "
				+ destSlave.getName());
		SlaveTransfer slaveTransfer =
			new SlaveTransfer(job.getFile(), sourceSlave, destSlave);
		try {
			logger.debug("Before transfer for " + job.getFile());
			if (!slaveTransfer.transfer(useCRC())) { // crc failed
				logger.debug("After transfer for " + job.getFile());
				try {
					destSlave.getSlave().delete(job.getFile().getPath());
				} catch (IOException e) {
					//couldn't delete it, just carry on
				}
				logger.debug(
					"CRC did not match for "
						+ job.getFile()
						+ " when sending from "
						+ sourceSlave.getName()
						+ " to "
						+ destSlave.getName());
				addJob(job);
				return false;
			}
			logger.debug("After transfer for " + job.getFile());
		} catch (IOException e) {
			logger.debug(
				"Caught IOException in sending "
					+ job.getFile().getName()
					+ " from "
					+ sourceSlave.getName()
					+ " to "
					+ destSlave.getName(),
				e);
			if (!e.getMessage().equals("File exists")) {
				try {
					destSlave.getSlave().delete(job.getFile().getPath());
				} catch (SlaveUnavailableException e3) {
					//couldn't delete it, just carry on
				} catch (IOException e1) {
					//couldn't delete it, just carry on
				}
				addJob(job);
				return false;
			}
			logger.debug(
				"File "
					+ job.getFile()
					+ " was already on the destination slave");
			try {
				if (destSlave.getSlave().checkSum(job.getFile().getPath())
					== job.getFile().getCheckSum()) {
					logger.debug("Accepting file because the crc's match");
					job.getFile().addSlave(destSlave);
				}
				else {
					try {
						destSlave.getSlave().delete(job.getFile().getPath());
					} catch (SlaveUnavailableException e3) {
						//couldn't delete it, just carry on
					} catch (IOException e1) {
						//couldn't delete it, just carry on
					}
				}
			} catch (RemoteException e1) {
				destSlave.handleRemoteException(e1);
				addJob(job);
				return false;
				//				} catch (NoAvailableSlaveException e1) { // extends IOException
				//					addJob(job);
				//					return false;
			} catch (SlaveUnavailableException e2) {
				addJob(job);
				return false;
			} catch (IOException e1) {
				addJob(job);
				return false;
			}
		} catch (Exception e) {
			logger.debug(
				"Error Sending "
					+ job.getFile().getName()
					+ " from "
					+ sourceSlave.getName()
					+ " to "
					+ destSlave.getName(),
				e);
			addJob(job);
			return false;
		}
		difference = System.currentTimeMillis() - time;
		logger.debug(
			"Sent file "
				+ job.getFile().getName()
				+ " to "
				+ destSlave.getName()
				+ " from "
				+ sourceSlave.getName());
		job.addTimeSpent(difference);
		if (job.removeDestinationSlave(destSlave)
			|| job.removeDestinationSlave(null)) {
			if (job.isDone()) {
				logger.debug("Job is finished, removing job " + job.getFile());
			} else
				addJob(job);
			return true;
		}
		logger.debug(
			"Was unable to remove "
				+ destSlave.getName()
				+ " from the destination list for file "
				+ job.getFile());
		// job was naturally removed from the list
		return false;
	}
	public void reload() {
		Properties p = new Properties();
		try {
			p.load(new FileInputStream("conf/jobmanager.conf"));
		} catch (IOException e) {
			throw new FatalException(e);
		}
		_useCRC = FtpConfig.getProperty(p, "useCRC").equals("true");
		_sleepSeconds = 1000*Integer.parseInt(FtpConfig.getProperty(p,"sleepSeconds"));
	}
	public synchronized void removeJob(Job job) {
		_jobList.remove(job);
		Collections.sort(_jobList, new JobComparator());
	}
	/**
	 * @throws NoAvailableSlaveException
	 * @deprecated
	 */
//	public void startAllSlaves() throws NoAvailableSlaveException {
//		stopAllSlaves();
//		_isStopped = false;
//		for (Iterator iter =
//			_cm.getSlaveManager().getAvailableSlaves().iterator();
//			iter.hasNext();
//			) {
//			startSlave((RemoteSlave) iter.next());
//		}
//	}

	/**
	 * @deprecated
	 */
//	public void startSlave(RemoteSlave rslave) {
//		if (_isStopped)
//			return;
//		JobManagerThread newThread = new JobManagerThread(rslave, this);
//		_threadList.add(newThread);
//		newThread.start();
//		logger.debug("Started slave thread for " + rslave.getName());
//	}
	/**
	 * @deprecated
	 */
//	public void stopAllSlaves() {
//		_isStopped = true;
//		//synchronized (_threadList) { // does not need to be synchronized if
//		// only one thread handles SlaveEvents
//		for (Iterator iter = _threadList.iterator(); iter.hasNext();) {
//			JobManagerThread tempThread = (JobManagerThread) iter.next();
//			tempThread.stopme();
//			iter.remove();
//			logger.debug(
//				"Stopped slave thread for " + tempThread.getRSlave().getName());
//		}
//	}
	/**
	 * @deprecated
	 */
//	public void stopSlave(RemoteSlave rslave) {
//		//synchronized (_threadList) { // does not need to be synchronized if
//		// only one thread handles SlaveEvents
//		for (Iterator iter = _threadList.iterator(); iter.hasNext();) {
//			JobManagerThread tempThread = (JobManagerThread) iter.next();
//			if (tempThread.getRSlave() == rslave) {
//				tempThread.stopme();
//				iter.remove();
//				logger.debug(
//					"Sending stop signal to "
//						+ tempThread.getRSlave().getName());
//				return;
//			}
//		}
//	}
	private boolean useCRC() {
		return _useCRC;
	}

	public void run() {
		while(true) {
			if (isStopped()) {
				logger.debug("Stopping JobTransferStarter thread");
				return;
			}
			new JobTransferThread(this).start();
			try {
				Thread.sleep(_sleepSeconds);
			} catch (InterruptedException e) {
			}
		}
	}

}
