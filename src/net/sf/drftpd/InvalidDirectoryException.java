package net.sf.drftpd;

import java.io.IOException;

/**
 * @author mog
 *
 * @version $Id: InvalidDirectoryException.java,v 1.3 2003/12/23 13:38:18 mog Exp $
 */
public class InvalidDirectoryException extends IOException {

	/**
	 * Constructor for InvalidDirectoryException.
	 */
	public InvalidDirectoryException() {
		super();
	}

	/**
	 * Constructor for InvalidDirectoryException.
	 * @param arg0
	 */
	public InvalidDirectoryException(String arg0) {
		super(arg0);
	}

}
