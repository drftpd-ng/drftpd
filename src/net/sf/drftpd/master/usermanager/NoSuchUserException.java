package net.sf.drftpd.master.usermanager;

/**
 * @author <a href="mailto:drftpd@mog.se">Morgan Christiansson</a>
 *
 * To change this generated comment edit the template variable "typecomment":
 * Window>Preferences>Java>Templates.
 * To enable and disable the creation of type comments go to
 * Window>Preferences>Java>Code Generation.
 */
public class NoSuchUserException extends Exception {
	public NoSuchUserException(String message) {
		super(message);
	}
}
