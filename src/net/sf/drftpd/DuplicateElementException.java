/*
 * Created on 2003-jul-30
 *
 * To change the template for this generated file go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
package net.sf.drftpd;

/**
 * @author mog
 *
 * To change the template for this generated type comment go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
public class DuplicateElementException extends Exception {

	/**
	 * 
	 */
	public DuplicateElementException() {
		super();
	}

	/**
	 * @param message
	 */
	public DuplicateElementException(String message) {
		super(message);
	}

	/**
	 * @param message
	 * @param cause
	 */
	public DuplicateElementException(String message, Throwable cause) {
		super(message, cause);
	}

	/**
	 * @param cause
	 */
	public DuplicateElementException(Throwable cause) {
		super(cause);
	}
}
