package net.sf.drftpd.remotefile;

import java.io.IOException;

/**
 * @author mog
 *
 * @version $Id: CorruptFileListException.java,v 1.2 2003/12/23 13:38:21 mog Exp $
 */
public class CorruptFileListException extends IOException {

	/**
	 * 
	 */
	public CorruptFileListException() {
		super();
	}

	/**
	 * @param message
	 */
	public CorruptFileListException(String message) {
		super(message);
	}

	/**
	 * @param message
	 * @param cause
	 */
	public CorruptFileListException(String message, Throwable cause) {
		super(message);
		initCause(cause);
	}

	/**
	 * @param cause
	 */
	public CorruptFileListException(Throwable cause) {
		super();
		initCause(cause);
	}

}
