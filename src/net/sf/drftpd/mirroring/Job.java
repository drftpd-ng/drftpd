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
import java.util.Iterator;
import java.util.Set;

import net.sf.drftpd.FileExistsException;
import net.sf.drftpd.master.RemoteSlave;
import net.sf.drftpd.master.config.FtpConfig;
import net.sf.drftpd.remotefile.LinkedRemoteFileInterface;
/**
 * @author zubov
 * @author mog
 * @version $Id: Job.java,v 1.29 2004/07/12 14:24:18 zubov Exp $
 */
public class Job {
	private RemoteSlave _destSlave;
	private Set _destSlaves;
	private LinkedRemoteFileInterface _file;
	private int _priority;
	private SlaveTransfer _slaveTransfer;
	private RemoteSlave _sourceSlave;
	private long _timeCreated;
	private long _timeSpent;
	private int _transferNum;
	public Job(LinkedRemoteFileInterface file, Set destSlaves, int priority,
			int transferNum) {
		_destSlaves = new HashSet(destSlaves);
		_file = file;
		_priority = priority;
		_timeCreated = System.currentTimeMillis();
		_timeSpent = 0;
		_transferNum = transferNum;
		_slaveTransfer = null;
		if (_transferNum > destSlaves.size())
			throw new IllegalArgumentException(
					"transferNum cannot be greater than destSlaves.size()");
		if (_transferNum <= 0)
			throw new IllegalArgumentException(
					"transferNum must be greater than 0");
	}
	public void addTimeSpent(long time) {
		_timeSpent += time;
	}
	/**
	 * Returns a List of slaves that can be used with
	 * {@see net.sf.drftpd.master.SlaveManagerImpl#getASlave(Collection, char, FtpConfig)}
	 */
	public Set getDestinationSlaves() {
		return Collections.unmodifiableSet(_destSlaves);
	}
	public synchronized String getDestSlaveName() {
		if (!isTransferring()) {
			throw new IllegalStateException(this + " is not transferring");
		}
		return _destSlave.getName();
	}
	/**
	 * Returns the file (or directory, if directories can be submitted as jobs,)
	 * for this job. This file is used to tell the slaves what file to transfer &
	 * receive.
	 */
	public LinkedRemoteFileInterface getFile() {
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
	public synchronized String getSrcSlaveName() {
		if (!isTransferring()) {
			throw new IllegalStateException(this + " is not transferring");
		}
		return _sourceSlave.getName();
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
		return ((_transferNum < 1) || (_destSlaves.isEmpty()));
	}
	public boolean isTransferring() {
		return _slaveTransfer != null;
	}
	private String outputDestinationSlaves() {
		String toReturn = new String();
		for (Iterator iter = getDestinationSlaves().iterator(); iter.hasNext();) {
			RemoteSlave rslave = (RemoteSlave) iter.next();
			if (!iter.hasNext())
				return toReturn + rslave.getName();
			toReturn = toReturn + rslave.getName() + ",";
		}
		return toReturn;
	}
	public synchronized void sentToSlave(RemoteSlave slave) {
		if (_destSlaves.remove(slave)) {
			_transferNum--;
		} else {
			throw new IllegalArgumentException("Slave " + slave.getName()
					+ " does not exist as a destination slave for job " + this);
		}
		if (_destSlaves.isEmpty() && _transferNum > 0) {
			throw new IllegalStateException(
					"Job cannot have a destSlaveSet of size 0 with transferNum > 0");
		}
	}
	private synchronized void reset() {
		_slaveTransfer = null;
		_destSlave = null;
		_sourceSlave = null;
	}
 	public void setDone() {
		_transferNum = 0;
	}
	public String toString() {
		String toReturn = "Job[file=" + getFile().getName() + "]dest=["
				+ outputDestinationSlaves() + "]transferNum=[" + _transferNum
				+ "]";
		return toReturn;
	}
	public boolean transfer(boolean checkCRC, RemoteSlave sourceSlave,
			RemoteSlave destSlave) throws DestinationSlaveException,
			SourceSlaveException, FileNotFoundException, FileExistsException {
		synchronized (this) {
			if (_slaveTransfer != null) {
				throw new IllegalStateException("Job is already transferring");
			}
			_sourceSlave = sourceSlave;
			_destSlave = destSlave;
			_slaveTransfer = new SlaveTransfer(getFile(), _sourceSlave,
					_destSlave);
		}
		boolean result = false;
		try {
			result = _slaveTransfer.transfer(checkCRC);
		} catch (DestinationSlaveException e) {
			reset();
			throw e;
		} catch (SourceSlaveException e) {
			reset();
			throw e;
		} catch (FileNotFoundException e) {
			reset();
			throw e;
		} catch (FileExistsException e) {
			reset();
			throw e;
		}
		reset();
		return result;
	}
}