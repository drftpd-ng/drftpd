package net.sf.drftpd.master.usermanager;


/**
 * @author mog
 * @deprecated used only by glftpdusermanager
 * @version $Id: CorruptUserFileException.java,v 1.5 2003/12/23 13:38:20 mog Exp $
 */
public class CorruptUserFileException extends UserFileException {
	public CorruptUserFileException(String message) {
		super(message);
	}
}
