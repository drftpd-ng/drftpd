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
package net.sf.drftpd.slave;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.rmi.server.RMISocketFactory;
import java.util.Random;

import org.apache.log4j.Logger;

/**
 * @author mog
 * @version $Id: PortRangeServerSocketFactory.java,v 1.5 2004/02/10 00:03:31 mog Exp $
 * @deprecated
 */
public class PortRangeServerSocketFactory extends RMISocketFactory {
	private static final Logger logger =
		Logger.getLogger(PortRangeServerSocketFactory.class);

	int _portfrom;
	int _portto;

	public PortRangeServerSocketFactory(int portfrom, int portto) {
		if(portfrom > portfrom) throw new IllegalArgumentException("from cannot be higher than to");
		_portfrom = portfrom;
		_portto = portto;
	}


	public ServerSocket createServerSocket(int port) throws IOException {
		if(port != 0) logger.error("Returning anonymous port to non-anonymous request", new Throwable());
		//getting a random number between portrangelow and portrangehigh,
		//attempt to listen on port, if it fails: increase port
		//if port is over portrangehigh, set to portrangelow, if port == random port: return
		//repeat
		int randport = new Random().nextInt(_portto - _portfrom) + _portfrom;
		int myport = randport;
		while (true) {
			//wrap
			if (myport > _portto)
				myport = _portfrom;
			try {
				return new ServerSocket(myport, 1);
			} catch (IOException e) {
				logger.warn("Failed to listen, will try next port", e);
				myport += 1;
				//fail
				if (myport == randport)
					throw (IOException) new IOException(
						"No available ports to listen on").initCause(
						e);
			}
		}
	}

	/* (non-Javadoc)
	 * @see java.rmi.server.RMISocketFactory#createSocket(java.lang.String, int)
	 */
	public Socket createSocket(String host, int port) throws IOException {
		return new Socket(host, port);
	}
	/* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	public boolean equals(Object obj) {
		if(obj instanceof PortRangeServerSocketFactory) {
			PortRangeServerSocketFactory factory = (PortRangeServerSocketFactory)obj;
			return (factory._portfrom == _portfrom && factory._portto == _portto);
		}
		return false;
	}

}
