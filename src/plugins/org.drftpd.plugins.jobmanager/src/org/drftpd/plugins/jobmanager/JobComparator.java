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
package org.drftpd.plugins.jobmanager;

import java.io.Serializable;
import java.util.Comparator;

/**
 * @author zubov
 * @version $Id$
 * 
 */
@SuppressWarnings("serial")
public class JobComparator implements Comparator<Job>, Serializable {
	/**
	 * Compares Jobs
	 */
	public JobComparator() {
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.Comparator#compare(java.lang.Object, java.lang.Object)
	 */
	public int compare(Job job1, Job job2) {
		if (job1.getPriority() > job2.getPriority()) {
			return -1;
		}

		if (job1.getPriority() < job2.getPriority()) {
			return 1;
		}

		if (job1.getIndex() < job2.getIndex()) { // older
			return -1;
		}

		// if (job1.getIndex() > job2.getIndex()) { //younger
		return 1;
	}
}
