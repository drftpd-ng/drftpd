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
public class UserEvent extends Event {
	User user;
	public UserEvent(User user, String command) {
		this(user, command, System.currentTimeMillis());
	}
	public UserEvent(User user, String command, long time) {
		super(command, time);
		this.user = user;
	}

	public User getUser() {
		return user;
	}

	public String toString() {
		return getClass().getName()+"[user="+getUser()+",cmd="+getCommand()+"]";
	}
}
