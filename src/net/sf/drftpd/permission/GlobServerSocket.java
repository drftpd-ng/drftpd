package net.sf.drftpd.permission;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import net.sf.drftpd.master.SlaveManagerImpl;

import org.apache.log4j.Logger;
import org.apache.oro.text.GlobCompiler;
import org.apache.oro.text.regex.MalformedPatternException;
import org.apache.oro.text.regex.Pattern;
import org.apache.oro.text.regex.Perl5Matcher;
import socks.server.Ident;

/**
 * @author mog
 * @version $Id: GlobServerSocket.java,v 1.7 2003/12/05 23:03:29 mog Exp $
 */
public class GlobServerSocket extends ServerSocket {

	public GlobServerSocket(Collection rslaves) throws IOException {
		super();
		this.rslaves = rslaves;
	}

	public GlobServerSocket(int port, Collection rslaves) throws IOException {
		super(port);
		this.rslaves = rslaves;
	}

	public GlobServerSocket(int arg0, int arg1, List rslaves)
		throws IOException {
		super(arg0, arg1);
		this.rslaves = rslaves;
	}

	public GlobServerSocket(int arg0, int arg1, InetAddress arg2)
		throws IOException {
		super(arg0, arg1, arg2);
	}

	private static final Logger logger =
		Logger.getLogger(GlobServerSocket.class);

	private Collection rslaves;

	public Socket accept() throws IOException {
		Perl5Matcher m = new Perl5Matcher();
		// Try until a valid peer tries to connect.
		while (true) {
			Socket sock = super.accept();
			Ident identObj = new Ident(sock);
			String ident;
			if (identObj.successful) {
				ident = identObj.userName;
			} else {
				ident = "";
			}

			String ipmask =
				ident + "@" + sock.getInetAddress().getHostAddress();
			String hostmask = ident + "@" + sock.getInetAddress().getHostName();
			for (Iterator i =
				SlaveManagerImpl.rslavesToMasks(this.rslaves).iterator();
				i.hasNext();
				) {
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
			} //for
			logger.warn(
				"Rejecting RMI connection: " + hostmask + "/" + ipmask + ".");
			sock.close();
		}
	}
}
