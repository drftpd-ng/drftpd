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
package net.sf.drftpd.util;

import java.util.Random;

import org.apache.log4j.Logger;

/**
 * @author mog
 * @version $Id: PortRange.java,v 1.9 2004/06/01 15:40:33 mog Exp $
 */
public class PortRange {
	private static final Logger logger = Logger.getLogger(PortRange.class);
	private int _minPort;
	private boolean _ports[];
	
	Random rand = new Random();

	/**
	 * Creates a default port range for port 49152 to 65535.
	 */
	public PortRange() {
		this(49152, 65535);
	}

	public PortRange(int minPort, int maxPort) {
		_ports = new boolean[maxPort - minPort];
		_minPort = minPort;
	}

	protected void finalize() throws Throwable {
		super.finalize();
		for (int i = 0; i < _ports.length; i++) {
			if (_ports[i]) {
				logger.debug(_minPort + i + " not released");
			}
		}
	}

	/**
	 * @deprecated Doesn't check if port is used even though marked as unused.
	 */
	public int getPort() {
		synchronized (_ports) {
			int initPos = rand.nextInt(_ports.length);
			logger.debug("initPos: " + initPos);
			int pos = initPos;
			while (true) {
				if (_ports[pos] == false) {
					_ports[pos] = true;
					logger.debug("returning " + _minPort + pos);
					return _minPort + pos;
				} else {
					pos++;
					if (pos == initPos)
						throw new RuntimeException("Portrange exhausted");
					if (pos > _ports.length)
						pos = 0;
				}
			}
		}
	}

	public void releasePort(int port) {
		synchronized (_ports) {
			if(_ports[port - _minPort]
				!= true) throw new RuntimeException("releasePort() on unused port");
			_ports[port - _minPort] = false;
		}
	}

}
