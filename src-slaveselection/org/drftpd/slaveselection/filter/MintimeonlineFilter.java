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

import org.drftpd.PropertyHelper;
import org.drftpd.Time;
import org.drftpd.master.RemoteSlave;
import org.drftpd.usermanager.User;
import org.drftpd.vfs.InodeHandleInterface;

/**
 * @author mog
 * @version $Id$
 */
public class MintimeonlineFilter extends Filter {
	private long _minTime;

	private float _multiplier;

	public MintimeonlineFilter(FilterChain fc, int i, Properties p) {
		_minTime = Time
				.parseTime(PropertyHelper.getProperty(p, i + ".mintime"));
		_multiplier = BandwidthFilter.parseMultiplier(PropertyHelper
				.getProperty(p, i + ".multiplier"));
	}

	public void process(ScoreChart scorechart, User user, InetAddress peer,
			char direction, InodeHandleInterface dir, RemoteSlave sourceSlave)
			throws NoAvailableSlaveException {
		process(scorechart, user, peer, direction, dir, System
				.currentTimeMillis());
	}

	protected void process(ScoreChart scorechart, User user, InetAddress peer,
			char direction, InodeHandleInterface dir, long currentTime)
			throws NoAvailableSlaveException {
		for (Iterator iter = scorechart.getSlaveScores().iterator(); iter
				.hasNext();) {
			ScoreChart.SlaveScore score = (ScoreChart.SlaveScore) iter.next();
			long lastTransfer = currentTime
					- score.getRSlave().getLastTransferForDirection(direction);

			if (lastTransfer < _minTime) {
				score.addScore(-(long) (lastTransfer * _multiplier));
			}
		}
	}
}
