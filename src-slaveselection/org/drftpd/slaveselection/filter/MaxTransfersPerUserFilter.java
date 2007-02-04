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
import java.util.Properties;

import net.sf.drftpd.NoAvailableSlaveException;

import org.apache.log4j.Logger;
import org.drftpd.GlobalContext;
import org.drftpd.master.RemoteSlave;
import org.drftpd.usermanager.User;
import org.drftpd.vfs.InodeHandleInterface;

/**
 * @author mog
 * @version $Id$
 */
public class MaxTransfersPerUserFilter extends Filter {

	private static final Logger logger = Logger
			.getLogger(MaxTransfersPerUserFilter.class);

	private GlobalContext _gctx;

	public MaxTransfersPerUserFilter(FilterChain ssm, int i, Properties p) {
		this(ssm.getGlobalContext());
	}

	public MaxTransfersPerUserFilter(GlobalContext gctx) {
		_gctx = gctx;
	}

	public void process(ScoreChart scorechart, char direction, User user)
			throws NoAvailableSlaveException {
		// Again, need to move session data to BaseFtpConnection
		/*
		 * for (BaseFtpConnection conn : _gctx.getConnectionManager()
		 * .getConnections()) {
		 * 
		 * if (!conn.getDataConnectionHandler().isTransfering()) continue; try {
		 * if (!conn.getUser().equals(user)) continue; } catch
		 * (NoSuchUserException e) { continue; }
		 * 
		 * for (Iterator<ScoreChart.SlaveScore> iter2 = scorechart
		 * .getSlaveScores().iterator(); iter2.hasNext();) {
		 * ScoreChart.SlaveScore score = iter2.next();
		 * 
		 * if (score.getRSlave().equals(
		 * conn.getDataConnectionHandler().getTranferSlave()) && direction ==
		 * conn.getDirection()) { logger.debug("Already has a transfer for slave " +
		 * score.getRSlave().getName()); iter2.remove(); } } }
		 */
		if (scorechart.isEmpty())
			throw new NoAvailableSlaveException(
					"All slaves were unavailable cause you already had open transfers to the available slaves");
	}

	public void process(ScoreChart scorechart, User user, InetAddress peer,
			char direction, InodeHandleInterface dir, RemoteSlave sourceSlave)
			throws NoAvailableSlaveException {
		process(scorechart, direction, user);
	}
}
