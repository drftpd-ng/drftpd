/*
 * Created on 2003-aug-10
 *
 * To change the template for this generated file go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
package net.sf.drftpd.permission;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.rmi.server.RMIServerSocketFactory;

/**
 * @author mog
 *
 * To change the template for this generated type comment go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
public class InetAddrRMIServerSocketFactory implements RMIServerSocketFactory {
	private InetAddress inetAddress;
	
	public InetAddrRMIServerSocketFactory(InetAddress addr) {
		this.inetAddress = addr;
	}

	/* (non-Javadoc)
	 * @see java.rmi.server.RMIServerSocketFactory#createServerSocket(int)
	 */
	public ServerSocket createServerSocket(int port) throws IOException {
		return new InetAddrServerSocket(inetAddress, port);
	}

}
