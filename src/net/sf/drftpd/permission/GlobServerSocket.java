/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * DrFTPD is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with DrFTPD; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package net.sf.drftpd.permission;

import net.sf.drftpd.master.SlaveManagerImpl;

import org.apache.log4j.Logger;

import org.apache.oro.text.GlobCompiler;
import org.apache.oro.text.regex.MalformedPatternException;
import org.apache.oro.text.regex.Pattern;
import org.apache.oro.text.regex.Perl5Matcher;

import socks.server.Ident;

import java.io.IOException;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;


/**
 * @author mog
 * @version $Id: GlobServerSocket.java,v 1.12 2004/08/03 20:14:01 zubov Exp $
 */
public class GlobServerSocket extends ServerSocket {
    private static final Logger logger = Logger.getLogger(GlobServerSocket.class);
    private SlaveManagerImpl _sm;
    private Collection rslaves;

    public GlobServerSocket(int port, SlaveManagerImpl sm)
        throws IOException {
        super(port);
        _sm = sm;
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

            logger.warn("Ident: " + ident + ".");

            String ipmask = ident + "@" +
                sock.getInetAddress().getHostAddress();
            String hostmask = ident + "@" +
                sock.getInetAddress().getHostName();

            for (Iterator i = _sm.getMasks().iterator(); i.hasNext();) {
                String mask = (String) i.next();
                Pattern p;

                try {
                    p = new GlobCompiler().compile(mask);
                } catch (MalformedPatternException ex) {
                    throw new RuntimeException("Invalid glob pattern: " + mask,
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

            logger.warn("Rejecting RMI connection: " + hostmask + "/" + ipmask +
                ".");
            sock.close();
        }
    }
}
