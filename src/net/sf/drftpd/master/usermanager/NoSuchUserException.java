package net.sf.drftpd.master.usermanager;

/**
 * @author mog
 *
 * @version $Id: NoSuchUserException.java,v 1.4 2003/12/23 13:38:20 mog Exp $
 */
public class NoSuchUserException extends Exception {
	/**
	 * 
	 */
	public NoSuchUserException() {
		super();
	}

	/**
	 * @param message
	 * @param cause
	 */
	public NoSuchUserException(String message, Throwable cause) {
		super(message, cause);
	}

	/**
	 * @param cause
	 */
	public NoSuchUserException(Throwable cause) {
		super(cause);
	}

	public NoSuchUserException(String message) {
		super(message);
	}
}
