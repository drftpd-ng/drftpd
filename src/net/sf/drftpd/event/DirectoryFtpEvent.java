/*
 * Created on 2003-jun-29
 *
 * To change the template for this generated file go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
package net.sf.drftpd.event;

import net.sf.drftpd.master.usermanager.User;
import net.sf.drftpd.remotefile.LinkedRemoteFile;

/**
 * @author <a href="mailto:drftpd@mog.se">Morgan Christiansson</a>
 *
 * To change the template for this generated type comment go to
 * Window>Preferences>Java>Code Generation>Code and Comments
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
	
	/**
		 * @return
		 */
	public LinkedRemoteFile getDirectory() {
		return directory;
	}

	public String toString() {
		return getClass().getName()+"[user="+getUser()+",cmd="+getCommand()+",directory="+directory.getPath()+"]";
	}
}
