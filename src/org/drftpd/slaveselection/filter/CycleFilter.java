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
import java.util.Iterator;
import java.util.Properties;

import net.sf.drftpd.NoAvailableSlaveException;
import net.sf.drftpd.SlaveUnavailableException;
import net.sf.drftpd.master.usermanager.User;
import net.sf.drftpd.remotefile.LinkedRemoteFileInterface;

/**
 * Checks ScoreChart for slaves with 0 bw usage and assigns 1 extra point to the one in that has been unused for the longest time.
 * 
 * @author mog
 * @version $Id: CycleFilter.java,v 1.3 2004/03/04 01:41:27 zubov Exp $
 */
public class CycleFilter extends Filter {

	public CycleFilter(FilterChain fc, int i, Properties p) {
	}

	public void process(
		ScoreChart scorechart,
		User user,
		InetAddress peer,
		char direction,
		LinkedRemoteFileInterface dir)
		throws NoAvailableSlaveException {

		ScoreChart.SlaveScore leastUsed = null;
		for (Iterator iter = scorechart.getSlaveScores().iterator();
			iter.hasNext();
			) {
			ScoreChart.SlaveScore score = (ScoreChart.SlaveScore) iter.next();
			try {
				if (score
					.getRSlave()
					.getStatus()
					.getThroughputDirection(direction)
					== 0) {

					if (leastUsed == null) {
						leastUsed = score;
					} else if (
						score.getRSlave().getLastTransferForDirection(
							direction)
							< leastUsed.getRSlave().getLastTransferForDirection(
								direction)) {
						leastUsed = score;
					}
				}
			} catch (SlaveUnavailableException e) {
				iter.remove();
			}
		}
		if (leastUsed != null) {
			leastUsed.addScore(1);
		}
	}
}
