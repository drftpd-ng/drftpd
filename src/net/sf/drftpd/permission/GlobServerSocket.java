package net.sf.drftpd.permission;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import net.sf.drftpd.master.SlaveManagerImpl;

import org.apache.oro.text.GlobCompiler;
import org.apache.oro.text.regex.MalformedPatternException;
import org.apache.oro.text.regex.Pattern;
import org.apache.oro.text.regex.Perl5Matcher;
import socks.server.Ident;

/**
 * @author <a href="mailto:drftpd@mog.se">Morgan Christiansson</a>
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
	public GlobServerSocket(Collection rslaves) throws IOException {
		super();
		this.rslaves = rslaves;
	}

	/**
	 * Constructor for ServerSocket2.
	 * @param arg0
	 * @throws IOException
	 */
	public GlobServerSocket(int port, Collection rslaves) throws IOException {
		super(port);
		this.rslaves = rslaves;
	}

	/**
	 * Constructor for ServerSocket2.
	 * @param arg0
	 * @param arg1
	 * @throws IOException
	 */
	public GlobServerSocket(int arg0, int arg1, List rslaves)
		throws IOException {
		super(arg0, arg1);
		this.rslaves = rslaves;
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

	
	private Collection rslaves;
	/**
	 * @see java.net.ServerSocket#accept()
	 */
	public Socket accept() throws IOException {
		Perl5Matcher m = new Perl5Matcher();
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
			for (Iterator i = SlaveManagerImpl.rslavesToMasks(this.rslaves).iterator(); i.hasNext();) {
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
					return sock;
				}
				// host
				if (m.matches(hostmask, p)) {
					return sock;
				}
				System.out.println("Didn't match: "+mask);
			} //for
			System.out.println("Denying connection: "+hostmask+"/"+ipmask+".");
			sock.close();
		}
	}
}
