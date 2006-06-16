/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 *
 * DrFTPD is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * DrFTPD; if not, write to the Free Software Foundation, Inc., 59 Temple Place,
 * Suite 330, Boston, MA 02111-1307 USA
 */
package net.sf.drftpd.mirroring;

import java.io.FileNotFoundException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import net.sf.drftpd.FileExistsException;
import net.sf.drftpd.NoAvailableSlaveException;
import net.sf.drftpd.SlaveUnavailableException;

import org.apache.log4j.Logger;
import org.drftpd.master.RemoteSlave;
import org.drftpd.slave.RemoteIOException;
import org.drftpd.vfs.FileHandle;

/**
 * @author zubov
 * @author mog
 * @version $Id$
 */
public class Job {
	private static long jobIndexCount = 1;

	private static final Logger logger = Logger.getLogger(Job.class);

	private Set<RemoteSlave> _destSlaves;

	private FileHandle _file;

	private int _priority;

	private SlaveTransfer _slaveTransfer;

	private long _timeCreated;

	private long _timeSpent;

	private int _transferNum;

	private long _index;

	private boolean _onlyCountOnlineSlaves;

	public Job(FileHandle file, Collection<RemoteSlave> destSlaves,
			int priority, int transferNum) {
		this(file, destSlaves, priority, transferNum, false);
	}

	public Job(FileHandle file, Collection<RemoteSlave> destSlaves,
			int priority, int transferNum, boolean onlyCountOnlineSlaves) {
		_index = jobIndexCount++;
		_destSlaves = new HashSet<RemoteSlave>(destSlaves);
		_file = file;
		_priority = priority;
		_timeCreated = System.currentTimeMillis();
		_timeSpent = 0;
		_transferNum = transferNum;
		_slaveTransfer = null;
		_onlyCountOnlineSlaves = onlyCountOnlineSlaves;
		if (_transferNum > destSlaves.size()) {
			throw new IllegalArgumentException(
					"transferNum cannot be greater than destSlaves.size()");
		}

		if (_transferNum <= 0) {
			throw new IllegalArgumentException(
					"transferNum must be greater than 0");
		}
	}

	public void addTimeSpent(long time) {
		_timeSpent += time;
	}

	/**
	 * Returns an unmodifiable List of slaves that can be used with.
	 * {@see net.sf.drftpd.master.SlaveManagerImpl#getASlave(Collection, char, FtpConfig)}
	 */
	public Set<RemoteSlave> getDestinationSlaves() {
		if (_onlyCountOnlineSlaves) {
			HashSet<RemoteSlave> onlineDestinationSlaves = new HashSet<RemoteSlave>();
			for (RemoteSlave rslave : new HashSet<RemoteSlave>(_destSlaves)) {
				if (rslave.isAvailable()) {
					onlineDestinationSlaves.add(rslave);
				}
			}
			return onlineDestinationSlaves;
		}
		return Collections.unmodifiableSet(_destSlaves);
	}

	/**
	 * Returns the file for this job. This file is used to tell the slaves what
	 * file to transfer & receive.
	 */
	public FileHandle getFile() {
		return _file;
	}

	/**
	 * Returns the priority of this job.
	 */
	public int getPriority() {
		return _priority;
	}

	public synchronized long getProgress() {
		if (!isTransferring()) {
			throw new IllegalStateException(this + " is not transferring");
		}

		return _slaveTransfer.getTransfered();
	}

	public synchronized long getSpeed() {
		if (!isTransferring()) {
			throw new IllegalStateException(this + " is not transferring");
		}

		return _slaveTransfer.getXferSpeed();
	}

	public synchronized RemoteSlave getSourceSlave() {
		if (!isTransferring()) {
			throw new IllegalStateException(this + " is not transferring");
		}
		return _slaveTransfer.getSourceSlave();
	}

	/**
	 * This is the time that the job was created
	 */
	public long getTimeCreated() {
		return _timeCreated;
	}

	/**
	 * This is the amount of time spent processing this job
	 */
	public long getTimeSpent() {
		return _timeSpent;
	}

	/**
	 * returns true if this job has nothing more to send
	 */
	public boolean isDone() {
		return _transferNum < 1;
	}

	public boolean isTransferring() {
		return _slaveTransfer != null;
	}

	private String outputDestinationSlaves() {
		String toReturn = "";

		for (RemoteSlave rslave : new HashSet<RemoteSlave>(_destSlaves)) {
			toReturn = toReturn + rslave.getName() + ",";
		}
		if (!toReturn.equals("")) {
			return toReturn.substring(0, toReturn.length() - 1);
		}
		return null;
	}

	private synchronized void reset() {
		if (_slaveTransfer != null) {
			_slaveTransfer.abort("Resetting slave2slave Transfer");
			_slaveTransfer = null;
		}
	}

	public synchronized void sentToSlave(RemoteSlave slave) {
		if (_destSlaves.remove(slave)) {
			_transferNum--;
		} else {
			throw new IllegalArgumentException("Slave " + slave.getName()
					+ " does not exist as a destination slave for job " + this);
		}

		if (_destSlaves.isEmpty() && (_transferNum > 0)) {
			throw new IllegalStateException(
					"Job cannot have a destSlaveSet of size 0 with transferNum > 0");
		}
	}

	public void setDone() {
		_transferNum = 0;
	}

	public String toString() {
		return "Job[index=" + _index + "][file=" + getFile() + ",dest=["
				+ outputDestinationSlaves() + "],transferNum=" + _transferNum
				+ ",priority=" + getPriority() + "]";
	}

	/**
	 * Returns true if transfer was completed successfully
	 * 
	 * @param checkCRC
	 * @param sourceSlave
	 * @param destSlave
	 * @return
	 * @throws FileNotFoundException
	 */

    public void transfer(boolean checkCRC, boolean secureTransfer, RemoteSlave sourceSlave,
			RemoteSlave destSlave) throws FileNotFoundException {
		synchronized (this) {
			if (_slaveTransfer != null) {
				throw new IllegalStateException("Job is already transferring");
			}
			if (getFile().getSlaves().contains(destSlave)) {
				throw new IllegalStateException(
						"File already exists on target slave");
			}
			_slaveTransfer = new SlaveTransfer(getFile(), sourceSlave,
					destSlave, secureTransfer);
		}

		logger.info("Sending " + getFile().getName() + " from "
				+ sourceSlave.getName() + " to " + destSlave.getName());
		long startTime = System.currentTimeMillis();
		try {
			boolean crcMatch = _slaveTransfer.transfer();
			if (crcMatch || !checkCRC) {
				logSuccess();
			} else {
				destSlave.simpleDelete(getFile().getPath());
				logger.debug("CRC did not match for " + getFile()
						+ " when sending from " + sourceSlave.getName()
						+ " to " + destSlave.getName());
			}
		} catch (DestinationSlaveException e) {
			if (e.getCause() instanceof FileExistsException) {
				logger.debug("Caught FileExistsException in sending "
						+ getFile().getName() + " from "
						+ sourceSlave.getName() + " to " + destSlave.getName(),
						e);
				long remoteChecksum = 0;
				long localChecksum = 0;

				try {
					String index = destSlave.issueChecksumToSlave(getFile()
							.getPath());
					remoteChecksum = destSlave.fetchChecksumFromIndex(index);
				} catch (SlaveUnavailableException e2) {
					logger.debug("SlaveUnavailableException from ", e2);
					destSlave.simpleDelete(getFile().getPath());
					return;
				} catch (RemoteIOException e3) {
					logger.debug("RemoteIOException from ", e3);
					destSlave.simpleDelete(getFile().getPath());
					return;
				}

				try {
					localChecksum = getFile().getCheckSum();
				} catch (NoAvailableSlaveException e4) {
					// File exists locally, but I can't verify it's checksum
					// Accept the new one since there's no cached checksum
					logger
							.debug("Accepting file because there's no local checksum");
					// successful transfer
					getFile().setCheckSum(remoteChecksum);
					logSuccess();
					return;
				}
				if (remoteChecksum == localChecksum) {
					logger.debug("Accepting file because the crc's match");
					// successful transfer
					logSuccess();
				} else {
					logger
							.debug("Checksum did not match, removing offending file");
					destSlave.simpleDelete(getFile().getPath());
				}
				return;
			} else {
				logger.error("Error on slave during slave2slave transfer", e);
			}
			destSlave.simpleDelete(getFile().getPath());
		} catch (SourceSlaveException e) {
			if (e.getCause() instanceof FileNotFoundException) {
				logger.warn("Caught FileNotFoundException in sending "
						+ getFile().getName() + " from "
						+ sourceSlave.getName() + " to " + destSlave.getName(),
						e);
				getFile().removeSlave(sourceSlave);
				return;
			} else {
				logger.error("Error on slave during slave2slave transfer", e);
			}
		} catch (SlaveException e) {
			throw new RuntimeException(
					"SlaveException was not of type DestinationSlaveException or SourceSlaveException");
		} finally {
			addTimeSpent(System.currentTimeMillis() - startTime);
			reset();
		}
	}

	private void logSuccess() {
		try {
			getFile().addSlave(getDestinationSlave());
		} catch (FileNotFoundException e) {
			logger
					.error(
							"File was sent with the JobManager but the file was deleted or moved during the send",
							e);
		}
		logger.debug("Sent file " + getFile().getName() + " from "
				+ getSourceSlave().getName() + " to "
				+ getDestinationSlave().getName());
		sentToSlave(getDestinationSlave());
	}

	public synchronized RemoteSlave getDestinationSlave() {
		if (!isTransferring()) {
			throw new IllegalStateException(this + " is not transferring");
		}
		return _slaveTransfer.getDestinationSlave();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	public boolean equals(Object arg0) {
		if (arg0 instanceof Job) {
			return _file == ((Job) arg0)._file;
		}
		return super.equals(arg0);
	}

	public long getIndex() {
		return _index;
	}
}
