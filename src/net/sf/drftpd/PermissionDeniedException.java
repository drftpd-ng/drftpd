package net.sf.drftpd;

import java.io.IOException;

/**
 * @author mog
 * @version $Id: PermissionDeniedException.java,v 1.4 2003/12/23 13:38:18 mog Exp $
 */
public class PermissionDeniedException extends IOException {
	public PermissionDeniedException() { super(); }
	public PermissionDeniedException(String message) { super(message); }
}
