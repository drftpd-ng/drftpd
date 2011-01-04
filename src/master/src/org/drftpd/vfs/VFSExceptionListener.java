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
package org.drftpd.vfs;

import java.beans.ExceptionListener;
import java.lang.reflect.InvocationTargetException;

import org.apache.log4j.Logger;

public class VFSExceptionListener implements ExceptionListener {

	private static final Logger logger = Logger
			.getLogger(VirtualFileSystem.class.getName());

	private String _inodePath;

	public VFSExceptionListener(String inodePath) {
		super();
		_inodePath = inodePath;
	}

	public void exceptionThrown(Exception arg0) {
		if (arg0 instanceof ClassNotFoundException) {
			// suppress this as will be thrown by deserializing plugin metadata which is no longer needed
		} else if (arg0 instanceof NullPointerException) {
			// suppress this as will be thrown by deserializing plugin metadata which is no longer needed
		} else if (arg0 instanceof InvocationTargetException) {
			logger.error("ExceptionListener throwing for inode " + _inodePath, arg0.getCause());
		} else {
			logger.error("ExceptionListener throwing for inode " + _inodePath, arg0);
		}
	}
}
