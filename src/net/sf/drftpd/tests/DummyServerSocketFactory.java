package net.sf.drftpd.tests;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

import javax.net.ServerSocketFactory;

/**
 * @author mog
 * @version $Id: DummyServerSocketFactory.java,v 1.1 2003/12/22 18:09:43 mog Exp $
 */
public class DummyServerSocketFactory extends ServerSocketFactory {
	private DummySocketFactory _dssf;

	public DummyServerSocketFactory(DummySocketFactory dssf) {
		_dssf = dssf;
	}

	public ServerSocket createServerSocket() {
		try {
			return new ServerSocket() {
				public Socket accept() {
					return getDummySocket();
				}
			};
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	public ServerSocket createServerSocket(int arg0) throws IOException {
		return createServerSocket();
	}

	public ServerSocket createServerSocket(int arg0, int arg1)
		throws IOException {
		return createServerSocket();
	}

	public ServerSocket createServerSocket(
		int arg0,
		int arg1,
		InetAddress arg2)
		throws IOException {
		return createServerSocket();
	}

	public DummySocket getDummySocket() {
		return _dssf.getDummySocket();
	}

}
