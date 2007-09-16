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
package org.drftpd.protocol.slave;

import org.drftpd.slave.Slave;
import org.drftpd.slave.async.AsyncResponse;

/**
 * All handlers *MUST* extend this class in order to be proper loaded and used.
 * @author fr0w
 * @version $Id$
 */
public abstract class AbstractHandler {
	private SlaveProtocolCentral _central;
	
	public AbstractHandler(SlaveProtocolCentral central) {
		_central = central;
	}
	
	/**
	 * @return the slave-side protocol central.
	 */
	public SlaveProtocolCentral getCentral() {
		return _central;
	}
	
	/**
	 * @return the Slave instance.
	 */
	public Slave getSlaveObject() {
		return _central.getSlaveObject();
	}
	
	/**
	 * {@link Slave.sendResponse(AsyncResponse ar)}
	 */
	public void sendResponse(AsyncResponse ar) {
		getSlaveObject().sendResponse(ar);
	}
}
