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
import java.io.FileNotFoundException;
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
 * @version $Id: JobManager.java,v 1.24 2004/02/10 00:03:14 mog Exp $
 */
public class JobManager implements FtpListener {
	private static final Logger logger = Logger.getLogger(JobManager.class);
	private ConnectionManager _cm;
	private boolean _isStopped = false;
	private ArrayList _jobList = new ArrayList();
	private ArrayList _slaveSendingList = new ArrayList();
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
			if (tempJob.getDestinationSlaves().contains(null)) { // mirror job
				try {
					if (jobToReturn == null) {
						if (tempJob.getFile() == null) {
							logger.error("tempJob.getFile() == null");
							continue;
						}
						if (tempJob.getFile().getAvailableSlaves() == null) {
							logger.debug(
								"tempJob.getFile().getAvailableSlaves() == null, can't transfer the file from nowhere");
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
					if (tempJob.getFile().isDeleted()) {
						iter.remove();
						logger.debug(
							"Job "
								+ tempJob
								+ " was removed from the list because it is deleted");
						continue;
					}
					logger.debug(
						"NoAvailableSlaveException for mirror algorithm - "
							+ slave.getName(),
						e);
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
		while (true) {
			temp = getNextJob(slave);

			if (temp == null) { // nothing to process for this slave
				return false;
			}
			if (!temp.getFile().isDeleted()) {
				// file is not deleted, process it now
				break;
			}
			// job is already out of the list and isDeleted(), just continue
		}
		if (temp.getFile().getSlaves().contains(slave)) {
			if (temp.removeDestinationSlave(slave)) {
				// if it's already there, remove it from the destinationList
				if (temp.getDestinationSlaves().size() > 0) {
					addJob(temp);
				} else {
					temp.setDone();
				}
				return true;
			}
			if (temp.removeDestinationSlave(null)) {
				if (temp.getDestinationSlaves().size() > 0) {
					addJob(temp);
				} else {
					temp.setDone();
				}
				return true;
			}
			logger.debug(
				"Unable to remove "
					+ slave.getName()
					+ " from the destination list of the job "
					+ temp);
			return false; // should not get here
		}
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
			while (true) {
				synchronized (_slaveSendingList) {
					if (!_slaveSendingList.contains(sourceSlave))
						break;
					_slaveSendingList.add(sourceSlave);
				}
				Thread.sleep(20000);
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
			_slaveSendingList.remove(sourceSlave);
			if (!e.getMessage().equals("File exists")) {
				logger.debug(
					"Uncaught IOException in sending "
						+ temp.getFile().getName()
						+ " from "
						+ sourceSlave.getName()
						+ " to "
						+ slave.getName(),
					e);
			} else
				logger.debug(
					"File "
						+ temp.getFile()
						+ " was sent okay because it was already on the destination slave");
		} catch (Exception e) {
			_slaveSendingList.remove(sourceSlave);
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
		}
		_slaveSendingList.remove(sourceSlave);
		difference = System.currentTimeMillis() - time;
		logger.debug(
			"Sent file "
				+ temp.getFile().getName()
				+ " to "
				+ slave.getName()
				+ " from "
				+ sourceSlave.getName());
		temp.addTimeSpent(difference);
		if (!temp.removeDestinationSlave(slave)) {
			if (!temp.removeDestinationSlave(null))
				logger.debug(
					"Not able to remove slave "
						+ slave.getName()
						+ " or a null reference from job "
						+ temp);
		}
		if (temp.getDestinationSlaves().size() > 0) {
			logger.debug("Adding job " + temp + " back into the jobList");
			addJob(temp); // job still has more places to transfer
		} else {
			temp.setDone();
			//			logger.debug("Setting job " + temp + " to be done");
		}
		return true;
	}

	private void reload() {
		Properties ircCfg = new Properties();
		try {
			ircCfg.load(new FileInputStream("conf/jobmanager.conf"));
		} catch (IOException e) {
			throw new FatalException(e);
		}
		_useCRC = FtpConfig.getProperty(ircCfg, "useCRC").equals("true");
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
