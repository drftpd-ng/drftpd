/*
 * Created on 2004-apr-18
 *
 * To change the template for this generated file go to
 * Window - Preferences - Java - Code Generation - Code and Comments
 */
package org.drftpd.usermanager;

/**
 * @author mog
 */
@SuppressWarnings("serial")
public class UserExistsException extends Exception {
	public UserExistsException() {
		super();
	}

	public UserExistsException(String message) {
		super(message);
	}

	public UserExistsException(Throwable cause) {
		super(cause);
	}

	public UserExistsException(String message, Throwable cause) {
		super(message, cause);
	}
}
