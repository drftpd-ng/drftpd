/*
 * Created on Dec 9, 2003
 *
 * To change the template for this generated file go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
package net.sf.drftpd.mirroring;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.apache.log4j.Logger;

import net.sf.drftpd.NoAvailableSlaveException;
import net.sf.drftpd.event.Event;
import net.sf.drftpd.event.FtpListener;
import net.sf.drftpd.event.SlaveEvent;
import net.sf.drftpd.master.ConnectionManager;
import net.sf.drftpd.master.RemoteSlave;
import net.sf.drftpd.remotefile.LinkedRemoteFile;

/**
 * @author matt
 * @version $Id: JobManager.java,v 1.4 2003/12/23 13:38:21 mog Exp $
 */
public class JobManager implements FtpListener {
	private ConnectionManager _cm;
	private ArrayList _jobList = new ArrayList();
	private ArrayList _slaveSendingList = new ArrayList();
	private ArrayList _threadList = new ArrayList();
	private Logger logger = Logger.getLogger(JobManager.class);
	/**
	 * Keeps track of all jobs and controls them
	 */
	public JobManager() {
	}

	public void actionPerformed(Event event) {
		if (!(event instanceof SlaveEvent))
			return;
		SlaveEvent slaveEvent = (SlaveEvent) event;
		if (slaveEvent.getCommand() == "DELSLAVE") {
			//synchronized (_threadList) { // does not need to be synchronized if only one thread handles events 
			for (Iterator iter = _threadList.iterator(); iter.hasNext();) {
				JobManagerThread tempThread = (JobManagerThread) iter.next();
				if (tempThread.getRSlave() == slaveEvent.getRSlave()) {
					tempThread.stopme();
					_threadList.remove(tempThread);
					break; // only one slave can die at a time
					// have to break here or else get ConcurrentModificationException
					// no point in searching for more anyhow
				}
			}
			//}
		} else if (slaveEvent.getCommand() == "ADDSLAVE") {
			JobManagerThread newThread =
				new JobManagerThread(slaveEvent.getRSlave(), this);
			_threadList.add(newThread);
			newThread.start();
		}
		logger.info(
			slaveEvent.getCommand()
				+ " was issued on "
				+ slaveEvent.getRSlave().getName());
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
	public synchronized List getAllJobs(Object o) {
		ArrayList tempList = new ArrayList();
		for (Iterator iter = _jobList.iterator(); iter.hasNext();) {
			Job tempJob = (Job) iter.next();
			if (tempJob.getSource().equals(o))
				tempList.add(tempJob);
		}
		return tempList;
	}
	/**
	 * Gets the next job suitable for the slave, returns null if none is found
	 */
	private synchronized Job getNextJob(RemoteSlave slave) {
		Job myMirrorJob = null;
		for (Iterator iter = _jobList.iterator(); iter.hasNext();) {
			Job tempJob = (Job) iter.next();
			if (tempJob.getDestinationSlaves().contains(null)) { // mirror job
				try {
					if (myMirrorJob == null) {
						if (tempJob.getFile() == null) {
							logger.error("tempJob.getFile() == null");
							continue;
						}
						if (tempJob.getFile().getAvailableSlaves() == null) {
							logger.error(
								"tempJob.getFile().getAvailableSlaves() == null");
							continue;
						}
						if (!tempJob
							.getFile()
							.getAvailableSlaves()
							.contains(slave)) {
							logger.info(
								"myMirror is assigned - " + slave.getName());
							myMirrorJob = tempJob;
						}
					}
				} catch (NoAvailableSlaveException e) {
					logger.info(
						"NoAvailableSlaveException for myMirrorJob - "
							+ slave.getName());
					// can't transfer it, so don't set myMirrorJob
				}
				continue;
			}
			try {
				if (tempJob.getDestinationSlaves().contains(slave)
					&& !tempJob.getFile().getAvailableSlaves().contains(slave)) {
					logger.info(
						"tempJob is being returned - " + slave.getName());
					return tempJob;
				}
			} catch (NoAvailableSlaveException e) {
				// continue searching through jobs
				logger.info(
					"NoAvailableSlaveException for tempJob - "
						+ slave.getName());
			}
		}
		if (myMirrorJob != null) {
			myMirrorJob.getDestinationSlaves().add(slave);
			myMirrorJob.getDestinationSlaves().remove(null);
		}
		//logger.info("myMirrorJob is returning - " + slave.getName());
		return myMirrorJob; // will be null if nothing is found
	}

	public void init(ConnectionManager mgr) {
		_cm = mgr;
		Collection slaveList;
		try {
			slaveList = _cm.getSlaveManager().getAvailableSlaves();
		} catch (NoAvailableSlaveException e) {
			slaveList = null;
		}
		if (slaveList == null)
			return;
		for (Iterator iter = slaveList.iterator(); iter.hasNext();) {
			RemoteSlave rslave = (RemoteSlave) iter.next();
			JobManagerThread newThread = new JobManagerThread(rslave, this);
			_threadList.add(newThread);
			newThread.start();
		}
	}
	public synchronized boolean isDone(Job job) {
		return !_jobList.contains(job);
	}
	public boolean processJob(RemoteSlave slave) {
		Job temp = getNextJob(slave);
		if (temp == null) { // nothing to process for this slave
			//logger.info("Nothing to process for slave " + slave.getName());
			//printJobs();
			return false;
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
				return false;
			}
			_slaveSendingList.add(sourceSlave);
			new SlaveTransfer(temp.getFile(), sourceSlave, slave).transfer();
			_slaveSendingList.remove(sourceSlave);
			difference = System.currentTimeMillis() - time;
			temp.addTimeSpent(difference);
		} catch (IOException e) {
			_slaveSendingList.remove(sourceSlave);
			logger.error(
				"Error Sending "
					+ temp.getFile().getName()
					+ " from "
					+ sourceSlave.getName()
					+ " to "
					+ slave.getName());
			return false;
		}
		logger.info(
			"Done Sending "
				+ temp.getFile().getName()
				+ " from "
				+ sourceSlave.getName()
				+ " to "
				+ slave.getName()
				+ " in "
				+ difference / 1000
				+ " seconds");
		synchronized (temp.getDestinationSlaves()) {
			temp.getDestinationSlaves().remove(slave);
			if (temp.getDestinationSlaves().size() == 0)
				remove(temp); // job is finished
		}
		return true;
	}
	public synchronized void remove(Job job) {
		_jobList.remove(job);
		Collections.sort(_jobList, new JobComparator());
	}
}
