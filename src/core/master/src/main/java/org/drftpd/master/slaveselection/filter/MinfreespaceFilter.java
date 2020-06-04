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

import org.drftpd.common.util.Bytes;
import org.drftpd.common.util.PropertyHelper;
import org.drftpd.common.vfs.InodeHandleInterface;
import org.drftpd.master.exceptions.SlaveUnavailableException;
import org.drftpd.master.slavemanagement.RemoteSlave;
import org.drftpd.master.usermanager.User;

import java.net.InetAddress;
import java.util.Iterator;
import java.util.Properties;

/**
 * Example slaveselection.conf entry:
 *
 * <pre>
 *  &lt;n&gt;.filter=minfreespace
 *  &lt;n&gt;.multiplier=1
 *  &lt;n&gt;.minfreespace=1GB
 * </pre>
 * <p>
 * Works like this:
 * if(diskfree < minfreespace) { addScore( -1 * (minfreespace - diskfree) * multiplier) ) }
 *
 * @author mog
 * @version $Id$
 */
public class MinfreespaceFilter extends Filter {
    private final long _minfreespace;

    private final float _multiplier;

    public MinfreespaceFilter(int i, Properties p) {
        super(i, p);
        _multiplier = parseMultiplier(PropertyHelper.getProperty(p, i + ".multiplier"));
        _minfreespace = Bytes.parseBytes(PropertyHelper.getProperty(p, i + ".minfreespace"));
    }

    public void process(ScoreChart scorechart, User user, InetAddress source,
                        char direction, InodeHandleInterface file, RemoteSlave sourceSlave) {
        for (Iterator<ScoreChart.SlaveScore> iter = scorechart.getSlaveScores().iterator(); iter.hasNext(); ) {
            ScoreChart.SlaveScore score = iter.next();
            long df;

            try {
                df = score.getRSlave().getSlaveStatusAvailable().getDiskSpaceAvailable();

                if (df < _minfreespace) {
                    if (_multiplier == 0) {
                        iter.remove();
                    } else {
                        score.addScore(-(long) ((_minfreespace - df) * _multiplier));
                    }
                }
            } catch (SlaveUnavailableException e) {
                iter.remove();
            }
        }
    }
}
