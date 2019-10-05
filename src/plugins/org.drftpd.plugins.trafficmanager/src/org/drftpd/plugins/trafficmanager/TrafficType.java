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
package org.drftpd.plugins.trafficmanager;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.drftpd.GlobalContext;
import org.drftpd.master.BaseFtpConnection;
import org.drftpd.permissions.Permission;
import org.drftpd.usermanager.User;
import org.drftpd.vfs.FileHandle;

import java.io.FileNotFoundException;
import java.util.Properties;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * @author CyBeR
 * @version $Id: TrafficType.java 1925 2009-06-15 21:46:05Z CyBeR $
 */

public abstract class TrafficType {
	protected static final Logger logger = LogManager.getLogger(TrafficType.class);
	
	private String _type;
	private String _name;
	private long _maxspeed;
	private long _minspeed;
	private String _include;
	private String _exclude;	
	private Permission _perms;
	private boolean _up;
	private boolean _dn;
	
	/*
	 * Loads all the .conf information for the specific type
	 */
	public TrafficType(Properties p, int confnum, String type) {
		_type = type;
		
		if ((_name = p.getProperty(confnum + ".name","").trim()).isEmpty()) {
			throw new RuntimeException("Invalid Name for " + confnum + ".name - Skipping Config");
		}
		
		try {
			_maxspeed = Integer.parseInt(p.getProperty(confnum + ".maxspeed","0").trim()) * 1000;
		} catch (NumberFormatException e) {
    		throw new RuntimeException("Invalid MaxSpeed for " + confnum + ".maxspeed - Skipping Config");
    	}	
		
		try {
			_minspeed = Integer.parseInt(p.getProperty(confnum + ".minspeed","0").trim()) * 1000;
		} catch (NumberFormatException e) {
    		throw new RuntimeException("Invalid MinSpeed for " + confnum + ".minspeed - Skipping Config");
		}
	
		_perms = new Permission(p.getProperty(confnum + ".perms","").trim());
		
		_include = p.getProperty(confnum + ".include","").trim();
		if (_include.equals("*")) {
			_include = ".*";
		}
		try {
			Pattern.compile(_include);
		} catch (PatternSyntaxException e) {
			throw new RuntimeException("Invalid Regex For " + confnum + ".include - Skipping Config");
		}
		
		
		_exclude = p.getProperty(confnum + ".exclude","").trim();
		if ((_exclude.equals(".*")) || (_exclude.equals("*"))) {
			throw new RuntimeException("Cannot Exclude Every Path for " + confnum + ".exclude - Skipping Config");
		}
		try {
			Pattern.compile(_exclude);
		} catch (PatternSyntaxException e) {
			throw new RuntimeException("Invalid Regex For " + confnum + ".exclude - Skipping Config");
		}		
		
		_up = p.getProperty(confnum + ".up", "false").trim().equalsIgnoreCase("true");
		_dn = p.getProperty(confnum + ".dn", "false").trim().equalsIgnoreCase("true");
	}
	
	protected String getName() {
		return _name;
	}
	
	protected String getType() {
		return _type;
	}
	
	protected Permission getPerms() {
		return _perms;
	}
	
	protected long getMaxSpeed() {
		return _maxspeed;
	}

	protected long getMinSpeed() {
		return _minspeed;
	}
	
	protected boolean checkInclude(String text) {
		try {
			return text.matches(_include);
		} catch (PatternSyntaxException e) {
			logger.debug(e);
			return true;
		}
	}
	
	protected boolean checkExclude(String text) {
		try {
			return text.matches(_exclude);
		} catch (PatternSyntaxException e) {
			logger.debug(e);
			return false;
		}
	}
	
	protected boolean getUpload() {
		return _up;
	}	

	protected boolean getDownload() {
		return _dn;
	}
	
	protected boolean doDelete(FileHandle file) {
		if (!Boolean.parseBoolean(GlobalContext.getConfig().getMainProperties().getProperty("delete.upload.on.abort", "false"))) {
			try {
				file.deleteUnchecked();
			} catch (FileNotFoundException e) {
				// FileDeleted - Ignore
			}
			return true;
		}
		return false;
	}
	
	public abstract void doAction(User user, FileHandle file, boolean isStor, long minspeed, long speed, long transfered, BaseFtpConnection conn, String slaveName);

}