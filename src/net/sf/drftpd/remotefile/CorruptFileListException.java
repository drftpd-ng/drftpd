/*
 * Created on 2003-okt-21
 *
 * To change the template for this generated file go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
package net.sf.drftpd.remotefile;

import java.io.IOException;

/**
 * @author mog
 *
 * To change the template for this generated type comment go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
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
