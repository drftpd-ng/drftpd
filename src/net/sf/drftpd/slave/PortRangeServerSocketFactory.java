/*
 * Created on 2003-okt-06
 *
 * To change the template for this generated file go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
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
 *
 * To change the template for this generated type comment go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
public class PortRangeServerSocketFactory extends RMISocketFactory {
	private static Logger logger =
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
				logger.warn("Failed to listen, will try next port");
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
