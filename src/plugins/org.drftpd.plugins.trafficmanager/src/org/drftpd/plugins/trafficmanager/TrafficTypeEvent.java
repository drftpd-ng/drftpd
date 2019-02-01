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

import org.drftpd.usermanager.User;
import org.drftpd.vfs.FileHandle;

/**
* @author CyBeR
* @version $Id: TrafficTypeEvent.java 1925 2009-06-15 21:46:05Z cyber $
*/
public class TrafficTypeEvent {

	private String _type;
	private User _user;
	private FileHandle _file;
	private boolean _isStor;
	private long _minspeed;
	private long _speed;
	private long _transfered;
	private String _slavename;
	
	public TrafficTypeEvent(String type, User user, FileHandle file, boolean isStor, long minspeed, long speed, long transfered, String slavename) {
		_type = type;
		_user = user;
		_file = file;
		_isStor = isStor;
		_minspeed = minspeed;
		_speed = speed;
		_transfered = transfered;
		_slavename = slavename;
	}

	public String getType() {
		return _type;
	}
	
	public User getUser() {
		return _user;
	}
	
	public FileHandle getFile() {
		return _file;
	}

	public boolean isStor() {
		return _isStor;
	}
	
	public long getMinSpeed() {
		return _minspeed;
	}

	public long getSpeed() {
		return _speed;
	}	
	
	public long getTransfered() {
		return _transfered;
	}	
	
	public String getSlaveName() {
		return _slavename;
	}
	
}