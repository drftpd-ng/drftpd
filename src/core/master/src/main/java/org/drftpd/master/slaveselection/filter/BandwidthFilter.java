/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * DrFTPD is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with DrFTPD; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.drftpd.master.slaveselection.filter;

import org.drftpd.common.util.PropertyHelper;
import org.drftpd.common.vfs.InodeHandleInterface;
import org.drftpd.master.exceptions.SlaveUnavailableException;
import org.drftpd.master.slavemanagement.RemoteSlave;
import org.drftpd.master.slavemanagement.SlaveStatus;
import org.drftpd.master.usermanager.User;

import java.net.InetAddress;
import java.util.Iterator;
import java.util.Properties;

/**
 * Removes bandwidth * multiplier from the score.
 *
 * @author mog
 * @version $Id$
 */
public class BandwidthFilter extends Filter {
    protected float _multiplier;

    public BandwidthFilter(int i, Properties p) {
        super(i, p);
        _multiplier = parseMultiplier(PropertyHelper.getProperty(p, i + ".multiplier"));
    }

    public void process(ScoreChart scorechart, User user, InetAddress source,
                        char direction, InodeHandleInterface file, RemoteSlave sourceSlave) {
        for (Iterator<ScoreChart.SlaveScore> iter = scorechart.getSlaveScores().iterator(); iter.hasNext(); ) {
            ScoreChart.SlaveScore score = iter.next();
            SlaveStatus status;

            try {
                status = score.getRSlave().getSlaveStatusAvailable();
            } catch (SlaveUnavailableException e) {
                // how come the slave is offline? it was just online.
                iter.remove();
                continue;
            }

            score.addScore(-(long) (status.getThroughputDirection(direction) * _multiplier));
        }
    }
}
