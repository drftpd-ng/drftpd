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

import org.apache.log4j.Logger;

import net.sf.drftpd.master.RemoteSlave;

/**
 * @author zubov
 * @version $Id: JobManagerThread.java,v 1.9 2004/02/10 00:03:14 mog Exp $
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
				while (_jm.processJob(_rslave));
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
