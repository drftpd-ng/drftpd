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
package org.drftpd.tests;

import javax.net.SocketFactory;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;


/**
 * @author mog
 * @version $Id$
 */
public class DummySocketFactory extends SocketFactory {
    private DummySocket _socket = new DummySocket();

    public DummySocketFactory() {
    }

    public Socket createSocket(String arg0, int arg1)
        throws IOException, UnknownHostException {
        return createSocket();
    }

    public Socket createSocket(String arg0, int arg1, InetAddress arg2, int arg3)
        throws IOException, UnknownHostException {
        return createSocket();
    }

    public Socket createSocket(InetAddress arg0, int arg1)
        throws IOException {
        return createSocket();
    }

    public Socket createSocket(InetAddress arg0, int arg1, InetAddress arg2,
        int arg3) throws IOException {
        return createSocket();
    }

    public Socket createSocket() throws IOException {
        return _socket;
    }

    public DummySocket getDummySocket() {
        return _socket;
    }
}
