/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 * 
 * DrFTPD is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 * 
 * DrFTPD is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with DrFTPD; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
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
 * @version $Id: UserEvent.java,v 1.9 2004/02/10 00:03:05 mog Exp $
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
