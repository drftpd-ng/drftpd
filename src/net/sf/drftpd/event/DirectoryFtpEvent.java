package net.sf.drftpd.event;

import net.sf.drftpd.master.usermanager.User;
import net.sf.drftpd.remotefile.LinkedRemoteFile;

/**
 * @author mog
 *
 * @version $Id: DirectoryFtpEvent.java,v 1.4 2003/12/23 13:38:18 mog Exp $
 */
public class DirectoryFtpEvent extends UserEvent {
	private LinkedRemoteFile directory;
	
	public DirectoryFtpEvent(User user, String command, LinkedRemoteFile directory) {
		this(user, command, directory, System.currentTimeMillis());
	}
	
	public DirectoryFtpEvent(User user, String command, LinkedRemoteFile directory, long time) {
		super(user, command, time);
		this.directory = directory;
	}
	
	public LinkedRemoteFile getDirectory() {
		return directory;
	}

	public String toString() {
		return getClass().getName()+"[user="+getUser()+",cmd="+getCommand()+",directory="+directory.getPath()+"]";
	}
}
