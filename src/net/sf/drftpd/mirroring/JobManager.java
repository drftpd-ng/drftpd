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
import java.net.SocketException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import net.sf.drftpd.FatalException;
import net.sf.drftpd.FileExistsException;
import net.sf.drftpd.NoAvailableSlaveException;
import net.sf.drftpd.SlaveUnavailableException;

import org.apache.log4j.Logger;
import org.drftpd.PropertyHelper;
import org.drftpd.master.ConnectionManager;
import org.drftpd.master.RemoteSlave;
import org.drftpd.slave.RemoteIOException;


/**
 * @author zubov
 * @version $Id$
 */
public class JobManager implements Runnable {
    private static final Logger logger = Logger.getLogger(JobManager.class);
    private ConnectionManager _cm;
    private boolean _isStopped = false;
    private LinkedList<Job> _jobList;
    private int _sleepSeconds;
    private boolean _useCRC;
    private Thread thread;

    /**
     * Keeps track of all jobs and controls them
     */
    public JobManager(ConnectionManager cm) throws IOException {
        this(cm, null);
    }

    public JobManager(ConnectionManager cm, Properties p) {
        _cm = cm;
        _jobList = new LinkedList<Job>();
        reload(p);
    }

    public synchronized void addJobToQueue(Job job) {
        Collection<RemoteSlave> slaves = job.getFile().getSlaves();

        for (Iterator<RemoteSlave> iter = slaves.iterator(); iter.hasNext();) {
            RemoteSlave slave = iter.next();

            if (job.getDestinationSlaves().contains(slave)) {
                job.sentToSlave(slave);
            }
        }

        if (job.isDone()) {
            return;
        }

        _jobList.add(job);
        Collections.sort(_jobList, new JobComparator());
    }

    /**
     * Gets all jobs.
     */
    public synchronized List<Job> getAllJobsFromQueue() {
        return Collections.unmodifiableList(_jobList);
    }

    public synchronized Job getNextJob(Set<RemoteSlave> busySlaves, Set skipJobs) {
        for (Iterator iter = _jobList.iterator(); iter.hasNext();) {
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

    public void processJob() {
        Job job = null;
        RemoteSlave sourceSlave = null;
        RemoteSlave destSlave = null;
        long time;
        long difference;

        synchronized (this) {
            Collection<RemoteSlave> availableSlaves;

            try {
                availableSlaves = _cm.getGlobalContext().getSlaveManager()
                                     .getAvailableSlaves();
            } catch (NoAvailableSlaveException e1) {
                return; // can't transfer with no slaves
            }

            Set<RemoteSlave> busySlavesDown = new HashSet<RemoteSlave>();
            Set<Job> skipJobs = new HashSet<Job>();

            while (!busySlavesDown.containsAll(availableSlaves)) {
                job = getNextJob(busySlavesDown, skipJobs);

                if (job == null) {
                    return;
                }

                //logger.debug("looking up slave for job " + job);
                try {
                    sourceSlave = _cm.getGlobalContext().getSlaveManager()
                                     .getSlaveSelectionManager()
                                     .getASlaveForJobDownload(job);
                } catch (NoAvailableSlaveException e) {
                    try {
                        busySlavesDown.addAll(job.getFile().getAvailableSlaves());
                    } catch (NoAvailableSlaveException e2) {
                    }

                    continue;
                }

                if (sourceSlave == null) {
                    logger.debug(
                        "JobManager was unable to find a suitable job for transfer");
                    return;
                }

                availableSlaves.removeAll(job.getFile().getSlaves());
                try {
                    destSlave = _cm.getGlobalContext().getSlaveManager()
                                   .getSlaveSelectionManager()
                                   .getASlaveForJobUpload(job, sourceSlave, availableSlaves);

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
                logger.debug(
                    "destSlave is null, all destination slaves are busy" + job);
                return;
            }

            time = System.currentTimeMillis();
            difference = 0;
        }

        // job is not deleted and is out of the jobList, we are ready to
        // process
        logger.info("Sending " + job.getFile().getName() + " from " +
            sourceSlave.getName() + " to " + destSlave.getName());

        try {
            if (!job.transfer(useCRC(), sourceSlave, destSlave)) { // crc failed
                destSlave.simpleDelete(job.getFile().getPath());
                logger.debug("CRC did not match for " + job.getFile() +
                    " when sending from " + sourceSlave.getName() + " to " +
                    destSlave.getName());

                return;
            }
        } catch (DestinationSlaveException e) {
            if (e.getCause() instanceof FileExistsException) {
                logger.debug("Caught FileExistsException in sending " +
                    job.getFile().getName() + " from " + sourceSlave.getName() +
                    " to " + destSlave.getName(), e);

                try {
                    String index = destSlave.issueChecksumToSlave(job.getFile()
                                                                     .getPath());

                    if (destSlave.fetchChecksumFromIndex(index) == job.getFile()
                                                                          .getCheckSum()) {
                        logger.debug("Accepting file because the crc's match");
                    } else {
                        destSlave.simpleDelete(job.getFile().getPath());

                        return;
                    }

                    return;
                } catch (NoAvailableSlaveException e1) {
                    return;
                } catch (SlaveUnavailableException e2) {
                    return;
                } catch (RemoteIOException e1) {
                    return;
                }
            } else if (e.getCause() instanceof SocketException) {
                SocketException se = (SocketException) e.getCause();
                destSlave.addNetworkError(se);
            } else {
                //destSlave.setOffline(
                //    "Error on slave during slave2slave transfer, check logs");
                logger.error("Error on slave during slave2slave transfer", e);
            }

            return;
        } catch (SourceSlaveException e) {
            if (e.getCause() instanceof FileNotFoundException) {
                logger.warn("Caught FileNotFoundException in sending " +
                    job.getFile().getName() + " from " + sourceSlave.getName() +
                    " to " + destSlave.getName(), e);
                job.getFile().removeSlave(sourceSlave);

                return;
            } else if (e.getCause() instanceof SocketException) {
                SocketException se = (SocketException) e.getCause();
                sourceSlave.addNetworkError(se);
            } else {
                //sourceSlave.setOffline(
                //    "Error on slave during slave2slave transfer, check logs");
                logger.error("Error on slave during slave2slave transfer", e);
            }

            return;
        } catch (SlaveException e) {
            throw new RuntimeException(
                "SlaveException was not of type DestinationSlaveException or SourceSlaveException");
        }

        difference = System.currentTimeMillis() - time;
        logger.info("Sent file " + job.getFile().getName() + " to " +
            destSlave.getName() + " from " + sourceSlave.getName());
        job.addTimeSpent(difference);
        job.sentToSlave(destSlave);

        if (job.isDone()) {
            logger.debug("Job is finished, removing job " + job.getFile());
            removeJobFromQueue(job);
        }
    }

    public void reload() {
        reload(null);
    }

    public void reload(Properties p) {
        if (p == null) {
            p = new Properties();

            try {
                p.load(new FileInputStream("conf/jobmanager.conf"));
            } catch (IOException e) {
                throw new FatalException(e);
            }
        }

        _useCRC = p.getProperty("useCRC", "true").equals("true");
        _sleepSeconds = 1000 * Integer.parseInt(PropertyHelper.getProperty(p,
                    "sleepSeconds"));
    }

    public synchronized void removeJobFromQueue(Job job) {
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
        removeJobFromQueue(job);
        job.setDone();
    }

    public void stopJobs() {
        _isStopped = true;
    }

    private boolean useCRC() {
        return _useCRC;
    }
}
