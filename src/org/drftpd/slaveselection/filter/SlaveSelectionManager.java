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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Collection;

import net.sf.drftpd.NoAvailableSlaveException;
import net.sf.drftpd.master.BaseFtpConnection;
import net.sf.drftpd.master.RemoteSlave;
import net.sf.drftpd.master.SlaveManagerImpl;
import net.sf.drftpd.master.config.FtpConfig;
import net.sf.drftpd.master.usermanager.User;
import net.sf.drftpd.remotefile.LinkedRemoteFileInterface;
import net.sf.drftpd.slave.Transfer;

import org.apache.log4j.Logger;
import org.drftpd.slaveselection.SlaveSelectionManagerInterface;

/**
 * @author mog
 * @version $Id: SlaveSelectionManager.java,v 1.6 2004/03/01 04:21:04 zubov Exp $
 */
public class SlaveSelectionManager implements SlaveSelectionManagerInterface {
	private FilterChain _ssmiDown;
	private FilterChain _ssmiUp;
	private FilterChain _ssmiMaster;
	private FilterChain _ssmiJobUp;
	private FilterChain _ssmiJobDown;
	private static final Logger logger =
		Logger.getLogger(SlaveSelectionManager.class);
	private SlaveManagerImpl _sm;

	public SlaveSelectionManager(SlaveManagerImpl sm)
		throws FileNotFoundException, IOException {
		_sm = sm;
		reload();
	}

	public void reload() throws FileNotFoundException, IOException {
		_ssmiDown =
			new FilterChain(
				this,
				"conf/slaveselection-down.conf");
		_ssmiMaster =
			new FilterChain(
				this,
				"conf/slaveselection-master.conf");
		_ssmiUp =
			new FilterChain(
				this,
				"conf/slaveselection-up.conf");
				
			try {
				if (_sm.getConnectionManager().getJobManager() != null) {
				logger.debug("loading jobManagerSlaveSelection configs");
					_ssmiJobUp =
						new FilterChain(this,"conf/slaveselection-jobup.conf");
					_ssmiJobDown =
						new FilterChain(this,"conf/slaveselection-jobdown.conf");		
				}
			} catch (IllegalStateException e) {
			}
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
		InetAddress source = (conn != null ? conn.getClientAddress() : null);
		String status;
		if (direction == Transfer.TRANSFER_RECEIVING_UPLOAD) {
			status = "up";
		} else if (direction == Transfer.TRANSFER_SENDING_DOWNLOAD) {
			status = "down";
		} else {
			throw new IllegalArgumentException();
		}
		return process(
			status,
			new ScoreChart(rslaves),
			conn != null ? conn.getUserNull() : null,
			source,
			direction,
			file);
	}

	/**
	 * Get slave for transfer to master.
	 */
	public RemoteSlave getASlaveForMaster(
		LinkedRemoteFileInterface file,
		FtpConfig cfg)
		throws NoAvailableSlaveException {
		return process(
			"master",
			new ScoreChart(file.getAvailableSlaves()),
			null,
			null,
			Transfer.TRANSFER_SENDING_DOWNLOAD,
			file);
	}

	private RemoteSlave process(
		String filterchain,
		ScoreChart sc,
		User user,
		InetAddress peer,
		char direction,
		LinkedRemoteFileInterface file)
		throws NoAvailableSlaveException {
		FilterChain ssmi;
		if (filterchain.equals("down")) {
			ssmi = _ssmiDown;
		} else if (filterchain.equals("up")) {
			ssmi = _ssmiUp;
		} else if (filterchain.equals("master")) {
			ssmi = _ssmiMaster;
		} else if (filterchain.equals("jobup")) {
			ssmi = _ssmiJobUp;
		} else if (filterchain.equals("jobdown")) {
			ssmi = _ssmiJobDown;
		} else {
			throw new IllegalArgumentException();
		}
		return ssmi.getBestSlave(sc, user, peer, direction, file);
	}

	public SlaveManagerImpl getSlaveManager() {
		return _sm;
	}

	public RemoteSlave getASlaveForJobDownload(LinkedRemoteFileInterface file)
		throws NoAvailableSlaveException {
			return process(
				"jobdown",
				new ScoreChart(file.getAvailableSlaves()),
				null,
				null,
				Transfer.TRANSFER_SENDING_DOWNLOAD,
				file);
	}

	public RemoteSlave getASlaveForJobUpload(LinkedRemoteFileInterface file)
		throws NoAvailableSlaveException {
			Collection slaves = _sm.getAvailableSlaves();
			slaves.removeAll(file.getAvailableSlaves());
		return process(
			"jobup",
			new ScoreChart(slaves),
			null,
			null,
			Transfer.TRANSFER_SENDING_DOWNLOAD,
			file);

	}

}
