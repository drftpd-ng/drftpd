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
package org.drftpd.util;

import junit.framework.TestCase;
import org.junit.Assert;

import javax.net.ServerSocketFactory;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;


/**
 * @author zubov
 * @version $Id$
 */
public class PortRangeTest extends TestCase {
    public void testGetPort() throws IOException {
        PortRange pr = new PortRange(45300, 45310, 0);
        ArrayList<Integer> ports = new ArrayList<>();
        ArrayList<ServerSocket> sockets = new ArrayList<>();

        for (int x = 45300; x <= 45310; x++) {
            ports.add(x);
        }

        Assert.assertEquals(11, ports.size());

        ServerSocket ss = new ServerSocket(45305);
        sockets.add(ss);
        ports.remove(Integer.valueOf(ss.getLocalPort()));
        Assert.assertEquals(10, ports.size());

        for (int x = 0; x < 10; x++) {
            ServerSocket socket = pr.getPort(ServerSocketFactory.getDefault(),null);
            sockets.add(socket);
            ports.remove(Integer.valueOf(socket.getLocalPort()));
            Assert.assertEquals(9 - x, ports.size());
        }

        Assert.assertEquals(0, ports.size());

        try {
            pr.getPort(ServerSocketFactory.getDefault(),null);
            throw new RuntimeException("PortRange should be exhausted!");
        } catch (RuntimeException e) {
            Assert.assertTrue(e.getMessage().equals("PortRange exhausted"));
        }

        ss.close();
        ss = pr.getPort(ServerSocketFactory.getDefault(),null);
        
        // clean up
        ss.close();
        for (ServerSocket socket : sockets) {
            socket.close();
        }
    }
}
