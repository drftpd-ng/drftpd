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

import net.sf.drftpd.NoAvailableSlaveException;
import net.sf.drftpd.master.BaseFtpConnection;
import net.sf.drftpd.remotefile.LinkedRemoteFileInterface;

import org.apache.log4j.Logger;
import org.drftpd.GlobalContext;
import org.drftpd.usermanager.User;

import java.net.InetAddress;

import java.util.Iterator;
import java.util.Properties;

/**
 * @author mog
 * @version $Id: MaxTransfersPerUserFilter.java,v 1.1 2004/11/11 14:58:35 mog Exp $
 */
public class MaxTransfersPerUserFilter extends Filter {
	private GlobalContext _gctx;
	private static final Logger logger = Logger.getLogger(MaxTransfersPerUserFilter.class);

	public MaxTransfersPerUserFilter(FilterChain ssm, int i,
			Properties p) {
		this(ssm.getGlobalContext());
	}

	public MaxTransfersPerUserFilter(GlobalContext gctx) {
		_gctx = gctx;
	}

	public void process(ScoreChart scorechart, User user, InetAddress peer,
			char direction, LinkedRemoteFileInterface dir)
			throws NoAvailableSlaveException {

		for (BaseFtpConnection conn : _gctx.getConnectionManager()
				.getConnections()) {

			if (!conn.isTransfering())
				continue;

			for (Iterator<ScoreChart.SlaveScore> iter2 = scorechart
					.getSlaveScores().iterator(); iter2.hasNext();) {
				ScoreChart.SlaveScore score = iter2.next();

				if (score.getRSlave().equals(
						conn.getDataConnectionHandler().getTranferSlave()) && direction == conn.getDirection()) {
					logger.debug("Already has a transfer for slave "+score.getRSlave().getName());
					iter2.remove();
				}
			}
		}
	}
}
