package net.sf.drftpd.util;

import java.util.Random;

import org.apache.log4j.Logger;

/**
 * @author mog
 * @version $Id: PortRange.java,v 1.2 2003/12/13 17:20:23 mog Exp $
 */
public class PortRange {
	private int _minPort;
	private boolean _ports[];
	private Logger logger = Logger.getLogger(PortRange.class);

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
		System.out.println("portrange finalize()'d");
		for (int i = 0; i < _ports.length; i++) {
			if (!_ports[i]) {
				System.out.println(_minPort + i + " not released");
			}
		}
	}

	public int getPort() {
		synchronized (_ports) {
			int initPos = new Random().nextInt(_ports.length);
			logger.debug("initPos: " + initPos);
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
