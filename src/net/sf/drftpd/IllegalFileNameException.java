package net.sf.drftpd;

import java.io.IOException;

/**
 * @author mog
 * @version $Id: IllegalFileNameException.java,v 1.3 2003/12/23 13:38:18 mog Exp $
 */
public class IllegalFileNameException extends IOException {

	public IllegalFileNameException() {
		super();
	}

	public IllegalFileNameException(String s) {
		super(s);
	}

}
