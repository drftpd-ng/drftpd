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
import org.drftpd.exceptions.NoAvailableSlaveException;
import org.drftpd.exceptions.SlaveUnavailableException;
import org.drftpd.master.RemoteSlave;
import org.drftpd.slave.SlaveStatus;
import org.drftpd.slave.Transfer;
import org.drftpd.slaveselection.filter.ScoreChart.SlaveScore;
import org.drftpd.usermanager.User;
import org.drftpd.vfs.InodeHandleInterface;

import java.net.InetAddress;
import java.util.Iterator;
import java.util.Properties;

/**
 * @author zubov
 */
public class MaxtransfersFilter extends Filter {
	private long _maxTransfers;

	public MaxtransfersFilter(int i, Properties p) {
		super(i, p);
		_maxTransfers = Long.parseLong(PropertyHelper.getProperty(p, i+ ".maxtransfers"));
	}

	public void process(ScoreChart scorechart, User user, InetAddress peer,
			char direction, InodeHandleInterface dir, RemoteSlave sourceSlave)
			throws NoAvailableSlaveException {
		for (Iterator<SlaveScore> iter = scorechart.getSlaveScores().iterator(); iter
				.hasNext();) {
			SlaveScore score = iter.next();
			SlaveStatus status;

			try {
				status = score.getRSlave().getSlaveStatusAvailable();
			} catch (SlaveUnavailableException e) {
				// how come the slave is offline? it was just online.
				iter.remove();
				continue;
			}

			int transfers = 0;

			if (direction == Transfer.TRANSFER_RECEIVING_UPLOAD) {
				transfers = status.getTransfersReceiving();
			} else if (direction == Transfer.TRANSFER_SENDING_DOWNLOAD) {
				transfers = status.getTransfersSending();
			} else {
				throw new IllegalArgumentException("Direction was not one of download or upload");
			}

			if (transfers > _maxTransfers) {
				iter.remove();
			}
		}
	}
}
