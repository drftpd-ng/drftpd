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
package org.drftpd.commands.dataconnection.event;

import org.drftpd.master.BaseFtpConnection;

/**
* @author CyBeR
* @version $Id: SlowTransferEvent.java 1925 2009-06-15 21:46:05Z cyber $
*/
public class SlowTransferEvent {

	private String _user;
	private String _path;
	private String _file;
	private boolean _isStor;
	private BaseFtpConnection _conn;
	private long _minspeed;
	private long _speed;
	private long _transfered;
	
	public SlowTransferEvent(String user, String path, String file, boolean isStor, BaseFtpConnection conn, long minspeed, long speed, long transfered) {
		_user = user;
		_path = path;
		_file = file;
		_isStor = isStor;
		_conn = conn;
		_minspeed = minspeed;
		_speed = speed;
		_transfered = transfered;
	}

	public String getUser() {
		return _user;
	}
	
	public String getPath() {
		return _path;
	}
	
	public String getFile() {
		return _file;
	}

	public boolean isStor() {
		return _isStor;
	}
	
	public BaseFtpConnection getConn() {
		return _conn;
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
	
}