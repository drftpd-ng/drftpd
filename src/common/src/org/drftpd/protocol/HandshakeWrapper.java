/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 *
 * DrFTPD is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * DrFTPD; if not, write to the Free Software Foundation, Inc., 59 Temple Place,
 * Suite 330, Boston, MA 02111-1307 USA
 */
package org.drftpd.protocol;

import java.io.Serializable;

/**
 * HandshakeWrapper is the object which is serialized by the slave and
 * unserialized by master in order to check if the handshake was successful or not.
 * @author fr0w
 * @version $Id$
 */
@SuppressWarnings("serial")
public class HandshakeWrapper implements Serializable {
	private boolean _status;
	private Exception _exception;

	/**
	 * @return true if *all* plugins were found on the slave-side
	 * false if there's was a problem while handshaking.
	 */
	public boolean pluginStatus() {
		return _status;
	}
	
	/**
	 * If there was a problem, it's most likely an exception were thrown.
	 * @return the thrown Exception.
	 * @throws IllegalStateException if no exceptions were thrown.
	 */
	public Exception getException() {
		if (!_status)
			return _exception;
		
		throw new IllegalStateException("No exception were thrown");
	}
	
	/**
	 * Sets the status of the handshake.
	 * @param status
	 * @see #pluginStatus()
	 */
	public void setPluginStatus(boolean status) {
		_status = status;
	}
	
	/**
	 * Set the thrown Exception.
	 * @param e
	 * @see #getException()
	 */
	public void setException(Exception e) {
		_exception = e;
	}
	
	public String toString() {
		return getClass().getName() + "[pluginStatus="+pluginStatus()+"]";
	}
}
