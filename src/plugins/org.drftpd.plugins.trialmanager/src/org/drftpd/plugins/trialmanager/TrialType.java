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

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.drftpd.GlobalContext;
import org.drftpd.commandmanager.CommandRequest;
import org.drftpd.commandmanager.CommandResponse;
import org.drftpd.master.cron.TimeManager;
import org.drftpd.permissions.Permission;
import org.drftpd.usermanager.User;
import org.drftpd.usermanager.util.UserComparator;

import java.util.*;

/**
 * @author CyBeR
 * @version $Id: TrialType.java 1925 2009-06-15 21:46:05Z CyBeR $
 */

public abstract class TrialType {
	protected static final Logger logger = LogManager.getLogger(TrialType.class);
	
	private String _eventtype;
	private String _name;
	private String _pass;
	private String _fail;
	private int _period; // 3 = daily | 2 = weekly | 1 = monthly
	private String _periodstr;
	private Permission _perms;
	private boolean _euroTime;
	
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
		
		_euroTime = false;
		TimeManager timemgr = new TimeManager();
		if (timemgr.isEuropeanCalendar()) {
			_euroTime = true;
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
		ArrayList<User> filteredusers = new ArrayList<>();
		for (User user : users) {
			if ((getPerms().check(user)) && (!user.isDeleted())) {
				filteredusers.add(user);
			}
		}
		filteredusers.sort(new UserComparator(getPeriodStr()));
		return filteredusers;
	}
	
	protected String getRemainingTime() {
		Calendar cal = Calendar.getInstance();
		Calendar cal2 = (Calendar) cal.clone();
		
		if (_euroTime) {
			cal.setFirstDayOfWeek(Calendar.MONDAY);
			cal2.setFirstDayOfWeek(Calendar.MONDAY);
		}
		
		cal.set(Calendar.HOUR_OF_DAY, 0);
		cal.set(Calendar.MINUTE, 0);
		cal.set(Calendar.SECOND, 0);
		// 3 = daily | 2 = weekly | 1 = monthly
		
		switch (_period) {
			case 1: {
				cal.set(Calendar.DAY_OF_MONTH, 1);
				cal.add(Calendar.MONTH, 1);
				break;
			}
			case 2: {
				cal.set(Calendar.DAY_OF_WEEK, cal.getFirstDayOfWeek());
				cal.add(Calendar.WEEK_OF_MONTH, 1);
				break;
			}
			case 3: {
				cal.add(Calendar.DAY_OF_MONTH, 1);
				break;
			}
		}
		
		//logger.debug("TrialType DateCheck: "+this.getPeriod() + " ~ " + this.getPeriodStr());
		//logger.debug("TrialType DateCheck 1: "+ cal.getTime());
		//logger.debug("TrialType DateCheck 2: "+ cal2.getTime());
		
		long diff = cal.getTimeInMillis() - cal2.getTimeInMillis();
		long diffSeconds = diff / 1000;
		long diffMinutes = diff / (60 * 1000);
		long diffHours = diff / (60 * 60 * 1000);
		long diffDays = diff / (24 * 60 * 60 * 1000);
		
		if (diffDays > 0) {
			return diffDays + " Days";
		}
		if (diffHours > 0) {
			return diffHours + " Hours";
		}
		if (diffMinutes > 0) {
			return diffMinutes + " Minutes";
		}
		return diffSeconds + " Seconds";
	}
	
	public abstract void doTrial();

	public abstract void doTop(CommandRequest request, ResourceBundle bundle, CommandResponse response);
	
	public abstract void doCut(CommandRequest request, ResourceBundle bundle, CommandResponse response);
	
	public abstract void doPassed(CommandRequest request, ResourceBundle bundle, CommandResponse response);

}