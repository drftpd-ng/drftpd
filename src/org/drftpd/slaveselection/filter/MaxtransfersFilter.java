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
 */package org.drftpd.slaveselection.filter;

import java.net.InetAddress;
import java.rmi.RemoteException;
import java.util.Iterator;
import java.util.Properties;

import org.apache.log4j.Logger;

import net.sf.drftpd.NoAvailableSlaveException;
import net.sf.drftpd.master.config.FtpConfig;
import net.sf.drftpd.master.usermanager.User;
import net.sf.drftpd.remotefile.LinkedRemoteFileInterface;
import net.sf.drftpd.slave.SlaveStatus;
import net.sf.drftpd.slave.Transfer;

/**
 * @author zubov
 */
public class MaxtransfersFilter extends Filter {
	private static final Logger logger =
		Logger.getLogger(MaxtransfersFilter.class);

	private long _maxTransfers;

	public MaxtransfersFilter(FilterChain ssm, int i, Properties p) {
		_maxTransfers =
			Long.parseLong(FtpConfig.getProperty(p, i + ".maxtransfers"));
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
			ScoreChart.SlaveScore slavescore =
				(ScoreChart.SlaveScore) iter.next();
			SlaveStatus status;
			try {
				status = slavescore.getRSlave().getStatus();
			} catch (Exception e) {
				if (e instanceof RemoteException) {
					slavescore.getRSlave().handleRemoteException(
						(RemoteException) e);
				}
				iter.remove();
				continue;
			}
			int transfers = 0;
			if ( direction == Transfer.TRANSFER_RECEIVING_UPLOAD)
					transfers = status.getTransfersReceiving();
			else if ( direction == Transfer.TRANSFER_SENDING_DOWNLOAD)
					transfers = status.getTransfersSending();
			else throw new IllegalArgumentException("Direction was not one of download or upload");
			if (transfers > _maxTransfers) {
				iter.remove();
			}
		}
	}
}
