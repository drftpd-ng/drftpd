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
package org.drftpd.slaveselection.def;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.rmi.RemoteException;
import java.util.Collection;
import java.util.Iterator;
import java.util.Properties;

import net.sf.drftpd.Bytes;
import net.sf.drftpd.NoAvailableSlaveException;
import net.sf.drftpd.master.BaseFtpConnection;
import net.sf.drftpd.master.RemoteSlave;
import net.sf.drftpd.master.SlaveManagerImpl;
import net.sf.drftpd.master.config.FtpConfig;
import net.sf.drftpd.mirroring.Job;
import net.sf.drftpd.remotefile.LinkedRemoteFileInterface;
import net.sf.drftpd.slave.SlaveStatus;
import net.sf.drftpd.slave.Transfer;

import org.drftpd.slaveselection.SlaveSelectionManagerInterface;

/**
 * @author mog
 * @version $Id: SlaveSelectionManager.java,v 1.3 2004/02/27 14:12:32 zubov Exp $
 */
public class SlaveSelectionManager implements SlaveSelectionManagerInterface {

	private long _minfreespace;

	private SlaveManagerImpl _sm;

	public SlaveSelectionManager(SlaveManagerImpl sm)
		throws FileNotFoundException, IOException {
		super();
		_sm = sm;
		Properties p = new Properties();
		p.load(new FileInputStream("conf/slaveselection-old.conf"));
		_minfreespace = Bytes.parseBytes(FtpConfig.getProperty(p, "minfreespace"));
	}

	public void reload() throws FileNotFoundException, IOException {
		// nothing to reload
	}

	public RemoteSlave getASlave(
		Collection rslaves,
		char direction,
		BaseFtpConnection conn,
		LinkedRemoteFileInterface file)
		throws NoAvailableSlaveException {
		return getASlaveInternal(rslaves, direction);
	}

	public RemoteSlave getASlaveForMaster(LinkedRemoteFileInterface file, FtpConfig cfg)
		throws NoAvailableSlaveException {
return getASlaveInternal(file.getAvailableSlaves(), Transfer.TRANSFER_SENDING_DOWNLOAD);
	}

	public SlaveManagerImpl getSlaveManager() {
		return _sm;
	}

	public RemoteSlave getASlaveForJobDownload(Job job, RemoteSlave destslave)
		throws NoAvailableSlaveException {
			return getASlaveInternal(job.getFile().getAvailableSlaves(), Transfer.TRANSFER_SENDING_DOWNLOAD);
	}

	private RemoteSlave getASlaveInternal(
		Collection slaves,
		char direction)
		throws NoAvailableSlaveException {
		RemoteSlave bestslave;
		SlaveStatus beststatus;
		{
			Iterator i = slaves.iterator();
			int bestthroughput;

			while (true) {
				if (!i.hasNext())
					throw new NoAvailableSlaveException();
				bestslave = (RemoteSlave) i.next();
				try {
					try {
						beststatus = bestslave.getStatus();
						// throws NoAvailableSlaveException
					} catch (NoAvailableSlaveException ex) {
						continue;
					}
					bestthroughput = beststatus.getThroughputDirection(direction);
					break;
				} catch (RemoteException ex) {
					bestslave.handleRemoteException(ex);
					continue;
				}
			}
			while (i.hasNext()) {
				RemoteSlave slave = (RemoteSlave) i.next();
				SlaveStatus status;

				try {
					status = slave.getStatus();
				} catch (RemoteException ex) {
					slave.handleRemoteException(ex);
					continue;
				} catch (NoAvailableSlaveException ex) { // throws NoAvailableSlaveException
					continue;
				}

				int throughput = status.getThroughputDirection(direction);

				if (beststatus.getDiskSpaceAvailable()
					< _minfreespace
					&& beststatus.getDiskSpaceAvailable()
						< status.getDiskSpaceAvailable()) {
					// best slave has less space than "freespace.min" &&
					// best slave has less space available than current slave 
					bestslave = slave;
					bestthroughput = throughput;
					beststatus = status;
					continue;
				}

				if (status.getDiskSpaceAvailable()
					< _minfreespace) {
					// current slave has less space available than "freespace.min"
					// above check made sure bestslave has more space than us
					continue;
				}

				if (throughput == bestthroughput) {
					if (direction == Transfer.TRANSFER_RECEIVING_UPLOAD) {
						if (bestslave.getLastUploadReceiving()
							> slave.getLastUploadReceiving()) {
							bestslave = slave;
							bestthroughput = throughput;
							beststatus = status;
						}
					} else if (
						direction == Transfer.TRANSFER_SENDING_DOWNLOAD) {
						if (bestslave.getLastDownloadSending()
							> slave.getLastDownloadSending()) {
							bestslave = slave;
							bestthroughput = throughput;
							beststatus = status;
						}
					} else if (direction == Transfer.TRANSFER_THROUGHPUT) {
						if (bestslave.getLastTransfer()
							> slave.getLastTransfer()) {
							bestslave = slave;
							bestthroughput = throughput;
							beststatus = status;
						}
					}
				}
				if (throughput < bestthroughput) {
					bestslave = slave;
					bestthroughput = throughput;
					beststatus = status;
				}
			}
		}
		if (direction == Transfer.TRANSFER_RECEIVING_UPLOAD) {
			bestslave.setLastUploadReceiving(System.currentTimeMillis());
		} else if (direction == Transfer.TRANSFER_SENDING_DOWNLOAD) {
			bestslave.setLastDownloadSending(System.currentTimeMillis());
		} else {
			bestslave.setLastUploadReceiving(System.currentTimeMillis());
			bestslave.setLastDownloadSending(System.currentTimeMillis());
		}
		return bestslave;
	}
}
