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

import net.sf.drftpd.master.usermanager.User;
import net.sf.drftpd.remotefile.LinkedRemoteFileInterface;
import net.sf.drftpd.slave.SlaveStatus;
import net.sf.drftpd.slave.Transfer;

/*
 * @author zubov
 * @version $Id
 */
public class ReversebandwidthFilter extends BandwidthFilter {
	public ReversebandwidthFilter(FilterChain ssm, int i, Properties p) {
		super(ssm, i, p);
	}

	public void process(
		ScoreChart scorechart,
		User user,
		InetAddress source,
		char direction,
		LinkedRemoteFileInterface file) {
		char oppositeDirection;
		if (direction == Transfer.TRANSFER_RECEIVING_UPLOAD) {
			oppositeDirection = Transfer.TRANSFER_SENDING_DOWNLOAD;
		} else
			oppositeDirection = Transfer.TRANSFER_RECEIVING_UPLOAD;

		Collection slavescores = scorechart.getSlaveScores();
		for (Iterator iter = slavescores.iterator(); iter.hasNext();) {
			ScoreChart.SlaveScore score = (ScoreChart.SlaveScore) iter.next();
			SlaveStatus status;
			try {
				status = score.getRSlave().getStatusAvailable();
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
					(status.getThroughputDirection(oppositeDirection)
						* _multiplier));
		}
	}

}
