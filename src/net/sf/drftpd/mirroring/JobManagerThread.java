/*
 * Created on Dec 11, 2003
 *
 * To change the template for this generated file go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
package net.sf.drftpd.mirroring;

import org.apache.log4j.Logger;

import net.sf.drftpd.master.RemoteSlave;

/**
 * @author zubov
 * @version $Id: JobManagerThread.java,v 1.6 2004/01/08 05:32:16 zubov Exp $
 */
public class JobManagerThread extends Thread {
	private static final Logger logger =
		Logger.getLogger(JobManagerThread.class);
	private JobManager _jm;

	private RemoteSlave _rslave;
	private boolean stopped = false;

	public JobManagerThread() {
	}

	/**
	 * This class repeatedly calls JobManager.processJob() for its respective RemoteSlave
	 */
	public JobManagerThread(RemoteSlave rslave, JobManager jm) {
		_rslave = rslave;
		_jm = jm;
	}

	public RemoteSlave getRSlave() {
		return _rslave;
	}

	public void run() {
		logger.debug("JobManagerThread started for " + _rslave.getName());
		while (true) {
			if (stopped) {
				logger.debug(
					"JobManagerThread stopped for " + _rslave.getName());
				return;
			}
			try {
				while (true){
					Job job = _jm.getNextJob(_rslave);
					if ( job == null )
						break;
					_jm.removeJob(job);
					if (_jm.processJob(_rslave,job)) {
						synchronized (job.getDestinationSlaves()) {
							job.getDestinationSlaves().remove(_rslave);
							if (job.getDestinationSlaves().size() > 0)
								_jm.addJob(job); // job still has more places to transfer
						}
					}
				}
			} catch (RuntimeException e1) {
				logger.debug(
					"Caught RunTimeException in processJob for "
						+ _rslave.getName(),
					e1);
			}
			try {
				Thread.sleep(60000);
			} catch (InterruptedException e) {
			}
		}
	}

	public void stopme() {
		stopped = true;
	}
}
