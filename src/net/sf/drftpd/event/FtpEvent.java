/*
 * Created on 2003-jun-29
 *
 * To change the template for this generated file go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
package net.sf.drftpd.event;

import net.sf.drftpd.master.usermanager.User;

/**
 * @author mog
 *
 * To change the template for this generated type comment go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
public class FtpEvent {
	User user;
	String command;
	
	FtpEvent(User user, String command) {
		this.user = user;
	}
	/**
	 * @return
	 */
	public String getCommand() {
		return command;
	}
}
