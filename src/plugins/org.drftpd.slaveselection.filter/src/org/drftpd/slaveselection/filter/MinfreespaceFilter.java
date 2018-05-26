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

import org.drftpd.Bytes;
import org.drftpd.PropertyHelper;
import org.drftpd.exceptions.SlaveUnavailableException;
import org.drftpd.master.RemoteSlave;
import org.drftpd.slaveselection.filter.ScoreChart.SlaveScore;
import org.drftpd.usermanager.User;
import org.drftpd.vfs.InodeHandleInterface;

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
 * 
 * Works like this: 
 * if(diskfree < minfreespace) { addScore( -1 * (minfreespace - diskfree) * multiplier) ) }
 * 
 * @author mog
 * @version $Id$
 */
public class MinfreespaceFilter extends Filter {
	private long _minfreespace;

	private float _multiplier;

	public MinfreespaceFilter(int i, Properties p) {
		super(i, p);
		_multiplier = parseMultiplier(PropertyHelper.getProperty(p, i + ".multiplier"));
		_minfreespace = Bytes.parseBytes(PropertyHelper.getProperty(p, i+ ".minfreespace"));
	}

	public void process(ScoreChart scorechart, User user, InetAddress source,
			char direction, InodeHandleInterface file, RemoteSlave sourceSlave) {
		for (Iterator<SlaveScore> iter = scorechart.getSlaveScores().iterator(); iter.hasNext();) {
			SlaveScore score = iter.next();
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
