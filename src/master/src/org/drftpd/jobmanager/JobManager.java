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
package org.drftpd.jobmanager;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Properties;
import java.util.Set;
import java.util.TimerTask;
import java.util.TreeSet;


import org.apache.log4j.Logger;
import org.drftpd.GlobalContext;
import org.drftpd.PropertyHelper;
import org.drftpd.exceptions.NoAvailableSlaveException;
import org.drftpd.master.RemoteSlave;

/**
 * @author zubov
 * @version $Id$
 */
public class JobManager {
	private static final Logger logger = Logger.getLogger(JobManager.class);

	private boolean _isStopped = false;

	private Set<Job> _queuedJobSet;

	private boolean _useCRC;
	
	private boolean _useSSL;

	private long _sleepSeconds;

	private GlobalContext _gctx;

	private TimerTask _runJob = null;

	/**
	 * Keeps track of all jobs and controls them
	 */
	public JobManager(GlobalContext gctx) {
		_gctx = gctx;
		_queuedJobSet = new TreeSet<Job>(new JobComparator());
		reload();
	}

	public synchronized void addJobsToQueue(Collection<Job> jobs) {
		ArrayList<Job> jobs2 = new ArrayList<Job>(jobs);
		for (Iterator jobiter = jobs2.iterator(); jobiter.hasNext();) {
			Job job = (Job) jobiter.next();
			Collection<RemoteSlave> slaves;
			try {
				slaves = job.getFile().getSlaves();
			} catch (FileNotFoundException e) {
				job.abort();
				jobiter.remove();
				continue;
			}

			for (Iterator<RemoteSlave> iter = slaves.iterator(); iter.hasNext();) {
				RemoteSlave slave = iter.next();

				if (job.getDestinationSlaves().contains(slave)) {
					job.sentToSlave(slave);
				}
			}
			if (job.isDone()) {
				jobiter.remove();
			}
		}
		_queuedJobSet.addAll(jobs2);
	}

	public synchronized void addJobToQueue(Job job) {
		addJobsToQueue(Collections.singletonList(job));
	}

	/**
	 * Gets all jobs.
	 */
	public synchronized Set<Job> getAllJobsFromQueue() {
		return Collections.unmodifiableSet(_queuedJobSet);
	}

	public synchronized Job getNextJob(Set<RemoteSlave> busySlaves, Set skipJobs) {
		for (Iterator iter = _queuedJobSet.iterator(); iter.hasNext();) {
			Job tempJob = (Job) iter.next();

			if (tempJob.isDone()) {
				iter.remove();

				continue;
			}

			if (tempJob.isTransferring()) {
				continue;
			}

			if (skipJobs.contains(tempJob)) {
				continue;
			}

			Collection availableSlaves = null;

			try {
				availableSlaves = tempJob.getFile().getAvailableSlaves();
			} catch (NoAvailableSlaveException e) {
				continue; // can't transfer what isn't online
			} catch (FileNotFoundException e) {
				tempJob.abort();
				iter.remove();
				continue;
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

	public void processJob() {
		Job job = null;
		RemoteSlave sourceSlave = null;
		RemoteSlave destSlave = null;

		Collection<RemoteSlave> availableSlaves;
		try {
			availableSlaves = getGlobalContext().getSlaveManager()
					.getAvailableSlaves();
		} catch (NoAvailableSlaveException e1) {
			return; // can't transfer with no slaves
		}

		Set<RemoteSlave> busySlavesDown = new HashSet<RemoteSlave>();
		Set<Job> skipJobs = new HashSet<Job>();

		synchronized (this) {
			while (!busySlavesDown.containsAll(availableSlaves)) {
				job = getNextJob(busySlavesDown, skipJobs);

				if (job == null) {
					return;
				}

				// logger.debug("looking up slave for job " + job);
				try {
					sourceSlave = getGlobalContext().getSlaveSelectionManager()
							.getASlaveForJobDownload(job);
				} catch (NoAvailableSlaveException e) {
					try {
						busySlavesDown.addAll(job.getFile().getSlaves());
					} catch (FileNotFoundException e1) {
						// can't transfer
						return;
					}
					continue;
				} catch (FileNotFoundException e) {
					// can't transfer
					return;
				}

				if (sourceSlave == null) {
					logger.debug("Unable to find a suitable job for transfer");
					return;
				}
				try {
					availableSlaves.removeAll(job.getFile().getSlaves());
					destSlave = getGlobalContext().getSlaveSelectionManager()
							.getASlaveForJobUpload(job, sourceSlave);

					break; // we have a source slave and a destination
					// slave,

					// transfer!
				} catch (NoAvailableSlaveException e) {
					// job was ready to be sent, but it had no slave that was
					// ready to accept it
					skipJobs.add(job);

					continue;
				} catch (FileNotFoundException e) {
					// can't transfer
					return;
				}
			}
			// sourceSlave will always be null if destSlave is null
			if (destSlave == null /* || sourceSlave == null */) {
				// all slaves are offline or busy
				return;
			}
		}

		// file is not deleted and is available, we are ready to process

		try {
			job.transfer(useCRC(), useSecureTransfers(), sourceSlave, destSlave);
		} catch (FileNotFoundException e) {
			// file is deleted, hah! stupid race conditions
			return;
		}
		if (job.isDone()) {
			logger.debug("Job is finished, removing job " + job.getFile());
			removeJobFromQueue(job);
		}
	}

	private GlobalContext getGlobalContext() {
		return _gctx;
	}

	public void reload() {
		Properties p = new Properties();
		FileInputStream fis = null;

		try {
			fis = new FileInputStream("conf/jobmanager.conf");
			p.load(fis);
		} catch (IOException e) {
			logger.warn("conf/jobmanager.conf missing, using default values");
			// defaults
			_useCRC = true;
			_sleepSeconds = 10000; // 10 seconds
			return;
		} finally {
			if (fis != null) {
				try {
					fis.close();
				} catch (IOException e) {
					logger
							.error(
									"Could not close the FileInputStream of conf/jobmanager.conf",
									e);
				}
				fis = null;
			}
		}
		_useCRC = p.getProperty("useCRC", "true").equals("true");
		_useSSL = p.getProperty("useSSLTransfers", "true").equals("true"); 
		_sleepSeconds = 1000 * Long.parseLong(PropertyHelper.getProperty(p,
				"sleepSeconds"));
		if (_runJob != null) {
			_runJob.cancel();
		}
		_runJob = new TimerTask() {
			public void run() {
				if (_isStopped) {
					return;
				}
				new JobTransferThread(getJobManager()).start();
			}
		};
		getGlobalContext().getTimer().schedule(_runJob, 0, _sleepSeconds);
	}

	public synchronized void removeJobFromQueue(Job job) {
		_queuedJobSet.remove(job);
	}

	public void startJobs() {
		_isStopped = false;
	}

	private JobManager getJobManager() {
		return this;
	}

	public void stopJob(Job job) {
		removeJobFromQueue(job);
		job.abort();
	}

	public void stopJobs() {
		_isStopped = true;
	}

	private boolean useCRC() {
		return _useCRC;
	}
	
	private boolean useSecureTransfers() {
		return _useSSL;
	}
}
