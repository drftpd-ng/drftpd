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
 * @version $Id: UnhandledCommandException.java,v 1.4 2003/12/23 13:38:19 mog Exp $
 */
public class UnhandledCommandException extends Exception {
	public UnhandledCommandException() {
		super();
	}

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
