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

import java.net.InetAddress;

import net.sf.drftpd.master.usermanager.User;
import net.sf.drftpd.remotefile.LinkedRemoteFileInterface;

/**
 * @author mog
 * @version $Id: TransferEvent.java,v 1.8 2004/04/17 02:24:35 mog Exp $
 */
public class TransferEvent extends DirectoryFtpEvent {

	/**
	 * @param user
	 * @param command
	 * @param directory
	 */
	public TransferEvent(
		User user,
		String command,
		LinkedRemoteFileInterface directory,
		InetAddress userHost,
		InetAddress xferHost,
		char type,
		boolean complete) {
		this(
			user,
			command,
			directory,
			userHost,
			xferHost,
			type,
			complete,
			System.currentTimeMillis());
	}

	public TransferEvent(
		User user,
		String command,
		LinkedRemoteFileInterface directory,
		InetAddress userHost,
		InetAddress xferHost,
		char type,
		boolean complete,
		long time) {
		super(user, command, directory, time);
		_userHost = userHost;
		_xferHost = xferHost;
		_complete = complete;
		_type = type;
	}
	private InetAddress _userHost;
	private InetAddress _xferHost;
	private boolean _complete;
	private char _type;

	public char getType() {
		return _type;
	}
	public boolean isComplete() {
		return _complete;
	}

	public InetAddress getUserHost() {
		return _userHost;
	}

	public InetAddress getXferHost() {
		return _xferHost;
	}
}
