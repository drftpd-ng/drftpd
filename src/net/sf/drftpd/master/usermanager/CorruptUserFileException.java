package net.sf.drftpd.master.usermanager;


/**
 * @author <a href="mailto:drftpd@mog.se">Morgan Christiansson</a>
 * @deprecated used only by glftpdusermanager
 */
public class CorruptUserFileException extends UserFileException {
	public CorruptUserFileException(String message) {
		super(message);
	}
}
