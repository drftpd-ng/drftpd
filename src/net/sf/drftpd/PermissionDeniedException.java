package net.sf.drftpd;

import java.io.IOException;

/**
 * @author mog
 *
 * To change this generated comment edit the template variable "typecomment":
 * Window>Preferences>Java>Templates.
 * To enable and disable the creation of type comments go to
 * Window>Preferences>Java>Code Generation.
 */
public class PermissionDeniedException extends IOException {
	public PermissionDeniedException() { super(); }
	public PermissionDeniedException(String message) { super(message); }
}
