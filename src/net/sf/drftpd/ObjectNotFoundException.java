package net.sf.drftpd;

/**
 * @author mog
 *
 * @version $Id: ObjectNotFoundException.java,v 1.2 2003/12/23 13:38:18 mog Exp $
 */
public class ObjectNotFoundException extends Exception {

	/**
	 * 
	 */
	public ObjectNotFoundException() {
		super();
	}

	/**
	 * @param message
	 */
	public ObjectNotFoundException(String message) {
		super(message);
	}

	/**
	 * @param message
	 * @param cause
	 */
	public ObjectNotFoundException(String message, Throwable cause) {
		super(message, cause);
	}

	/**
	 * @param cause
	 */
	public ObjectNotFoundException(Throwable cause) {
		super(cause);
	}
}
