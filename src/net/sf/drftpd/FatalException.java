package net.sf.drftpd;

/**
 * @author mog
 * @version $Id: FatalException.java,v 1.2 2003/12/23 13:38:18 mog Exp $
 */
public class FatalException extends RuntimeException {

	/**
	 * 
	 */
	public FatalException() {
		super();
	}

	/**
	 * @param message
	 */
	public FatalException(String message) {
		super(message);
	}

	/**
	 * @param message
	 * @param cause
	 */
	public FatalException(String message, Throwable cause) {
		super(message, cause);
	}

	/**
	 * @param cause
	 */
	public FatalException(Throwable cause) {
		super(cause);
	}

}
