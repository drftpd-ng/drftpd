package net.sf.drftpd.master.usermanager;

import java.io.IOException;

/**
 * @author <a href="mailto:mog@linux.nu">Morgan Christiansson</a>
 */
public class CorruptUserFileException extends IOException {
	public CorruptUserFileException(String message) {
		super(message);
	}
}
