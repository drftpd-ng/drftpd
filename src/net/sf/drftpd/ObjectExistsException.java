package net.sf.drftpd;

import java.io.IOException;

/**
 * @author <a href="mailto:drftpd@mog.se">Morgan Christiansson</a>
 *
 * To change this generated comment edit the template variable "typecomment":
 * Window>Preferences>Java>Templates.
 * To enable and disable the creation of type comments go to
 * Window>Preferences>Java>Code Generation.
 */
public class ObjectExistsException extends IOException {

	/**
	 * Constructor for FileExists.
	 */
	public ObjectExistsException() {
		super();
	}

	/**
	 * Constructor for FileExists.
	 * @param arg0
	 */
	public ObjectExistsException(String arg0) {
		super(arg0);
	}

}
