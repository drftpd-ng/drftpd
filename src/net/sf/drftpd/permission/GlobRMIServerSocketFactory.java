package net.sf.drftpd.permission;

import java.io.IOException;
import java.net.ServerSocket;
import java.rmi.server.RMIServerSocketFactory;
import java.util.Collection;


/**
 * @author mog
 * @version $Id: GlobRMIServerSocketFactory.java,v 1.3 2003/12/23 13:38:21 mog Exp $
 */
public class GlobRMIServerSocketFactory implements RMIServerSocketFactory {
	private Collection rslaves;
	
	/**
	 * Constructor for GlobRMIServerSocketFactory.
	 */
	public GlobRMIServerSocketFactory(Collection rslaves) {
		super();
		this.rslaves = rslaves;
	}

	/**
	 * @see java.rmi.server.RMIServerSocketFactory#createServerSocket(int)
	 */
	public ServerSocket createServerSocket(int port) throws IOException {
		return new GlobServerSocket(port, rslaves);
	}

}
