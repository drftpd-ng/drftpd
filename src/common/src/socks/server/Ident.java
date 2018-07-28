package socks.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.StringTokenizer;

/**
 * Class Ident provides means to obtain user name of the owner of the socket on
 * remote machine, providing remote machine runs identd daemon.
 * <p>
 * To use it: <tt><pre>
 *  Socket s = ss.accept();
 *  Ident id = new Ident(s);
 *  if(id.successful) goUseUser(id.userName);
 *  else handleIdentError(id.errorCode,id.errorMessage)
 * </pre></tt>
 */
public class Ident {
	/** Identd on port 113 can't be contacted */
	public static final int ERR_NO_CONNECT = 1;

	/** Connection timed out */
	public static final int ERR_TIMEOUT = 2;

	/**
	 * Identd daemon responded with ERROR, in this case errorMessage contains
	 * the string explanation, as send by the daemon.
	 */
	public static final int ERR_PROTOCOL = 3;

	/**
	 * When parsing server response protocol error happened.
	 */
	public static final int ERR_PROTOCOL_INCORRECT = 4;

	/**
	 * Maximum amount of time we should wait before dropping the connection to
	 * identd server.Setting it to 0 implies infinit timeout.
	 */
	public static final int connectionTimeout = 10000;

	/** Host type as returned by daemon, can be null, if error happened */
	private String hostType;

	/** User name as returned by the identd daemon, or null, if it failed */
	private String userName;

	/** Error code */
	public int errorCode;

	/**
	 * Constructor tries to connect to Identd daemon on the host of the given
	 * socket, and retrieve user name of the owner of given socket connection on
	 * remote machine. After constructor returns public fields are initialised
	 * to whatever the server returned.
	 * <p>
	 * If user name was successfully retrieved successful is set to true, and
	 * userName and hostType are set to whatever server returned. If however for
	 * some reason user name was not obtained, successful is set to false and
	 * errorCode contains the code explaining the reason of failure, and
	 * errorMessage contains human readable explanation.
	 * <p>
	 * Constructor may block, for a while.
	 * 
	 * @param s
	 *            Socket whose ownership on remote end should be obtained.
	 */
	public Ident(Socket s) throws IOException {
		Socket sock = null;

		try {
			sock = new Socket();
			sock.bind(s.isBound() ? new InetSocketAddress(s.getLocalAddress(),
					0) : null);
			sock.setSoTimeout(connectionTimeout);
			sock.connect(new InetSocketAddress(s.getInetAddress(), 113),
					connectionTimeout);

			byte[] request = ("" + s.getPort() + " , " + s.getLocalPort() + "\r\n")
					.getBytes();

			sock.getOutputStream().write(request);

			BufferedReader in = new BufferedReader(new InputStreamReader(sock
					.getInputStream()));

			parseResponse(in.readLine());
		} finally {
			try {
				if (sock != null) {
					sock.close();
				}
			} catch (IOException ioe) {
			}
		}
	}

	private void parseResponse(String response) throws IOException {
		if (response == null) {
			throw new IOException("Identd server closed connection.");
		}

		StringTokenizer st = new StringTokenizer(response, ":");

		if (st.countTokens() < 3) {
			throw new IOException("Can't parse server response: " + response);
		}

		st.nextToken(); // Discard first token, it's basically what we have send

		String command = st.nextToken().trim().toUpperCase();

		if (command.equals("USERID") && (st.countTokens() >= 2)) {
			hostType = st.nextToken().trim();
			userName = st.nextToken().trim(); // Get all that is left

			if (userName.indexOf('@') != -1) {
				throw new IOException("Illegal username: " + userName);
			}

        } else if (command.equals("ERROR")) {
			throw new IOException("Ident ERROR: " + response);
		} else {
			throw new IOException("Unexpected reply: " + response);
		}
	}

	public String getUserName() {
		return userName;
	}

	public String getHostType() {
		return hostType;
	}
}
