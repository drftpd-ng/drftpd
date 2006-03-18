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
package org.drftpd.slaveselection.filter;

import java.net.InetAddress;
import java.util.Properties;

import net.sf.drftpd.NoAvailableSlaveException;
import net.sf.drftpd.mirroring.Job;
import net.sf.drftpd.mirroring.JobManager;

import org.drftpd.GlobalContext;
import org.drftpd.master.RemoteSlave;
import org.drftpd.usermanager.User;
import org.drftpd.vfs.InodeHandle;

/**
 * @author mog
 * @version $Id: MaxUploadsPerSlaveJob.java 879 2004-12-29 03:39:22Z mog $
 */
public class MaxUploadsPerSlaveJob extends Filter {
	private GlobalContext _gctx;

	public MaxUploadsPerSlaveJob(FilterChain fc, int i, Properties p) {
		_gctx = fc.getGlobalContext();
	}

	public MaxUploadsPerSlaveJob(GlobalContext gctx) {
		_gctx = gctx;
	}

	public void process(ScoreChart scorechart, User user, InetAddress peer,
			char direction, InodeHandle dir, RemoteSlave sourceSlave)
			throws NoAvailableSlaveException {
		process(scorechart, sourceSlave);
	}

	public void process(ScoreChart scorechart, RemoteSlave sourceSlave) {
		if (sourceSlave == null)
			return;
		JobManager jm = _gctx.getJobManager();
		for (Job job : jm.getAllJobsFromQueue()) {
			synchronized (job) {
				if (job.isTransferring()) {
					if (job.getSourceSlave().equals(sourceSlave))
						scorechart.removeSlaveScore(job.getDestinationSlave());
				}
			}
		}
	}
}
