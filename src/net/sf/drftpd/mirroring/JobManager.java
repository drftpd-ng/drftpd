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
import net.sf.drftpd.Bytes;
import net.sf.drftpd.FatalException;
import net.sf.drftpd.NoAvailableSlaveException;
import net.sf.drftpd.SlaveUnavailableException;
import net.sf.drftpd.event.Event;
import net.sf.drftpd.event.FtpListener;
import net.sf.drftpd.event.SlaveEvent;
import net.sf.drftpd.master.ConnectionManager;
import net.sf.drftpd.master.RemoteSlave;
import net.sf.drftpd.master.config.FtpConfig;
import net.sf.drftpd.remotefile.LinkedRemoteFileInterface;
import net.sf.drftpd.slave.SlaveStatus;
import org.apache.log4j.Logger;
/**
 * @author zubov
 * @version $Id: JobManager.java,v 1.32 2004/03/01 00:21:08 mog Exp $
 */
public class JobManager implements FtpListener {
	private static final Logger logger = Logger.getLogger(JobManager.class);
	private ConnectionManager _cm;
	private boolean _isStopped = false;
	private ArrayList _jobList = new ArrayList();
	private long _maxBandwidth;
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
				if (tempJob.getDestinationSlaves().contains(null)) { // mirror job
					if (!availableSlaves.contains(slave)) {
						System.out.println("jobToReturn is set to " + tempJob);
						jobToReturn = tempJob;
					}
					else System.out.println("slave already has file " + tempJob);
				} else System.out.println("job did not contain null as a destination slave " + tempJob);
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
	 * Returns true if the slave could possibly have another file to
	 * immediately transfer
	 */
	public boolean processJob(RemoteSlave slave) {
		Job job = getNextJob(slave);
		if (job == null) { // nothing to process for this slave
			logger.debug("Nothing to process for slave " + slave.getName());
			return false;
		}
		// job is not deleted and is out of the jobList, we are ready to
		// process
		long time = System.currentTimeMillis();
		long difference = 0;
		RemoteSlave sourceSlave = null;
		try {
			sourceSlave = _cm.getSlaveManager().getSlaveSelectionManager("down").getASlaveForJobDownload(job, slave);
			//sourceSlave = job.getFile().getASlaveForDownload();
		} catch (NoAvailableSlaveException e) {
			logger
					.debug(
							"Could not send the file "
									+ job.getFile()
									+ " because getASlaveForDownload returned NoAvailableSlaveException",
							e);
			addJob(job);
			return false;
		}
		if (System.currentTimeMillis() - job.getTimeCreated() < _maxWait) {
			// check to see if we should transfer it since it is under maxWait
			// time
			SlaveStatus sourceStatus;
			SlaveStatus destStatus;
			try {
				try {
				sourceStatus = sourceSlave.getStatus();
				} catch(RemoteException e) {
					sourceSlave.handleRemoteException(e);
					addJob(job);
					return true; // try and send another job immediately
				}
				try {
					destStatus = slave.getStatus();
				} catch(RemoteException e) {
					slave.handleRemoteException(e);
					addJob(job);
					return false;
				}
			} catch (SlaveUnavailableException e) {
				// slave went offline after using getNextJob()
				addJob(job);
				return true; // try and send another job immediately
			}
			int sendThroughput = sourceStatus.getThroughputSending();
			if (sendThroughput > _maxBandwidth) {
				logger.debug(sourceSlave.getName()
						+ " is using too much bandwidth (" + sendThroughput
						+ ") to send " + job.getFile());
				addJob(job);
				return false;
			}
			int receiveThroughput = destStatus.getThroughputReceiving();
			if (receiveThroughput > _maxBandwidth) {
				logger.debug(slave.getName() + " is using too much bandwidth ("
						+ receiveThroughput + ") to send " + job.getFile());
				addJob(job);
				return false;
			}
			int sendTransfers = sourceStatus.getTransfersSending();
			if (sendTransfers > _maxTransfers) {
				logger.debug(sourceSlave.getName()
						+ " has too many transfers (" + sendTransfers
						+ ") to send " + job.getFile());
				addJob(job);
				return false;
			}
			int receiveTransfers = destStatus.getTransfersReceiving();
			if (receiveTransfers > _maxTransfers) {
				logger.debug(slave.getName() + " has too many transfers ("
						+ receiveTransfers + ") to send " + job.getFile());
				addJob(job);
				return false;
			}
		} // job has been in the queue too long or
		//the sourceSlave and destSlave are both <= maxTransfers && <=
		// maxBandwidth
		//send it now!
		logger.info("Sending " + job.getFile().getName() + " to "
				+ slave.getName());
		SlaveTransfer slaveTransfer = new SlaveTransfer(job.getFile(),
				sourceSlave, slave);
		try {
			logger.debug("Before transfer for " + job.getFile());
			if (!slaveTransfer.transfer(useCRC())) { // crc failed
				logger.debug("After transfer for " + job.getFile());
				try {
					slave.getSlave().delete(job.getFile().getPath());
				} catch (IOException e) {
					//couldn't delete it, just carry on
				}
				logger.debug("CRC did not match for " + job.getFile()
						+ " when sending from " + sourceSlave.getName()
						+ " to " + slave.getName());
				addJob(job);
				return false;
			}
			logger.debug("After transfer for " + job.getFile());
		} catch (IOException e) {
			if (!e.getMessage().equals("File exists")) {
				logger.debug("Uncaught IOException in sending "
						+ job.getFile().getName() + " from "
						+ sourceSlave.getName() + " to " + slave.getName(), e);
				addJob(job);
				return false;
				// with this code we'll try to send it again
			} else
				logger
						.debug("File "
								+ job.getFile()
								+ " was sent okay because it was already on the destination slave");
		} catch (Exception e) {
			logger.debug("Error Sending " + job.getFile().getName() + " from "
					+ sourceSlave.getName() + " to " + slave.getName(), e);
			addJob(job);
			return false;
			// with this code we'll try to send it again
		}
		difference = System.currentTimeMillis() - time;
		logger.debug("Sent file " + job.getFile().getName() + " to "
				+ slave.getName() + " from " + sourceSlave.getName());
		job.addTimeSpent(difference);
		if (job.removeDestinationSlave(slave) || job.removeDestinationSlave(null)) {
			if (job.isDone()) {
				logger.debug("Job is finished, removing job " + job.getFile());
			} else
				addJob(job);
			return true;
		}
		logger.debug("Was unable to remove " + slave.getName()
				+ " from the destination list for file " + job.getFile());
		// job was naturally removed from the list
		return false;
	}
	private void reload() {
		Properties jobManCfg = new Properties();
		try {
			jobManCfg.load(new FileInputStream("conf/jobmanager.conf"));
		} catch (IOException e) {
			throw new FatalException(e);
		}
		_useCRC = FtpConfig.getProperty(jobManCfg, "useCRC").equals("true");
		_maxWait = 60000 * Long.parseLong(FtpConfig.getProperty(jobManCfg,
				"maxWait"));
		_maxTransfers = Integer.parseInt(FtpConfig.getProperty(jobManCfg,
				"maxTransfers"));
		_maxBandwidth = Bytes.parseBytes(FtpConfig.getProperty(jobManCfg,
				"maxBandwidth"));
	}
	public synchronized void removeJob(Job job) {
		_jobList.remove(job);
		Collections.sort(_jobList, new JobComparator());
	}
	public void startAllSlaves() throws NoAvailableSlaveException {
		stopAllSlaves();
		_isStopped = false;
		for (Iterator iter = _cm.getSlaveManager().getAvailableSlaves()
				.iterator(); iter.hasNext();) {
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
		//synchronized (_threadList) { // does not need to be synchronized if
		// only one thread handles SlaveEvents
		for (Iterator iter = _threadList.iterator(); iter.hasNext();) {
			JobManagerThread tempThread = (JobManagerThread) iter.next();
			tempThread.stopme();
			iter.remove();
			logger.debug("Stopped slave thread for "
					+ tempThread.getRSlave().getName());
		}
	}
	public void stopSlave(RemoteSlave rslave) {
		//synchronized (_threadList) { // does not need to be synchronized if
		// only one thread handles SlaveEvents
		for (Iterator iter = _threadList.iterator(); iter.hasNext();) {
			JobManagerThread tempThread = (JobManagerThread) iter.next();
			if (tempThread.getRSlave() == rslave) {
				tempThread.stopme();
				iter.remove();
				logger.debug("Sending stop signal to "
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
