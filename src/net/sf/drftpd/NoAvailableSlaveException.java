package net.sf.drftpd;

import java.io.IOException;

/**
 * @author mog
 * @version $Id: NoAvailableSlaveException.java,v 1.2 2003/12/23 13:38:18 mog Exp $
 */
public class NoAvailableSlaveException extends IOException {
	public NoAvailableSlaveException(String message) {
		super(message);
	}
	public NoAvailableSlaveException() {
		super();
	}
}
