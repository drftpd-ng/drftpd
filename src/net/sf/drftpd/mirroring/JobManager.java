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
import java.util.List;
import java.util.Properties;

import net.sf.drftpd.FatalException;
import net.sf.drftpd.NoAvailableSlaveException;
import net.sf.drftpd.event.Event;
import net.sf.drftpd.event.FtpListener;
import net.sf.drftpd.event.SlaveEvent;
import net.sf.drftpd.master.ConnectionManager;
import net.sf.drftpd.master.RemoteSlave;
import net.sf.drftpd.master.config.FtpConfig;
import net.sf.drftpd.remotefile.LinkedRemoteFile;

import org.apache.log4j.Logger;

/**
 * @author zubov
 * @version $Id: JobManager.java,v 1.26 2004/02/11 22:33:17 zubov Exp $
 */
public class JobManager implements FtpListener {
	private static final Logger logger = Logger.getLogger(JobManager.class);
	private ConnectionManager _cm;
	private boolean _isStopped = false;
	private ArrayList _jobList = new ArrayList();
	private int _maxTransfers;
	private long _maxWait;
	private ArrayList _threadList = new ArrayList();
	private boolean _useCRC;

	/**
	 * Keeps track of all jobs and controls them
	 */
	public JobManager() throws IOException {
		reload();
	}

	public void actionPerformed(Event event) {
		if (event.getCommand().equals("RELOAD")) {
			reload();
			return;
		}
		if (!(event instanceof SlaveEvent))
			return;
		SlaveEvent slaveEvent = (SlaveEvent) event;
		if (slaveEvent.getCommand().equals("DELSLAVE")) {
			stopSlave(slaveEvent.getRSlave());
		} else if (slaveEvent.getCommand().equals("ADDSLAVE")) {
			startSlave(slaveEvent.getRSlave());
		}
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
	 * @return All jobs.
	 */
	public List getAllJobs() {
		return Collections.unmodifiableList(_jobList);
	}

	/**
	 * Get all jobs for a specific LinkedRemoteFile.
	 * @param lrf The LinkedRemoteFile to return all jobs for.
	 * @return A <code>java.util.List</code> of all jobs for the specific LinkedRemoteFile
	 */
	public synchronized List getAllJobs(LinkedRemoteFile lrf) {
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
	 * @param source The source of all objects to get.
	 * @return List of all <code>Job</code>s
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
	 */
	public synchronized Job getNextJob(RemoteSlave slave) {
		Job jobToReturn = null;
		for (Iterator iter = _jobList.iterator(); iter.hasNext();) {
			Job tempJob = (Job) iter.next();
			if (tempJob.getFile().isDeleted() || tempJob.isDone()) {
				iter.remove();
				continue;
			}
			if (tempJob.getDestinationSlaves().contains(null)) { // mirror job
				try {
					if (jobToReturn == null) {
						if (tempJob.getFile() == null) {
							logger.error("tempJob.getFile() == null");
							continue;
						}
						if (!tempJob
							.getFile()
							.getAvailableSlaves()
							.contains(slave)) {
							jobToReturn = tempJob;
						}
					}
				} catch (NoAvailableSlaveException e) {
					logger.debug(
						"NoAvailableSlaveException for mirror algorithm - "
							+ slave.getName(),
						e);
					continue;
					// can't transfer it, so don't set jobToReturn
				}
				continue;
			}
			if (tempJob.getDestinationSlaves().contains(slave)) {
				//				logger.debug(
				//					"an Archive job is being returned - " + slave.getName());
				removeJob(tempJob);
				return tempJob;
			}
		}
		if (jobToReturn != null) {
			removeJob(jobToReturn);
		}
		return jobToReturn;
	}

	public void init(ConnectionManager mgr) {
		_cm = mgr;
		Collection slaveList;
		try {
			slaveList = _cm.getSlaveManager().getAvailableSlaves();
		} catch (NoAvailableSlaveException e) {
			return;
		}

		for (Iterator iter = slaveList.iterator(); iter.hasNext();) {
			RemoteSlave rslave = (RemoteSlave) iter.next();
			JobManagerThread newThread = new JobManagerThread(rslave, this);
			_threadList.add(newThread);
			newThread.start();
		}
	}
	/**
	 * Returns true if the slave could possibly have another file to immediately transfer
	 */
	public boolean processJob(RemoteSlave slave) {
		Job temp;
		temp = getNextJob(slave);
		if (temp == null) { // nothing to process for this slave
			return false;
		}
		// job is not deleted and is out of the jobList, we are ready to process
		logger.info(
			"Sending " + temp.getFile().getName() + " to " + slave.getName());
		long time = System.currentTimeMillis();
		long difference = 0;
		RemoteSlave sourceSlave = null;
		try {
			try {
				sourceSlave = temp.getFile().getASlaveForDownload();
			} catch (NoAvailableSlaveException e) {
				logger.debug(
					"Could not send the file "
						+ temp.getFile()
						+ " because getASlaveForDownload returned NoAvailableSlaveException",
					e);
				addJob(temp);
				return false;
			}
			if (System.currentTimeMillis() - temp.getTimeCreated()
				< _maxWait) {
				// check to see if we should transfer it
				if (sourceSlave.getStatus().getTransfersSending()
					> _maxTransfers
					|| slave.getStatus().getTransfersReceiving()
						> _maxTransfers) {
					logger.debug(
						"One of the slaves, "
							+ sourceSlave.getName()
							+ " or "
							+ slave.getName()
							+ ", has too many transfers");
					addJob(temp);
					return false;
				}
			} // job has been in the queue too long or 
			//the sourceSlave and destSlave are both idle, send it now!
			else {
				logger.debug(
					temp.getFile()
						+ " has been in the queue too long, sending now");
			}
			SlaveTransfer slaveTransfer =
				new SlaveTransfer(temp.getFile(), sourceSlave, slave);
			if (!slaveTransfer.transfer(useCRC())) { // crc failed
				try {
					slave.getSlave().delete(temp.getFile().getPath());
				} catch (IOException e) {
					//couldn't delete it, just carry on
				}
				logger.debug(
					"CRC did not match for "
						+ temp.getFile()
						+ " when sending from "
						+ sourceSlave.getName()
						+ " to "
						+ slave.getName());
				addJob(temp);
				return false;
			}
		} catch (IOException e) {
			if (!e.getMessage().equals("File exists")) {
				logger.debug(
					"Uncaught IOException in sending "
						+ temp.getFile().getName()
						+ " from "
						+ sourceSlave.getName()
						+ " to "
						+ slave.getName(),
					e);
				addJob(temp);
				return false;
				// with this code we'll try to send it again
			} else
				logger.debug(
					"File "
						+ temp.getFile()
						+ " was sent okay because it was already on the destination slave");
		} catch (Exception e) {
			logger.debug(
				"Error Sending "
					+ temp.getFile().getName()
					+ " from "
					+ sourceSlave.getName()
					+ " to "
					+ slave.getName(),
				e);
			addJob(temp);
			return false;
			// with this code we'll try to send it again
		}
		difference = System.currentTimeMillis() - time;
		logger.debug(
			"Sent file "
				+ temp.getFile().getName()
				+ " to "
				+ slave.getName()
				+ " from "
				+ sourceSlave.getName());
		temp.addTimeSpent(difference);
		if (temp.removeDestinationSlave(slave)) {
			// if it's there, remove it from the destinationList
			if (!temp.isDone()) {
				addJob(temp);
			}
			return true;
		} else {
			if (temp.removeDestinationSlave(null)) {
				if (!temp.isDone()) {
					addJob(temp);
				}
				return true;
			}
			logger.debug(
				"Was unable to remove "
					+ slave.getName()
					+ " from the destination list for file "
					+ temp.getFile());
			// job was naturally removed from the list
			return false;

		}
	}

	private void reload() {
		Properties jobManCfg = new Properties();
		try {
			jobManCfg.load(new FileInputStream("conf/jobmanager.conf"));
		} catch (IOException e) {
			throw new FatalException(e);
		}
		_useCRC = FtpConfig.getProperty(jobManCfg, "useCRC").equals("true");
		_maxWait =
			60000 * Long.parseLong(FtpConfig.getProperty(jobManCfg, "maxWait"));
		_maxTransfers =
			Integer.parseInt(FtpConfig.getProperty(jobManCfg, "maxTransfers"));
	}

	public synchronized void removeJob(Job job) {
		_jobList.remove(job);
		Collections.sort(_jobList, new JobComparator());
	}

	public void startAllSlaves() throws NoAvailableSlaveException {
		stopAllSlaves();
		_isStopped = false;
		for (Iterator iter =
			_cm.getSlaveManager().getAvailableSlaves().iterator();
			iter.hasNext();
			) {
			startSlave((RemoteSlave) iter.next());
		}
	}

	public void startSlave(RemoteSlave rslave) {
		if (_isStopped)
			return;
		JobManagerThread newThread = new JobManagerThread(rslave, this);
		_threadList.add(newThread);
		newThread.start();
		logger.debug("Started slave thread for " + rslave.getName());
	}
	public void stopAllSlaves() {
		_isStopped = true;
		//synchronized (_threadList) { // does not need to be synchronized if only one thread handles SlaveEvents 
		for (Iterator iter = _threadList.iterator(); iter.hasNext();) {
			JobManagerThread tempThread = (JobManagerThread) iter.next();
			tempThread.stopme();
			iter.remove();
			logger.debug(
				"Stopped slave thread for " + tempThread.getRSlave().getName());
		}
	}

	public void stopSlave(RemoteSlave rslave) {
		//synchronized (_threadList) { // does not need to be synchronized if only one thread handles SlaveEvents 
		for (Iterator iter = _threadList.iterator(); iter.hasNext();) {
			JobManagerThread tempThread = (JobManagerThread) iter.next();
			if (tempThread.getRSlave() == rslave) {
				tempThread.stopme();
				iter.remove();
				logger.debug(
					"Stopped slave thread for "
						+ tempThread.getRSlave().getName());
				return;
			}
		}
	}

	public void unload() {

	}

	private boolean useCRC() {
		return _useCRC;
	}

}
