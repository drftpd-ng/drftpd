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
package net.sf.drftpd.permission;

import java.io.IOException;
import java.net.ServerSocket;
import java.rmi.server.RMIServerSocketFactory;

import net.sf.drftpd.master.SlaveManagerImpl;


/**
 * @author mog
 * @version $Id: GlobRMIServerSocketFactory.java,v 1.5 2004/07/12 20:37:28 mog Exp $
 */
public class GlobRMIServerSocketFactory implements RMIServerSocketFactory {
	private SlaveManagerImpl _sm;

	public GlobRMIServerSocketFactory(SlaveManagerImpl sm) {
		_sm = sm;
	}

	public ServerSocket createServerSocket(int port) throws IOException {
		return new GlobServerSocket(port, _sm);
	}

}
