/*
 * Created on 2003-aug-03
 *
 * To change the template for this generated file go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
package net.sf.drftpd.event;

import net.sf.drftpd.event.listeners.Trial;
import net.sf.drftpd.master.usermanager.StaticUser;
import net.sf.drftpd.master.usermanager.User;

/**
 * @author mog
 *
 * Dispatched for LOGIN, LOGOUT and RELOAD.
 * 
 * Subclassed for events that are paired with a user object.
 */
public class UserEvent extends Event {
	public static String getCommandFromPeriod(int period) {
		String cmd;
		switch (period) {
			case Trial.PERIOD_DAILY :
				return "RESETDAY";
			case Trial.PERIOD_MONTHLY :
				return "RESETMONTH";
			case Trial.PERIOD_WEEKLY :
				return "RESETWEEK";
			default :
				throw new RuntimeException();
		}
	}
	public UserEvent(StaticUser user, int period, long time) {
		this(user, getCommandFromPeriod(period), time);
	}
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
		return getClass().getName()
			+ "[user="
			+ getUser()
			+ ",cmd="
			+ getCommand()
			+ "]";
	}
}
