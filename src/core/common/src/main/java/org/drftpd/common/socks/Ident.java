/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * DrFTPD is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with DrFTPD; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.drftpd.common.socks;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.StringTokenizer;

/**
 * Class Ident provides means to obtain username of the owner of the socket on
 * remote machine, providing remote machine runs identd daemon.
 *
 * To use it:
 *  Socket s = ss.accept();
 *  Ident id = new Ident(s);
 *  if(id.getUserName() != null) goUseUser(id.userName);
 *  else handleIdentError(id.errorCode,id.errorMessage)
 */
public class Ident {
    /**
     * Maximum amount of time we should wait before dropping the connection to
     * identd server.Setting it to 0 implies infinit timeout.
     */
    public static final int defaultConnectionTimeout = 2000;

    /**
     * Username as returned by the identd daemon, or null, if it failed
     */
    private String userName;

    /**
     * Constructor tries to connect to Identd daemon on the host of the given
     * socket, and retrieve username of the owner of given socket connection on
     * remote machine. After constructor returns public fields are initialised
     * to whatever the server returned.
     *
     * If username was successfully retrieved successful is set to true, and
     * userName and hostType are set to whatever server returned. If however for
     * some reason username was not obtained, successful is set to false and
     * errorCode contains the code explaining the reason of failure, and
     * errorMessage contains human-readable explanation.
     *
     * Constructor may block, for a while.
     *
     * @param s Socket whose ownership on remote end should be obtained.
     */
    public Ident(Socket s, int connectionTimeout) throws IOException {

        if (connectionTimeout < 1) {
            // connectionTimeout needs to be positive, so we revert to our default
            connectionTimeout = defaultConnectionTimeout;
        }

        try (Socket sock = new Socket()) {
            sock.bind(s.isBound() ? new InetSocketAddress(s.getLocalAddress(), 0) : null);
            sock.setSoTimeout(connectionTimeout);
            sock.connect(new InetSocketAddress(s.getInetAddress(), 113), connectionTimeout);

            byte[] request = ("" + s.getPort() + " , " + s.getLocalPort() + "\r\n").getBytes();

            sock.getOutputStream().write(request);

            BufferedReader in = new BufferedReader(new InputStreamReader(sock.getInputStream()));

            parseResponse(in.readLine());
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

        st.nextToken(); // Discard first token, it's basically what we have sent

        String command = st.nextToken().trim().toUpperCase();

        if (command.equals("USERID") && (st.countTokens() >= 2)) {
            // We do not care for the first token (hostType) so skip it
            st.nextToken();
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
}
