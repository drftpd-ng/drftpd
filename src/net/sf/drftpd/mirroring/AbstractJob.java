package net.sf.drftpd.mirroring;

import java.util.ArrayList;
import java.util.List;

import net.sf.drftpd.master.RemoteSlave;
import net.sf.drftpd.master.usermanager.User;
import net.sf.drftpd.remotefile.LinkedRemoteFile;

/**
 * @author zubov
 * @version $Id: AbstractJob.java,v 1.5 2004/01/12 08:24:24 zubov Exp $
 */
public class AbstractJob extends Job {
	private ArrayList _destSlaves;
	private LinkedRemoteFile _file;
	private User _owner;
	private int _priority;
	private Object _source;
	private long _timeCreated;
	private long _timeSpent;
	private boolean _done;

	public AbstractJob(
		LinkedRemoteFile file,
		List destSlaves,
		Object source,
		User owner,
		int priority) {
		super();
		_destSlaves = (ArrayList) destSlaves;
		_file = file;
		_source = source;
		_owner = owner;
		_priority = priority;
		_timeCreated = System.currentTimeMillis();
		_timeSpent = 0;
		_done = false;
	}
	
	public synchronized void addSlaves(List list) {
		_destSlaves.addAll(list);
	}
	
	public void addTimeSpent(long time) {
		_timeSpent += time;
	}
	
	public List getDestinationSlaves() {
		return _destSlaves;
	}
	
	public LinkedRemoteFile getFile() {
		return _file;
	}

	public User getOwner() {
		return _owner;
	}

	public int getPriority() {
		return _priority;
	}

	public Object getSource() {
		return _source;
	}
	public long getTimeCreated() {
		return _timeCreated;
	}
	public long getTimeSpent() {
		return _timeSpent;
	}
	
	public boolean isDone() {
		return _done;
	}
	
	public void setDone() {
		_done = true;
	}

	public String toString() {
		String toReturn = "Job[file=" + getFile().getPath() + ",dest="+getDestinationSlaves()+",owner=";
		if ( getOwner() != null ) {
			toReturn += getOwner().getUsername();
		}
		else {
			toReturn += "null";
		}
		toReturn += "]";
		return toReturn;
	}

	public boolean removeDestinationSlave(RemoteSlave slave) {
		return _destSlaves.remove(slave);
	}

}
