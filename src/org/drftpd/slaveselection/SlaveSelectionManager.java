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

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Properties;

import net.sf.drftpd.FatalException;
import net.sf.drftpd.NoAvailableSlaveException;
import net.sf.drftpd.master.BaseFtpConnection;
import net.sf.drftpd.master.RemoteSlave;
import net.sf.drftpd.master.SlaveManagerImpl;
import net.sf.drftpd.master.usermanager.User;
import net.sf.drftpd.mirroring.Job;
import net.sf.drftpd.remotefile.LinkedRemoteFileInterface;
import net.sf.drftpd.slave.Transfer;

import org.apache.log4j.Logger;

/**
 * @author mog
 * @version $Id: SlaveSelectionManager.java,v 1.2 2004/02/23 01:14:41 mog Exp $
 */
public class SlaveSelectionManager {
	private static final Logger logger =
		Logger.getLogger(SlaveSelectionManager.class);
	private SlaveManagerImpl _sm;
	private String _cfgfileName;
	private ArrayList _filters;
	protected SlaveSelectionManager() {
	}
	public SlaveSelectionManager(SlaveManagerImpl sm, String cfgFileName)
		throws FileNotFoundException, IOException {
		_sm = sm;
		_cfgfileName = cfgFileName;
		reload();
	}

	protected SlaveSelectionManager(SlaveManagerImpl sm, Properties p) {
		_sm = sm;
		reload(p);
	}

	public void reload() throws FileNotFoundException, IOException {
		Properties p = new Properties();
		p.load(new FileInputStream(_cfgfileName));
		reload(p);
	}

	private void reload(Properties p) {
		ArrayList filters = new ArrayList();
		int i = 1;
		for (;; i++) {
			String type = p.getProperty(i + ".filter");
			if (type == null)
				break;
			if (type.indexOf('.') == -1) {
				type =
					"org.drftpd.slaveselection."
						+ type.substring(0, 1).toUpperCase()
						+ type.substring(1)
						+ "Filter";
			}
			try {
				Class[] SIG =
					new Class[] {
						SlaveSelectionManager.class,
						int.class,
						Properties.class };

				Filter filter =
					(Filter) Class.forName(type).getConstructor(
						SIG).newInstance(
						new Object[] { this, new Integer(i), p });
				filters.add(filter);
			} catch (Exception e) {
				throw new FatalException(i + ".filter = " + type, e);
			}
		}
		if (i == 1)
			throw new IllegalArgumentException();
		filters.trimToSize();
		_filters = filters;
	}

	/**
	 * Checksums call us with null BaseFtpConnection.
	 */
	public RemoteSlave getASlave(
		Collection rslaves,
		char direction,
		BaseFtpConnection conn,
		LinkedRemoteFileInterface file)
		throws NoAvailableSlaveException {
		if (conn != null && direction != conn.getDirection())
			throw new RuntimeException("direction != conn.getDirection()");
		InetAddress source = conn != null ? conn.getClientAddress() : null;
		return process(
			new ScoreChart(rslaves),
			conn != null ? conn.getUserNull() : null,
			source,
			direction,
			file);
	}

	/**
	 * Get slave for transfer to master.
	 */
	public RemoteSlave getASlave(LinkedRemoteFileInterface file)
		throws NoAvailableSlaveException {
		return process(
			new ScoreChart(file.getAvailableSlaves()),
			null,
			null,
			Transfer.TRANSFER_SENDING_DOWNLOAD,
			file);
	}

	private RemoteSlave process(
		ScoreChart sc,
		User user,
		InetAddress peer,
		char direction,
		LinkedRemoteFileInterface file)
		throws NoAvailableSlaveException {
		for (Iterator iter = _filters.iterator(); iter.hasNext();) {
			Filter filter = (Filter) iter.next();
			filter.process(sc, user, peer, direction, file);
		}
		return sc.getBestSlave();
	}
	public SlaveManagerImpl getSlaveManager() {
		return _sm;
	}

	public RemoteSlave getASlave(Job temp, RemoteSlave destslave)
		throws NoAvailableSlaveException {
		return process(
			new ScoreChart(getSlaveManager().getAvailableSlaves()),
			null,
			destslave.getInetAddress(),
			Transfer.TRANSFER_SENDING_DOWNLOAD,
			temp.getFile());
	}
}
