/*
 * Created on 2003-okt-16
 *
 * To change the template for this generated file go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
package net.sf.drftpd.master.command;

import net.sf.drftpd.master.FtpRequest;

/**
 * @author mog
 */
public class UnhandledCommandException extends Exception {
	public static UnhandledCommandException create(Class clazz, FtpRequest req) {
		return create(clazz, req.getCommand());
	}
	
	public UnhandledCommandException(String message) {
		super(message);
	}

	public UnhandledCommandException(String message, Throwable cause) {
		super(message, cause);
	}

	public UnhandledCommandException(Throwable cause) {
		super(cause);
	}

	public static UnhandledCommandException create(Class clazz, String command) {
		return new UnhandledCommandException(clazz.getName()+" doesn't know how to handle "+command);
	}
}
