package net.sf.drftpd.master.usermanager;

import java.io.IOException;

/**
 * @author <a href="mailto:drftpd@mog.se">Morgan Christiansson</a>
 */
public class CorruptUserFileException extends IOException {
	public CorruptUserFileException(String message) {
		super(message);
	}
}
