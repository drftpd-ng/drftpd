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
 * @version $Id: JobManagerThread.java,v 1.2 2003/12/11 23:12:51 zubov Exp $
 */
public class JobManagerThread extends Thread {
	private JobManager _jm;

	private RemoteSlave _rslave;
	private Logger logger = Logger.getLogger(JobManagerThread.class);
	private boolean stopped = false;

	/**
	 * 
	 */
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
		logger.info("JobManagerThread started for " + _rslave.getName());
		while (true) {
			if (stopped) {
				logger.info(
					"JobManagerThread stopped for " + _rslave.getName());
				return;
			}
			try {
				while (_jm.processJob(_rslave));
			} catch (RuntimeException e1) {
				logger.info(
					"Caught RunTimeException in processJob for "
						+ _rslave.getName());
				e1.printStackTrace();
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
