package net.sf.drftpd.tests;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;

import javax.net.SocketFactory;

/**
 * @author mog
 * @version $Id: DummySocketFactory.java,v 1.1 2003/12/22 18:09:43 mog Exp $
 */
public class DummySocketFactory extends SocketFactory {

	private DummySocket _socket = new DummySocket();
	
	public DummySocketFactory() {
	}

	public Socket createSocket(String arg0, int arg1)
		throws IOException, UnknownHostException {
			return createSocket();	}

	public Socket createSocket(
		String arg0,
		int arg1,
		InetAddress arg2,
		int arg3)
		throws IOException, UnknownHostException {
			return createSocket();	}

	public Socket createSocket(InetAddress arg0, int arg1) throws IOException {
		return createSocket();	}

	public Socket createSocket(
		InetAddress arg0,
		int arg1,
		InetAddress arg2,
		int arg3)
		throws IOException {
		return createSocket();
	}

	public Socket createSocket() throws IOException {
		return _socket;
	}

	public DummySocket getDummySocket() {
		return _socket;
	}

}
