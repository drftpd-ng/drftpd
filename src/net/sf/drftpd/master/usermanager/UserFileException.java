/*
 * Created on 2003-aug-07
 *
 * To change the template for this generated file go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
package net.sf.drftpd.master.usermanager;


/**
 * @author mog
 * @version $Id: UserFileException.java,v 1.2 2003/11/17 20:13:10 mog Exp $
 */
public class UserFileException extends Exception {

	public UserFileException() {
		super();
	}

	public UserFileException(String message) {
		super(message);
	}

	public UserFileException(String message, Throwable cause) {
		super(message, cause);
	}

	public UserFileException(Throwable cause) {
		super(cause);
	}

}
