/*
 * Created on 2003-aug-11
 *
 * To change the template for this generated file go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
package net.sf.drftpd.event;

import java.net.InetAddress;

import net.sf.drftpd.master.usermanager.User;
import net.sf.drftpd.remotefile.LinkedRemoteFile;

/**
 * @author mog
 *
 * To change the template for this generated type comment go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
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
		LinkedRemoteFile directory,
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
	/**
	 * @param user
	 * @param command
	 * @param directory
	 * @param time
	 */
	public TransferEvent(
		User user,
		String command,
		LinkedRemoteFile directory,
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
	/**
	 * @return
	 */
	public InetAddress getUserHost() {
		return _userHost;
	}

	/**
	 * @return
	 */
	public InetAddress getXferHost() {
		return _xferHost;
	}

}
