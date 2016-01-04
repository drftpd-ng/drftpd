/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 *
 * DrFTPD is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * DrFTPD; if not, write to the Free Software Foundation, Inc., 59 Temple Place,
 * Suite 330, Boston, MA 02111-1307 USA
 */
package org.drftpd.plugins.trialmanager.types.grouptop;

import java.util.ArrayList;

import org.drftpd.usermanager.User;

/**
 * @author CyBeR
 * @version $Id: TopTrialEvent.java 1925 2009-06-15 21:46:05Z tdsoul $
 */
public class GroupTopEvent {

	private String _name;
	private ArrayList<User> _users;
	private int _period;
	private String _periodstr;
	private int _keep;
	private long _min;

	public GroupTopEvent(String name, ArrayList<User> users, int period, int keep, long min) {
		_name = name;
		_users = users;
		_period = period;
		_keep = keep;
		_min = min;
		
		switch (_period) {
			case 1: _periodstr = "MONTHUP"; break;
			case 2: _periodstr = "WKUP"; break;
			case 3: _periodstr = "DAYUP"; break;
			default: _periodstr = "UNKNOWN"; break;
		}
	}

	public String getName() {
		return _name;
	}
	
	public ArrayList<User> getUsers() {
		return _users;
	}
	
	public int getPeriod() {
		return _period;
	}

	public String getPeriodStr() {
		return _periodstr;
	}
	
	public int getKeep() {
		return _keep;
	}
	
	public long getMin() {
		return _min;
	}
}
