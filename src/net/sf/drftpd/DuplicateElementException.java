package net.sf.drftpd;

/**
 * @author mog
 *
 * @version $Id: DuplicateElementException.java,v 1.2 2003/12/23 13:38:18 mog Exp $
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
