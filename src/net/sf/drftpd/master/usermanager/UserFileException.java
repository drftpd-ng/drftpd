/*
 * Created on 2003-aug-07
 *
 * To change the template for this generated file go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
package net.sf.drftpd.master.usermanager;

/**
 * @author mog
 *
 * To change the template for this generated type comment go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
public class UserFileException extends Exception {

	/**
	 * 
	 */
	public UserFileException() {
		super();
	}

	/**
	 * @param message
	 */
	public UserFileException(String message) {
		super(message);
	}

	/**
	 * @param message
	 * @param cause
	 */
	public UserFileException(String message, Throwable cause) {
		super(message, cause);
	}

	/**
	 * @param cause
	 */
	public UserFileException(Throwable cause) {
		super(cause);
	}

}
