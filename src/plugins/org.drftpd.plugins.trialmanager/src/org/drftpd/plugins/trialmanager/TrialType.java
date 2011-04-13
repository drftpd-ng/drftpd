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
package org.drftpd.plugins.trialmanager;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Properties;
import java.util.ResourceBundle;

import org.apache.log4j.Logger;
import org.drftpd.GlobalContext;
import org.drftpd.commandmanager.CommandRequest;
import org.drftpd.commandmanager.CommandResponse;
import org.drftpd.master.cron.TimeManager;
import org.drftpd.permissions.Permission;
import org.drftpd.usermanager.User;
import org.drftpd.usermanager.util.UserComparator;

/**
 * @author CyBeR
 * @version $Id: TrialType.java 1925 2009-06-15 21:46:05Z CyBeR $
 */

public abstract class TrialType {
	protected static final Logger logger = Logger.getLogger(TrialType.class);
	
	private String _eventtype;
	private String _name;
	private String _pass;
	private String _fail;
	private int _period; // 3 = daily | 2 = weekly | 1 = monthly
	private String _periodstr;
	private Permission _perms;
	
	/*
	 * Loads all the .conf information for the specific type
	 */
	public TrialType(Properties p, int confnum, String type) {
		_eventtype = type;
        _name = p.getProperty(confnum + ".name","").trim();
        _pass = p.getProperty(confnum + ".pass","").trim();
        _fail = p.getProperty(confnum + ".fail","").trim();
        _perms = new Permission(p.getProperty(confnum + ".perms","").trim());
        
        if (_name.isEmpty()) {
        	throw new RuntimeException("Invalid Name for " + confnum + ".name - Skipping Config");
        }

        // Check to make sure the period is valid
    	try {
    		_period = Integer.parseInt(p.getProperty(confnum + ".period","0").trim());
    		if ((_period < 1) || (_period > 3)) {
    			throw new NumberFormatException("Period Out Of Range");
    		}
    	} catch (NumberFormatException e) {
    		throw new RuntimeException("Invalid Period for " + confnum + ".period - Skipping Config");
    	}
		switch (_period) {
			case 1: _periodstr = "MONTHUP"; break;
			case 2: _periodstr = "WKUP"; break;
			case 3: _periodstr = "DAYUP"; break;
		}
        
	}
	
	protected String getName() {
		return _name;
	}
	
	protected String getEventType() {
		return _eventtype;
	}
	
	protected Permission getPerms() {
		return _perms;
	}
	
	protected int getPeriod() {
		return _period;
	}

	protected String getPeriodStr() {
		return _periodstr;
	}
	
	protected String getPass() {
		return _pass;
	}
	
	protected String getFail() {
		return _fail;
	}
	
	protected ArrayList<User> getUsers() {
		Collection<User> users = GlobalContext.getGlobalContext().getUserManager().getAllUsers();
		ArrayList<User> filteredusers = new ArrayList<User>();
		for (User user : users) {
			if ((getPerms().check(user)) && (!user.isDeleted())) {
				filteredusers.add(user);
			}
		}
		Collections.sort(filteredusers,new UserComparator(getPeriodStr()));
		return filteredusers;
	}
	
	@SuppressWarnings("deprecation")
	protected String getRemainingTime() {
		Calendar cal = Calendar.getInstance();
		
		TimeManager timemgr = new TimeManager();
		if (timemgr.isEuropeanCalendar()) {
			cal.setFirstDayOfWeek(Calendar.MONDAY);
		}
		cal.set(Calendar.HOUR, 0);
		cal.set(Calendar.MINUTE, 0);
		cal.set(Calendar.SECOND, 0);
		cal.set(Calendar.DAY_OF_WEEK, 1);
		cal.add(Calendar.WEEK_OF_MONTH, 1);
		
		Calendar cal2 = Calendar.getInstance();

		
		long difference = cal.getTimeInMillis() - cal2.getTimeInMillis();
		Date date = new Date(difference);
		
		if ((date.getDate() - 1) < 1) {
			if (date.getHours() < 1) {
				return date.getMinutes() + " Minutes";
			}
			return date.getHours() + " Hours";
		}
		return (date.getDate() - 1) + " Days";		
		
	}
	
	public abstract void doTrial();

	public abstract void doTop(CommandRequest request, ResourceBundle bundle, CommandResponse response);
	
	public abstract void doCut(CommandRequest request, ResourceBundle bundle, CommandResponse response);
	
	public abstract void doPassed(CommandRequest request, ResourceBundle bundle, CommandResponse response);

}