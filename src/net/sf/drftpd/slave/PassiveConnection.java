package net.sf.drftpd.slave;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Random;

import org.apache.log4j.Logger;

/**
 * @author <a href="mailto:drftpd@mog.se">Morgan Christiansson</a>
 */
public class PassiveConnection extends Connection {
	private static Logger logger = Logger.getLogger(PassiveConnection.class);
	ServerSocket server;
	
	/**
	 * @deprecated use portrange
	 * @param bindAddr
	 * @throws IOException
	 */
	public PassiveConnection(InetAddress bindAddr) throws IOException {
		server = new ServerSocket(0, 1, bindAddr);
	}

	public PassiveConnection(InetAddress bindAddr, int portfrom, int portto) throws IOException {
		if(portfrom == 0) {
			server = new ServerSocket(0, 1, bindAddr);
			return;
		}
		//getting a random number between portrangelow and portrangehigh,
		//attempt to listen on port, if it fails: increase port
		//if port is over portrangehigh, set to portrangelow, if port == random port: return
		//repeat
		int randport = new Random().nextInt(portto-portfrom)+portfrom;
		int port = randport;
		while(true) {
			//wrap
			if(port > portto) port = portfrom; 
			try {
				server = new ServerSocket(port, 1, bindAddr);
				return;
			} catch(IOException e) {
				logger.warn("Failed to listen, will try next port");
				port += 1;
				//fail
				if(port == randport) throw (IOException)new IOException("No available ports to listen on").initCause(e);
			}
		}
	}
	
	public Socket connect() throws IOException {
		Socket sock = server.accept();
		setSockOpts(sock);
		server.close();
		return sock;
	}

	public int getLocalPort() {
		return server.getLocalPort();
	}

}
