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
import java.rmi.RemoteException;
import java.util.Collection;
import java.util.Iterator;
import java.util.Properties;

import net.sf.drftpd.master.config.FtpConfig;
import net.sf.drftpd.master.usermanager.User;
import net.sf.drftpd.remotefile.LinkedRemoteFileInterface;
import net.sf.drftpd.slave.SlaveStatus;

import org.apache.log4j.Logger;

/**
 * Removes bandwidth * multiplier from the score.
 * @author mog
 * @version $Id: BandwidthFilter.java,v 1.4 2004/05/17 11:27:26 mog Exp $
 */
public class BandwidthFilter extends Filter {
	private static final Logger logger =
		Logger.getLogger(BandwidthFilter.class);

	protected float _multiplier;

	public BandwidthFilter(FilterChain ssm, int i, Properties p) {
		setMultiplier(FtpConfig.getProperty(p, i + ".multiplier"));
	}

	protected void setMultiplier(String s) {
		_multiplier = parseMultiplier(s);
	}
	protected static float parseMultiplier(String string) {
		if (string.equalsIgnoreCase("remove"))
			return 0;
		boolean isMultiplier;
		float multiplier = 1;
		while (string.length() != 0) {
			char c = string.charAt(0);
			if (c == '*') {
				isMultiplier = true;
				string = string.substring(1);
			} else if (c == '/') {
				isMultiplier = false;
				string = string.substring(1);
			} else {
				isMultiplier = true;
			}

			int pos = string.indexOf('*');
			if (pos == -1)
				pos = string.length();
			int tmp = string.indexOf('/');
			if (tmp != -1 && tmp < pos)
				pos = tmp;
			if (isMultiplier) {
				multiplier *= Float.parseFloat(string.substring(0, pos));
			} else {
				multiplier /= Float.parseFloat(string.substring(0, pos));
			}
			string = string.substring(pos);
		}
		return multiplier;
	}

	public void process(
		ScoreChart scorechart,
		User user,
		InetAddress source,
		char direction,
		LinkedRemoteFileInterface file) {
		Collection slavescores = scorechart.getSlaveScores();
		for (Iterator iter = slavescores.iterator(); iter.hasNext();) {
			ScoreChart.SlaveScore score = (ScoreChart.SlaveScore) iter.next();
			SlaveStatus status;
			try {
				status = score.getRSlave().getStatus();
			} catch (Exception e) {
				if (e instanceof RemoteException) {
					score.getRSlave().handleRemoteException(
						(RemoteException) e);
				}
				iter.remove();
				continue;
			}
			score.addScore(
				- (long)
					(status.getThroughputDirection(direction)*_multiplier));
		}
	}

}
