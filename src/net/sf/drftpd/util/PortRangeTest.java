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
package net.sf.drftpd.util;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;

import javax.net.ServerSocketFactory;

import junit.framework.TestCase;

/**
 * @author zubov
 * @version $Id: PortRangeTest.java,v 1.1 2004/11/05 13:13:41 zubov Exp $
 */
public class PortRangeTest extends TestCase {

    public void testGetPort() throws IOException {
        PortRange pr = new PortRange(45300,45310);
        ArrayList ports = new ArrayList();
        for (int x = 45300; x<=45310; x++) {
            ports.add(new Integer(x));
        }
        assertEquals(11,ports.size());
        ServerSocket ss = new ServerSocket(45305);
        ports.remove(new Integer(ss.getLocalPort()));
        assertEquals(10,ports.size());
        for (int x = 0; x<10; x++) {
            ServerSocket socket = pr.getPort(ServerSocketFactory.getDefault());
            ports.remove(new Integer(socket.getLocalPort()));
            assertEquals(10-x-1,ports.size());
        }
        assertEquals(0,ports.size());
        try {
            pr.getPort(ServerSocketFactory.getDefault());
        } catch (RuntimeException e) {
            assertTrue(e.getMessage().equals("PortRange exhausted"));
        }
        ss.close();
        ss = pr.getPort(ServerSocketFactory.getDefault());
    }

}
