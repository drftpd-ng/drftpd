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

import net.sf.drftpd.NoAvailableSlaveException;
import net.sf.drftpd.remotefile.LinkedRemoteFileInterface;

import org.drftpd.usermanager.User;

import java.net.InetAddress;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Properties;


/**
 * Checks ScoreChart for slaves with 0 bw usage and assigns 1 extra point to the one in that has been unused for the longest time.
 *
 * @author mog, zubov
 * @version $Id: CycleFilter.java,v 1.6 2004/11/03 16:46:48 mog Exp $
 */
public class CycleFilter extends Filter {
    public CycleFilter(FilterChain fc, int i, Properties p) {
    }

    public void process(ScoreChart scorechart, User user, InetAddress peer,
        char direction, LinkedRemoteFileInterface dir)
        throws NoAvailableSlaveException {
        ArrayList tempList = new ArrayList(scorechart.getSlaveScores());

        while (true) {
            if (tempList.isEmpty()) {
                return;
            }

            ScoreChart.SlaveScore first = (ScoreChart.SlaveScore) tempList.get(0);
            ArrayList equalList = new ArrayList();
            equalList.add(first);
            tempList.remove(first);

            for (Iterator iter = tempList.iterator(); iter.hasNext();) {
                ScoreChart.SlaveScore match = (ScoreChart.SlaveScore) iter.next();

                if (match.compareTo(first) == 0) {
                    equalList.add(match);
                    iter.remove();
                }
            }

            ScoreChart.SlaveScore leastUsed = first;

            for (Iterator iter = equalList.iterator(); iter.hasNext();) {
                ScoreChart.SlaveScore match = (ScoreChart.SlaveScore) iter.next();

                if (match.getRSlave().getLastTransferForDirection(direction) < leastUsed.getRSlave()
                                                                                            .getLastTransferForDirection(direction)) {
                    leastUsed = match;
                }
            }

            if (leastUsed != null) {
                leastUsed.addScore(1);
            }
        }
    }
}
