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
 * @author mog
 *
 * To change the template for this generated type comment go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
public class FtpEvent {
	User user;
	FtpRequest request;
	
	FtpEvent(User user, FtpRequest request) {
		this.user = user;
		this.request = request;
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

}
