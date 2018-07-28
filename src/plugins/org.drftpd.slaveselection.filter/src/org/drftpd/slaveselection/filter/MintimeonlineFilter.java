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

import org.drftpd.PropertyHelper;
import org.drftpd.Time;
import org.drftpd.exceptions.NoAvailableSlaveException;
import org.drftpd.master.RemoteSlave;
import org.drftpd.slaveselection.filter.ScoreChart.SlaveScore;
import org.drftpd.usermanager.User;
import org.drftpd.vfs.InodeHandleInterface;

import java.net.InetAddress;
import java.util.Properties;

/**
 * @author mog
 * @version $Id$
 */
public class MintimeonlineFilter extends Filter {
	private long _minTime;

	private float _multiplier;

	public MintimeonlineFilter(int i, Properties p) {
		super(i, p);
		_minTime = Time.parseTime(PropertyHelper.getProperty(p, i + ".mintime"));
		_multiplier = parseMultiplier(PropertyHelper.getProperty(p, i + ".multiplier"));
	}

	public void process(ScoreChart scorechart, User user, InetAddress peer,
			char direction, InodeHandleInterface dir, RemoteSlave sourceSlave)
			throws NoAvailableSlaveException {
		process(scorechart, user, peer, direction, dir, System.currentTimeMillis());
	}

	protected void process(ScoreChart scorechart, User user, InetAddress peer,
			char direction, InodeHandleInterface dir, long currentTime) {
		
		for (SlaveScore score : scorechart.getSlaveScores()) {
			long lastTransfer = currentTime	- score.getRSlave().getLastTransferForDirection(direction);

			if (lastTransfer < _minTime) {
				score.addScore(-(long) (lastTransfer * _multiplier));
			}
		}
	}
}
