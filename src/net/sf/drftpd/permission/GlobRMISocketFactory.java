package net.sf.drftpd.permission;

import java.io.IOException;
import java.net.ServerSocket;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.rmi.server.RMISocketFactory;
import java.util.List;


/**
 * @author mog
 *
 * To change this generated comment edit the template variable "typecomment":
 * Window>Preferences>Java>Templates.
 * To enable and disable the creation of type comments go to
 * Window>Preferences>Java>Code Generation.
 */
public class GlobRMISocketFactory implements RMIServerSocketFactory {
	private List masks;
	
	/**
	 * Constructor for GlobRMIServerSocketFactory.
	 */
	public GlobRMISocketFactory(List masks) {
		super();
		this.masks = masks;
	}

	/**
	 * @see java.rmi.server.RMIServerSocketFactory#createServerSocket(int)
	 */
	public ServerSocket createServerSocket(int port) throws IOException {
		return new GlobServerSocket(port, masks);
	}

}
