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
 * @version $Id: UserEvent.java,v 1.8 2004/01/22 01:41:51 zubov Exp $
 */
public class UserEvent extends Event {
	public static String getCommandFromPeriod(int period) {
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
	User user;
	public UserEvent(StaticUser user, int period, long time) {
		this(user, getCommandFromPeriod(period), time);
	}
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

	public void setUser(User user) {
		this.user = user;
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
