package net.sf.drftpd.mirroring;

import java.util.List;

import net.sf.drftpd.master.usermanager.User;
import net.sf.drftpd.remotefile.LinkedRemoteFile;

/**
 * @author mog
 * @version $Id: Job.java,v 1.3 2004/01/05 00:14:20 mog Exp $
 */
public abstract class Job {

	public Job() {
		super();
	}
	/**
	 * Add destination slaves to this job
	 */
	public abstract void addSlaves(List list);
	public abstract void addTimeSpent(long time);

	/**
	 * Returns a List of slaves that can be used with {@see net.sf.drftpd.master.SlaveManagerImpl#getASlave(Collection, char, FtpConfig)}
	 */
	public abstract List getDestinationSlaves();

	/**
	 * Returns the file (or directory, if directories can be submitted as jobs,) for this job.
	 * This file is used to tell the slaves what file to transfer & receive.
	 */
	public abstract LinkedRemoteFile getFile();

	/**
	 * Returns user that is the owner of this file/job or null (or exception) if not applicable.
	 */
	public abstract User getOwner();

	/**
	 * Returns the priority of this job.
	 */
	public abstract int getPriority();

	/**
	 * Instance that submitted this object.
	 * 
	 * For example so that Archive instance can see if this job was submitted by itself
	 * 
	 * @see java.util.EventObject#getSource()
	 */
	public abstract Object getSource();

	/**
	 * This is the time that the job was created
	 */
	public abstract long getTimeCreated();
	/**
	 * This is the amount of time spent processing this job
	 */
	public abstract long getTimeSpent();
}