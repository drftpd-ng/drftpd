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
package org.drftpd.master.tests;

import javax.net.ServerSocketFactory;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;


/**
 * @author mog
 * @version $Id$
 */
public class DummyServerSocketFactory extends ServerSocketFactory {
    private final DummySocketFactory _dssf;

    public DummyServerSocketFactory(DummySocketFactory dssf) {
        _dssf = dssf;
    }

    public ServerSocket createServerSocket() {
        try {
            return new DummyServerSocket() {
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

    public ServerSocket createServerSocket(int arg0, int arg1, InetAddress arg2)
            throws IOException {
        return createServerSocket();
    }

    public DummySocket getDummySocket() {
        return _dssf.getDummySocket();
    }
}
