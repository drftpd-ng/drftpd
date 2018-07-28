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

import org.drftpd.exceptions.NoAvailableSlaveException;
import org.drftpd.master.RemoteSlave;
import org.drftpd.slaveselection.filter.ScoreChart.SlaveScore;
import org.drftpd.usermanager.User;
import org.drftpd.vfs.InodeHandleInterface;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Properties;

/**
 * Checks ScoreChart for slaves with 0 bw usage and assigns 1 extra point to the
 * one in that has been unused for the longest time.
 * 
 * @author mog, zubov
 * @version $Id$
 */
public class CycleFilter extends Filter {
	public CycleFilter(int i, Properties p) {
		super(i, p);
	}

	public void process(ScoreChart scorechart, User user, InetAddress peer,
			char direction, InodeHandleInterface dir, RemoteSlave sourceSlave)
			throws NoAvailableSlaveException {
		
		ArrayList<SlaveScore> tempList = new ArrayList<>(scorechart
                .getSlaveScores());

		while (true) {
			if (tempList.isEmpty()) {
				return;
			}

			SlaveScore first = tempList.get(0);
			
			ArrayList<SlaveScore> equalList = new ArrayList<>();
			equalList.add(first);
			tempList.remove(first);

			for (Iterator<SlaveScore> iter = tempList.iterator(); iter.hasNext();) {
				SlaveScore match = iter.next();

				if (match.compareTo(first) == 0) {
					equalList.add(match);
					iter.remove();
				}
			}

			SlaveScore leastUsed = first;

            for (SlaveScore match : equalList) {
                if (match.getRSlave().getLastTransferForDirection(direction) < leastUsed
                        .getRSlave().getLastTransferForDirection(direction)) {
                    leastUsed = match;
                }
            }

			if (leastUsed != null) {
				leastUsed.addScore(1);
			}
		}
	}
}
