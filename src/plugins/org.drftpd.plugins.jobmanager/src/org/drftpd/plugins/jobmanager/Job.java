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
package org.drftpd.plugins.jobmanager;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;


import org.apache.log4j.Logger;
import org.drftpd.GlobalContext;
import org.drftpd.exceptions.FileExistsException;
import org.drftpd.exceptions.NoAvailableSlaveException;
import org.drftpd.exceptions.ObjectNotFoundException;
import org.drftpd.exceptions.SlaveUnavailableException;
import org.drftpd.master.RemoteSlave;
import org.drftpd.master.SlaveManager;
import org.drftpd.slave.RemoteIOException;
import org.drftpd.vfs.FileHandle;

/**
 * @author zubov
 * @author mog
 * @version $Id$
 */
public class Job {
	private static long jobIndexCount = 0;

	private static final Logger logger = Logger.getLogger(Job.class);

	private Set<String> _destSlaves;

	private FileHandle _file;

	private int _priority;

	private SlaveTransfer _slaveTransfer;

	private long _timeCreated;

	private long _timeSpent;
	
	private int _originalTransferNum;

	private int _transferNum;

	private long _index;

	private boolean _onlyCountOnlineSlaves;
	
	/**
	 * 	A flag declaring whether the delete operation at the end of the Job transfer has been done or not
	 */
	private boolean _deleteDone;
	
	public Job(FileHandle file, int priority, int transferNum, Collection<RemoteSlave> destSlaves) {
		this(file, priority, transferNum);
		HashSet<String> slaves = new HashSet<String>();
		for (RemoteSlave rslave : destSlaves) {
			slaves.add(rslave.getName());
		}
		_destSlaves = slaves;
	}

	public Job(FileHandle file, Collection<String> destSlaves,
			int priority, int transferNum) {
		this(file, destSlaves, priority, transferNum, false);
	}
	
	/**
	 * This does NOT create a complete Job, it lacks only the destination Slave list
	 * @param file
	 * @param priority
	 * @param transfernum
	 */
	private Job(FileHandle file, int priority, int transferNum) {
		_index = jobIndexCount++;
		_timeCreated = System.currentTimeMillis();
		_timeSpent = 0;
		_slaveTransfer = null;
		_deleteDone = false;
		_file = file;
		_priority = priority;
		_transferNum = transferNum;
		_originalTransferNum = transferNum;
		_onlyCountOnlineSlaves = false;
		_destSlaves = null;
	}

	public Job(FileHandle file, Collection<String> destSlaves,
			int priority, int transferNum, boolean onlyCountOnlineSlaves) {
		this(file, priority, transferNum);
		_destSlaves = new HashSet<String>(destSlaves);
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
	
	public Collection<String> getSlavesToTransferTo() throws FileNotFoundException {
		ArrayList<String> toTransferTo = new ArrayList<String>(_destSlaves);
		toTransferTo.removeAll(getFile().getSlaveNames());
		return toTransferTo;
	}
	
	/**
	 * Once the files have have been sent, this method is called. This
	 * is where files are possibly deleted from slaves.
	 */
	public void cleanup() {
		try {
				// remove slaves that aren't in the destination list
				for (RemoteSlave rslave : new ArrayList<RemoteSlave>(getFile().getSlaves())) {
					if (!_destSlaves.contains(rslave.getName())) {
						rslave.simpleDelete(getFile().getPath());
						getFile().removeSlave(rslave);
					}
				}
				// remove slaves if they aren't online and we have too many copies
				for (RemoteSlave rslave : new ArrayList<RemoteSlave>(getFile()
					.getSlaves())) {
				if (getFile().getSlaves().size() <= _originalTransferNum) {
					return;
				}
				if (!rslave.isAvailable()) {
					rslave.simpleDelete(getFile().getPath());
					getFile().removeSlave(rslave);
				}
			}
				// remove slaves if they are online and we have too many copies
				for (RemoteSlave rslave : new ArrayList<RemoteSlave>(getFile().getSlaves())) {
					if (getFile().getSlaves().size() <= _originalTransferNum) {
						return;
					}
					rslave.simpleDelete(getFile().getPath());
					getFile().removeSlave(rslave);
				}
			} catch (FileNotFoundException e) {
				// couldn't find the file that was referenced, unsure of what to
				// do now
				// probably can just leave it alone
			} finally {
				_deleteDone = true;
			}
	}

	
	public Collection<RemoteSlave> getSlaveObjects(Collection<String> names) throws ObjectNotFoundException {
		ArrayList<RemoteSlave> slaves = new ArrayList<RemoteSlave>();
		ArrayList<String> stringSlaves = new ArrayList<String>(names);
		for (String rslave : stringSlaves) {
			try {
				slaves.add(GlobalContext.getGlobalContext().getSlaveManager().getRemoteSlave(rslave));
			} catch (ObjectNotFoundException e) {
				// have to abort, destination slave no longer exists
				abort();
				throw new ObjectNotFoundException("Slave " + rslave + " no longer exists");
			}
		}
		return slaves;
	}
	
	public Collection<String> getSlaveNames(Collection<RemoteSlave> names) {
		ArrayList<RemoteSlave> slaves = new ArrayList<RemoteSlave>(names);
		ArrayList<String> stringSlaves = new ArrayList<String>();
		for (RemoteSlave rslave : slaves) {
				stringSlaves.add(rslave.getName());
		}
		return stringSlaves;
	}

	/**
	 * Returns an unmodifiable List of slaves that can be used with.
	 * {@see net.sf.drftpd.master.SlaveManagerImpl#getASlave(Collection, char, FtpConfig)}
	 */
	public Set<String> getDestinationSlaves() {
		if (_onlyCountOnlineSlaves) {
			HashSet<String> onlineDestinationSlaves = new HashSet<String>();
			for (String rslave : new HashSet<String>(_destSlaves)) {
				RemoteSlave remoteSlave;
				try {
					remoteSlave = GlobalContext.getGlobalContext().getSlaveManager().getRemoteSlave(rslave);
					if (remoteSlave.isAvailable()) {
						onlineDestinationSlaves.add(rslave);
					}
				} catch (ObjectNotFoundException e) {
					// Slave doesn't exist anymore in List, I guess we just abort?
					abort();
				}
			}
			return Collections.unmodifiableSet(onlineDestinationSlaves);
		}
		return Collections.unmodifiableSet(_destSlaves);
	}

	public void abort() {
		_deleteDone = true;
		_transferNum = 0;
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
	 * returns true if this job has nothing more to send and the delete operation has finished
	 */
	public boolean isDone() {
		return _transferNum == 0 && _deleteDone;
	}

	public boolean isTransferring() {
		return _slaveTransfer != null;
	}

	private String outputDestinationSlaves() {
		String toReturn = "";

		for (String rslave : new HashSet<String>(_destSlaves)) {
			toReturn = toReturn + rslave + ",";
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

	public synchronized void sentToSlave(RemoteSlave slave) throws FileNotFoundException {
		if (_destSlaves.contains(slave.getName())) {
			_transferNum--;
		} else {
			throw new IllegalArgumentException("Slave " + slave.getName()
					+ " does not exist as a destination slave for job " + this);
		}

		if (getSlavesToTransferTo().isEmpty() && (_transferNum > 0)) {
			throw new IllegalStateException(
					"Job cannot have a destSlaveSet of size 0 with transferNum > 0");
		}
		if (_transferNum == 0) {
			cleanup();
		}
	}

	public String toString() {
		return "Job[index=" + _index + "][file=" + getFile() + ",dest=["
				+ outputDestinationSlaves() + "],transferNum=" + _transferNum
				+ ",priority=" + getPriority() + "],deleteDone=" + _deleteDone;
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
					String index = SlaveManager.getBasicIssuer().issueChecksumToSlave(destSlave, getFile().getPath());
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
		try {
			sentToSlave(getDestinationSlave());
		} catch (FileNotFoundException e) {
			getSourceSlave().simpleDelete(getFile().getPath());
			abort();
		}
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

	public Collection<? extends RemoteSlave> getDestinationSlaveObjects() throws ObjectNotFoundException {
		return getSlaveObjects(getDestinationSlaves());
	}
}
