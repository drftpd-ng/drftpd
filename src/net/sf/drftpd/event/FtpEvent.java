/*
 * Created on 2003-jun-29
 *
 * To change the template for this generated file go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
package net.sf.drftpd.event;

import net.sf.drftpd.master.FtpRequest;
import net.sf.drftpd.master.usermanager.User;

/**
 * @author <a href="mailto:drftpd@mog.se">Morgan Christiansson</a>
 *
 * To change the template for this generated type comment go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
public class FtpEvent {
	User user;
	FtpRequest request;
	long time;
	
	FtpEvent(User user, FtpRequest request, String directory) {
		this(user, request, directory, System.currentTimeMillis());
	}
	FtpEvent(User user, FtpRequest request, String directory, long time) {
		this.user = user;
		this.request = request;
		this.time = time;
	}

	public String getCommand() {
		return request.getCommand();
	}
	/**
	 * @return
	 */
	public User getUser() {
		return user;
	}

	/**
	 * @return
	 */
	public FtpRequest getRequest() {
		return request;
	}

	/**
	 * @return
	 */
	public long getTime() {
		return time;
	}
	protected String directory;
	/**
		 * @return
		 */
	public String getDirectory() {
		return directory;
	}

}
