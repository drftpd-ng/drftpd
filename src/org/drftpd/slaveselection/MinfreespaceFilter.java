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
package org.drftpd.slaveselection;

import java.net.InetAddress;
import java.rmi.RemoteException;
import java.util.Iterator;
import java.util.Properties;

import net.sf.drftpd.Bytes;
import net.sf.drftpd.NoAvailableSlaveException;
import net.sf.drftpd.master.config.FtpConfig;
import net.sf.drftpd.master.usermanager.User;
import net.sf.drftpd.remotefile.LinkedRemoteFileInterface;

/**
 * @author mog
 * @version $Id: MinfreespaceFilter.java,v 1.1 2004/02/23 01:14:41 mog Exp $
 */
public class MinfreespaceFilter extends Filter {
	private long _minfreespace;

	private int _assign;

	public MinfreespaceFilter(SlaveSelectionManager ssm, int i, Properties p) {
		_assign = -Integer.parseInt(FtpConfig.getProperty(p, i + ".remove"));
		_minfreespace =
			Bytes.parseBytes(FtpConfig.getProperty(p, i + ".minfreespace"));
	}

	public void process(
		ScoreChart scorechart,
		User user,
		InetAddress source,
		char direction,
		LinkedRemoteFileInterface file) {
		for (Iterator iter = scorechart.getSlaveScores().iterator();
			iter.hasNext();
			) {
			ScoreChart.SlaveScore score = (ScoreChart.SlaveScore) iter.next();
			long df;
			try {
				df = score.getRSlave().getStatus().getDiskSpaceAvailable();
				if (df < _minfreespace) {
					if (_assign == 0) {
						iter.remove();
					} else {
						score.addScore(_assign);
					}
				}
			} catch (RemoteException e) {
				score.getRSlave().handleRemoteException(e);
				iter.remove();
			} catch (NoAvailableSlaveException e) {
				iter.remove();
			}
		}
	}
}
