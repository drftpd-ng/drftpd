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

import net.sf.drftpd.SlaveUnavailableException;
import net.sf.drftpd.master.config.FtpConfig;
import net.sf.drftpd.remotefile.LinkedRemoteFileInterface;

import org.drftpd.Bytes;
import org.drftpd.PropertyHelper;
import org.drftpd.master.RemoteSlave;
import org.drftpd.usermanager.User;

import java.net.InetAddress;

import java.util.Iterator;
import java.util.Properties;


/**
 * Example slaveselection.conf entry:
 * <pre>
 * <n>.filter=minfreespace
 * <n>.multiplier=1
 * <n>.minfreespace=1GB
 * </pre>
 *
 * Works like this:
 * if(diskfree > minfreespace) {
 *   addScore((minfreespace - diskfree) * multiplier)
 * }
 * @author mog
 * @version $Id$
 */
public class MinfreespaceFilter extends Filter {
    private long _minfreespace;
    private float _multiplier;

    public MinfreespaceFilter(FilterChain ssm, int i, Properties p) {
        //_multiplier = -Integer.parseInt(FtpConfig.getProperty(p, i + ".multiplier"));
        _multiplier = BandwidthFilter.parseMultiplier(PropertyHelper.getProperty(p,
                    i + ".multiplier"));
        _minfreespace = Bytes.parseBytes(PropertyHelper.getProperty(p,
                    i + ".minfreespace"));
    }

    public void process(ScoreChart scorechart, User user, InetAddress source,
        char direction, LinkedRemoteFileInterface file, RemoteSlave sourceSlave) {
        for (Iterator iter = scorechart.getSlaveScores().iterator();
                iter.hasNext();) {
            ScoreChart.SlaveScore score = (ScoreChart.SlaveScore) iter.next();
            long df;

            try {
                df = score.getRSlave().getSlaveStatusAvailable()
                          .getDiskSpaceAvailable();

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
