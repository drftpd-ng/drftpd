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
import java.net.Socket;

/**
 * @author mog
 *
 * To change the template for this generated type comment go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
public class InetAddrServerSocket extends ServerSocket {
	private InetAddress inetAddr;
	
	/**
	 * @throws java.io.IOException
	 */
	public InetAddrServerSocket() throws IOException {
		super();
	}

	/**
	 * @param port
	 * @throws java.io.IOException
	 */
	public InetAddrServerSocket(InetAddress inetAddr, int port) throws IOException {
		super(port);
		this.inetAddr = inetAddr;
	}
	/* (non-Javadoc)
	 * @see java.net.ServerSocket#accept()
	 */
	public Socket accept() throws IOException {
		// TODO Auto-generated method stub
		Socket sock = super.accept();
		
		return sock;
	}

}
