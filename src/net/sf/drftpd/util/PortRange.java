package net.sf.drftpd.util;

import java.util.Random;

/**
 * @author mog
 * @version $Id: PortRange.java,v 1.1 2003/12/12 22:31:14 mog Exp $
 */
public class PortRange {
	boolean _ports[];
	int _minPort;
	public PortRange(int minPort, int maxPort) {
		_ports = new boolean[maxPort - minPort];
		_minPort = minPort;
	}

	public int reservePort() {
		synchronized (_ports) {
			int initPos = new Random().nextInt(_ports.length);
			int pos = initPos;
			while (true) {
				if (_ports[pos] == false) {
					_ports[initPos] = true;
					return _minPort + initPos;
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
			assert _ports[_minPort + port]
				== true : "releasePort() on unused port";
			_ports[_minPort + port] = false;
		}
	}
}
