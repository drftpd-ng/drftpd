package net.sf.drftpd.permission;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Iterator;
import java.util.List;

import org.apache.oro.text.GlobCompiler;
import org.apache.oro.text.regex.MalformedPatternException;
import org.apache.oro.text.regex.Pattern;
import org.apache.oro.text.regex.Perl5Matcher;
import socks.server.Ident;

/**
 * @author mog
 *
 * To change this generated comment edit the template variable "typecomment":
 * Window>Preferences>Java>Templates.
 * To enable and disable the creation of type comments go to
 * Window>Preferences>Java>Code Generation.
 */
public class GlobServerSocket extends ServerSocket {

	/**
	 * Constructor for ServerSocket2.
	 * @throws IOException
	 */
	public GlobServerSocket(List masks) throws IOException {
		super();
		this.masks = masks;
	}

	/**
	 * Constructor for ServerSocket2.
	 * @param arg0
	 * @throws IOException
	 */
	public GlobServerSocket(int port, List masks) throws IOException {
		super(port);
		this.masks = masks;
	}

	/**
	 * Constructor for ServerSocket2.
	 * @param arg0
	 * @param arg1
	 * @throws IOException
	 */
	public GlobServerSocket(int arg0, int arg1, List masks)
		throws IOException {
		super(arg0, arg1);
		this.masks = masks;
	}

	/**
	 * Constructor for ServerSocket2.
	 * @param arg0
	 * @param arg1
	 * @param arg2
	 * @throws IOException
	 */
	public GlobServerSocket(int arg0, int arg1, InetAddress arg2)
		throws IOException {
		super(arg0, arg1, arg2);
	}

	private Perl5Matcher m = new Perl5Matcher();
	/**
	 * @see java.net.ServerSocket#accept()
	 */
	public Socket accept() throws IOException {
		// Try until a valid peer tries to connect.
		while (true) {
			Socket sock = super.accept();
			Ident identObj = new Ident(sock);
			String ident;
			if(identObj.successful) {
				ident = identObj.userName;
			} else {
				ident = "";
			}
			
			String ipmask = ident + "@" + sock.getInetAddress().getHostAddress();
			String hostmask = ident + "@" + sock.getInetAddress().getHostName();
			for (Iterator i = masks.iterator(); i.hasNext();) {
				String mask = (String) i.next();
				Pattern p;
				try {
					p = new GlobCompiler().compile(mask);
				} catch (MalformedPatternException ex) {
					throw new RuntimeException(
						"Invalid glob pattern: " + mask,
						ex);
				}

				// ip
				if (m.matches(ipmask, p)) {
					System.out.println("Accepted connection from "+hostmask+".");
					return sock;
				}
				// host
				if (m.matches(hostmask, p)) {
					System.out.println("Accepted connection from "+hostmask+".");
					return sock;
				}
			} //for
			System.out.println("Denying connection: "+hostmask+".");
			sock.close();
		}
	}
	private List masks;
}
