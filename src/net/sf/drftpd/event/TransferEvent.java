package net.sf.drftpd.event;

import java.net.InetAddress;

import net.sf.drftpd.master.usermanager.User;
import net.sf.drftpd.remotefile.LinkedRemoteFile;

/**
 * @author mog
 * @version $Id: TransferEvent.java,v 1.6 2004/01/13 21:36:31 mog Exp $
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

	public InetAddress getUserHost() {
		return _userHost;
	}

	public InetAddress getXferHost() {
		return _xferHost;
	}
}
