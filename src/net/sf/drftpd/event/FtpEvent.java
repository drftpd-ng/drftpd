/*
 * Created on 2003-aug-03
 *
 * To change the template for this generated file go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
package net.sf.drftpd.event;

import net.sf.drftpd.master.usermanager.User;

/**
 * @author mog
 *
 * To change the template for this generated type comment go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
public class FtpEvent {
	User user;
	String request;
	long time;

	public FtpEvent(User user, String command) {
		this(user, command, System.currentTimeMillis());
	}
	public FtpEvent(User user, String command, long time) {
		this.user = user;
		this.request = command;
		this.time = time;
	}

	/**
	 * 
	 */
	public FtpEvent() {
		super();
	}

	public String getCommand() {
		return request;
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
	public long getTime() {
		return time;
	}
	
	public String toString() {
		return getClass().getName()+"[user="+getUser()+",cmd="+getCommand()+"]";
	}
}
