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
import net.sf.drftpd.master.config.FtpConfig;
import net.sf.drftpd.master.usermanager.User;
import net.sf.drftpd.remotefile.LinkedRemoteFileInterface;
import net.sf.drftpd.util.Time;

/**
 * @author mog
 * @version $Id: MintimeonlineFilter.java,v 1.1 2004/02/27 01:02:21 mog Exp $
 */
public class MintimeonlineFilter extends Filter {

	private float _multiplier;

	private long _minTime;

	public MintimeonlineFilter(FilterChain fc, int i, Properties p) {
		_minTime = Time.parseTime(FtpConfig.getProperty(p, i + ".mintime"));
		_multiplier =
			BandwidthFilter.parseMultiplier(
				FtpConfig.getProperty(p, i + ".multiplier"));
	}

	public void process(
		ScoreChart scorechart,
		User user,
		InetAddress peer,
		char direction,
		LinkedRemoteFileInterface dir)
		throws NoAvailableSlaveException {

		for (Iterator iter = scorechart.getSlaveScores().iterator();
			iter.hasNext();
			) {
			ScoreChart.SlaveScore score = (ScoreChart.SlaveScore) iter.next();
			long lastTransfer =
				System.currentTimeMillis()
					- score.getRSlave().getLastTransferForDirection(direction);
			if (lastTransfer < _minTime) {
				score.addScore(- (long) (lastTransfer * _multiplier));
			}

		}
	}

}
