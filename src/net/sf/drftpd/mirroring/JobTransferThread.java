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
/**
 * @author zubov
 * @version $Id
 */
public class JobTransferThread extends Thread {
	private static final Logger logger = Logger
			.getLogger(JobTransferThread.class);
	private JobManager _jm;
	/**
	 * This class sends a JobTransfer if it is available
	 */
	JobTransferThread(JobManager jm) {
		_jm = jm;
	}
	
	public void run() {
		setName("JobTransfer");
		logger.debug("JobTransfer started");
		try {
		if(_jm.processJob()) {
			logger.debug("processJob() returned true, file was sent okay");
		}
		}
		catch (Exception e) {
			logger.debug("",e);
		}
		logger.debug("JobTransfer stopped");
	}
}
