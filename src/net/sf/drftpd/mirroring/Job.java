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
package net.sf.drftpd.mirroring;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import net.sf.drftpd.master.RemoteSlave;
import net.sf.drftpd.master.usermanager.User;
import net.sf.drftpd.remotefile.LinkedRemoteFileInterface;

/**
 * @author zubov
 * @author mog
 * @version $Id: Job.java,v 1.23 2004/07/08 16:09:53 zubov Exp $
 */
public class Job {
	private int _transferNum;
	private Set _destSlaves;
	private LinkedRemoteFileInterface _file;
	private User _owner;
	private int _priority;
	private Object _source;
	private long _timeCreated;
	private long _timeSpent;

	public Job(
		LinkedRemoteFileInterface file,
		Set destSlaves,
		Object source,
		User owner,
		int priority,
		int transferNum) {
		super();
		_destSlaves = new HashSet(destSlaves);
		_file = file;
		_source = source;
		_owner = owner;
		_priority = priority;
		_timeCreated = System.currentTimeMillis();
		_timeSpent = 0;
		_transferNum = transferNum;
		if (_transferNum > destSlaves.size())
			throw new IllegalArgumentException("transferNum cannot be greater than destSlaves.size()");
	}

	public void addTimeSpent(long time) {
		_timeSpent += time;
	}

	/**
	 * Returns a List of slaves that can be used with {@see net.sf.drftpd.master.SlaveManagerImpl#getASlave(Collection, char, FtpConfig)}
	 */
	public Set getDestinationSlaves() {
		return Collections.unmodifiableSet(_destSlaves);
	}

	/**
	 * Returns the file (or directory, if directories can be submitted as jobs,) for this job.
	 * This file is used to tell the slaves what file to transfer & receive.
	 */
	public LinkedRemoteFileInterface getFile() {
		return _file;
	}

	/**
	 * Returns user that is the owner of this file/job or null (or exception) if not applicable.
	 */
	public User getOwner() {
		return _owner;
	}

	/**
	 * Returns the priority of this job.
	 */
	public int getPriority() {
		return _priority;
	}

	/**
	 * Instance that submitted this object.
	 * 
	 * For example so that Archive instance can see if this job was submitted by itself
	 * 
	 * @see java.util.EventObject#getSource()
	 */
	public Object getSource() {
		return _source;
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
		return _transferNum < 1 || _destSlaves.isEmpty();
	}

	public synchronized void sentToSlave(RemoteSlave slave) {
		if (_destSlaves.remove(slave)) {
			_transferNum--;
		} else {
			throw new IllegalArgumentException("Slave " + slave.getName() + " does not exist as a destination slave for job " + this);
		}
		if (_destSlaves.isEmpty() && _transferNum > 0) {
			throw new IllegalStateException("Job cannot have a destSlaveSet of size 0 with transferNum > 0");
		}
	}

	private String outputDestinationSlaves() {
		String toReturn = new String();
		for (Iterator iter = getDestinationSlaves().iterator();iter.hasNext();) {
			RemoteSlave rslave = (RemoteSlave) iter.next();
			if (!iter.hasNext())
				return toReturn + rslave.getName();
			toReturn = toReturn + rslave.getName() + ",";
		}
		return toReturn;
	}
	
	public String toString() {
		String toReturn =
			"Job[file="
				+ getFile().getName()
				+ "],dest=["
				+ outputDestinationSlaves()
				+ "],owner=";
		if (getOwner() != null) {
			toReturn += getOwner().getUsername();
		} else {
			toReturn += "null";
		}
		toReturn += "]";
		return toReturn;
	}

	public void setDone() {
		_transferNum = 0;
	}
}