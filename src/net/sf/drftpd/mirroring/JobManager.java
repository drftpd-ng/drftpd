/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 * 
 * DrFTPD is free software; you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 * 
 * DrFTPD is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * DrFTPD; if not, write to the Free Software Foundation, Inc., 59 Temple Place,
 * Suite 330, Boston, MA 02111-1307 USA
 */
package net.sf.drftpd.mirroring;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import net.sf.drftpd.FatalException;
import net.sf.drftpd.FileExistsException;
import net.sf.drftpd.NoAvailableSlaveException;
import net.sf.drftpd.SlaveUnavailableException;
import net.sf.drftpd.master.ConnectionManager;
import net.sf.drftpd.master.RemoteSlave;
import net.sf.drftpd.master.config.FtpConfig;

import org.apache.log4j.Logger;
/**
 * @author zubov
 * @version $Id: JobManager.java,v 1.52 2004/07/09 06:11:57 zubov Exp $
 */
public class JobManager implements Runnable {
	private static final Logger logger = Logger.getLogger(JobManager.class);
	private ConnectionManager _cm;
	private boolean _isStopped = false;
	private ArrayList _jobList = new ArrayList();
	private int _sleepSeconds;
	private boolean _useCRC;
	private Thread thread;
	/**
	 * Keeps track of all jobs and controls them
	 */
	public JobManager(ConnectionManager cm) throws IOException {
		_cm = cm;
		reload();
	}
	protected JobManager(ConnectionManager cm, Properties p) {
		_cm = cm;
		reload(p);
	}
	public synchronized void addJob(Job job) {
		Collection slaves = job.getFile().getSlaves();
		for (Iterator iter = slaves.iterator(); iter.hasNext();) {
			RemoteSlave slave = (RemoteSlave) iter.next();
			if (job.getDestinationSlaves().contains(slave)) {
				job.sentToSlave(slave);
			}
		}
		if (job.isDone())
			return;
		_jobList.add(job);
		Collections.sort(_jobList, new JobComparator());
	}
	/**
	 * Gets all jobs.
	 */
	public synchronized List getAllJobs() {
		return Collections.unmodifiableList(_jobList);
	}
	public synchronized Job getNextJob(Set busySlaves, Set skipJobs) {
		for (Iterator iter = _jobList.iterator(); iter.hasNext();) {
			Job tempJob = (Job) iter.next();
			if (tempJob.isDone()) {
				iter.remove();
				continue;
			}
			if (skipJobs.contains(tempJob))
				continue;
			Collection availableSlaves = null;
			try {
				availableSlaves = tempJob.getFile().getAvailableSlaves();
			} catch (NoAvailableSlaveException e) {
				if (tempJob.getFile().isDeleted()) {
					tempJob.setDone();
					iter.remove();
				}
				continue; // can't transfer what isn't online
			}
			if (!busySlaves.containsAll(availableSlaves)) {
				return tempJob;
			}
		}
		return null;
	}
	public boolean isStopped() {
		return _isStopped;
	}
	/**
	 * Returns true if the file was sent okay
	 */
	public boolean processJob() {
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
			Set busySlavesDown = new HashSet();
			Set skipJobs = new HashSet();
			while (!busySlavesDown.containsAll(availableSlaves)) {
				job = getNextJob(busySlavesDown, skipJobs);
				if (job == null) {
					return false;
				}
				//logger.debug("looking up slave for job " + job);
				try {
					sourceSlave = _cm.getSlaveManager()
							.getSlaveSelectionManager()
							.getASlaveForJobDownload(job);
				} catch (NoAvailableSlaveException e) {
					try {
						busySlavesDown.addAll(job.getFile()
								.getAvailableSlaves());
					} catch (NoAvailableSlaveException e2) {
					}
					continue;
				}
				if (sourceSlave == null) {
					logger.debug("JobManager was unable to find a suitable job for transfer");
					return false;
				}
				try {
					destSlave = _cm.getSlaveManager()
							.getSlaveSelectionManager().getASlaveForJobUpload(
									job);
					break; // we have a source slave and a destination slave,
						   // transfer!
				} catch (NoAvailableSlaveException e) {
					// job was ready to be sent, but it had no slave that was
					// ready to accept it
					skipJobs.add(job);
					continue;
				}
			}
			if (destSlave == null) {
				logger.debug("destSlave is null, all destination slaves are busy"
								+ job);
				return false;
			}
			logger.debug("ready to transfer " + job + " from "
					+ sourceSlave.getName() + " to " + destSlave.getName());
			time = System.currentTimeMillis();
			difference = 0;
			removeJob(job);
		}
		// job is not deleted and is out of the jobList, we are ready to
		// process
		logger.info("Sending " + job.getFile().getName() + " from "
				+ sourceSlave.getName() + " to " + destSlave.getName());
		SlaveTransfer slaveTransfer = new SlaveTransfer(job.getFile(),
				sourceSlave, destSlave);
		try {
			if (!slaveTransfer.transfer(useCRC())) { // crc failed
				try {
					destSlave.delete(job.getFile().getPath());
				} catch (IOException e) {
					// queued for deletion
				}
				logger.debug("CRC did not match for " + job.getFile()
						+ " when sending from " + sourceSlave.getName()
						+ " to " + destSlave.getName());
				addJob(job);
				return false;
			}
		} catch (FileExistsException e) {
			logger.debug("Caught FileExistsException in sending "
					+ job.getFile().getName() + " from "
					+ sourceSlave.getName() + " to " + destSlave.getName(), e);
			try {
				if (destSlave.getSlave().checkSum(job.getFile().getPath()) == job
						.getFile().getCheckSum()) {
					logger.debug("Accepting file because the crc's match");
				} else {
					try {
						destSlave.delete(job.getFile().getPath());
					} catch (IOException e1) {
						// queued for deletion
					}
					addJob(job);
					return false;
				}
			} catch (RemoteException e1) {
				destSlave.handleRemoteException(e1);
			} catch (NoAvailableSlaveException e1) {
				addJob(job);
				return false;
			} catch (SlaveUnavailableException e2) {
				addJob(job);
				return false;
			} catch (IOException e1) {
				addJob(job);
				return false;
			}
		} catch (FileNotFoundException e) {
			logger.debug("Caught FileNotFoundException in sending "
					+ job.getFile().getName() + " from "
					+ sourceSlave.getName() + " to " + destSlave.getName(), e);
			job.getFile().removeSlave(sourceSlave);
			addJob(job);
			return false;
		} catch (DestinationSlaveException e) {
			destSlave.setOffline(e.getMessage());
			addJob(job);
			return false;
		} catch (SourceSlaveException e) {
			destSlave.setOffline(e.getMessage());
			addJob(job);
			return false;
		}
		difference = System.currentTimeMillis() - time;
		logger.debug("Sent file " + job.getFile().getName() + " to "
				+ destSlave.getName() + " from " + sourceSlave.getName());
		job.addTimeSpent(difference);
		job.sentToSlave(destSlave);
		if (job.isDone()) {
			logger.debug("Job is finished, removing job " + job.getFile());
		} else {
			addJob(job);
		}
		return true;
	}
	public void reload() {
		Properties p = new Properties();
		try {
			p.load(new FileInputStream("conf/jobmanager.conf"));
		} catch (IOException e) {
			throw new FatalException(e);
		}
		reload(p);
	}
	protected void reload(Properties p) {
		_useCRC = p.getProperty("useCRC", "true").equals("true");
		_sleepSeconds = 1000 * Integer.parseInt(FtpConfig.getProperty(p,
				"sleepSeconds"));
	}
	public synchronized void removeJob(Job job) {
		_jobList.remove(job);
		Collections.sort(_jobList, new JobComparator());
	}
	public void run() {
		while (true) {
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
	public void startJobs() {
		if (thread != null) {
			stopJobs();
			thread.interrupt();
			while (thread.isAlive()) {
				logger.debug("thread is still alive");
				Thread.yield();
			}
		}
		_isStopped = false;
		logger.debug("Starting JobTransferStarter thread");
		thread = new Thread(this, "JobTransferStarter");
		thread.start();
	}
	public void stopJob(Job job) {
		removeJob(job);
		job.setDone();
	}
	public void stopJobs() {
		_isStopped = true;
	}
	private boolean useCRC() {
		return _useCRC;
	}
}