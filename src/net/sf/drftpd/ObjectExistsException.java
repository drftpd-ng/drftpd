package net.sf.drftpd;

import java.io.IOException;

/**
 * @author mog
 * @version $Id: ObjectExistsException.java,v 1.2 2003/11/17 20:13:09 mog Exp $
 */
public class ObjectExistsException extends IOException {

	public ObjectExistsException() {
		super();
	}

	public ObjectExistsException(String arg0) {
		super(arg0);
	}

}
