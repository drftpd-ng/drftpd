package net.sf.drftpd.event;

import net.sf.drftpd.master.usermanager.User;

/**
 * Dispatched when a user is reset.
 * Usually dispatched with the command "RESETDAY", "RESETWEEK" or "RESETMONTH".
 * 
 * @deprecated use user.getLastReset() instead
 * @author mog
 * @version $Id: UserResetEvent.java,v 1.2 2003/11/13 22:55:06 mog Exp $
 */
public class UserResetEvent extends UserEvent {

	private long _lastreset;

//	public UserResetEvent(User user, String command, long lastreset) {
//		this(user, command, lastreset, System.currentTimeMillis());
//	}

	public UserResetEvent(User user, String command, long lastreset, long time) {
		super(user, command, time);
		_lastreset = lastreset;
	}
	
	/**
	 * Returns when the user was last reset.
	 * @return When the user was last reset
	 */
	public long getLastReset() {
		return _lastreset;
	}
}
