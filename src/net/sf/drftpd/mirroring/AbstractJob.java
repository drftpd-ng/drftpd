/*
 * Created on Dec 9, 2003
 *
 * To change the template for this generated file go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
package net.sf.drftpd.mirroring;

import java.util.ArrayList;
import java.util.List;

import net.sf.drftpd.master.usermanager.User;
import net.sf.drftpd.remotefile.LinkedRemoteFile;

/**
 * @author zubov
 * @version $Id: AbstractJob.java,v 1.3 2003/12/23 13:38:21 mog Exp $
 */
public class AbstractJob extends Job {
	private ArrayList _destSlaves;
	private LinkedRemoteFile _file;
	private User _owner;
	private int _priority;
	private Object _source;
	private long _timeCreated;
	private long _timeSpent;

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
}
