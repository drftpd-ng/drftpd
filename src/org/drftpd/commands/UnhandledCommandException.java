/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 * 
 * DrFTPD is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 * 
 * DrFTPD is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with DrFTPD; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package org.drftpd.commands;

import net.sf.drftpd.master.FtpRequest;

/**
 * @author mog
 * @version $Id: UnhandledCommandException.java,v 1.1 2004/06/01 15:40:34 mog Exp $
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
