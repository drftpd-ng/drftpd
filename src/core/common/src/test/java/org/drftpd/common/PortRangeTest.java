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
package org.drftpd.common;

import org.drftpd.common.util.PortRange;
import org.junit.jupiter.api.Test;

import javax.net.ServerSocketFactory;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * @author zubov
 * @version $Id$
 */
public class PortRangeTest {

    private static final int RANGE_SIZE = 11;

    /**
     * Finds RANGE_SIZE consecutive ports that are free right now.
     * The bases are below the Linux ephemeral range (32768-60999), so outgoing connections on a busy
     * CI runner don't take them in the middle of the test, as they could with the old fixed 45300-45310.
     */
    private static int findFreeRange() {
        Random random = new Random();
        for (int attempt = 0; attempt < 100; attempt++) {
            int base = 20000 + random.nextInt(12000 - RANGE_SIZE);
            if (allFree(base)) {
                return base;
            }
        }
        throw new IllegalStateException("No " + RANGE_SIZE + " consecutive free ports found");
    }

    private static boolean allFree(int base) {
        List<ServerSocket> probes = new ArrayList<>();
        try {
            for (int port = base; port < base + RANGE_SIZE; port++) {
                probes.add(new ServerSocket(port));
            }
            return true;
        } catch (IOException e) {
            return false;
        } finally {
            for (ServerSocket probe : probes) {
                try {
                    probe.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    @Test
    public void testGetPort() throws IOException {
        int min = findFreeRange();
        int max = min + RANGE_SIZE - 1;
        PortRange pr = new PortRange(min, max, 0);
        List<Integer> ports = new ArrayList<>();
        List<ServerSocket> sockets = new ArrayList<>();

        try {
            for (int x = min; x <= max; x++) {
                ports.add(x);
            }
            assertEquals(RANGE_SIZE, ports.size());

            // One port in the middle is already taken by someone else
            ServerSocket ss = new ServerSocket(min + 5);
            sockets.add(ss);
            ports.remove(Integer.valueOf(ss.getLocalPort()));
            assertEquals(RANGE_SIZE - 1, ports.size());

            // PortRange hands out every other port in the range, each once
            for (int x = 0; x < RANGE_SIZE - 1; x++) {
                ServerSocket socket = pr.getPort(ServerSocketFactory.getDefault(), null);
                sockets.add(socket);
                assertTrue(ports.remove(Integer.valueOf(socket.getLocalPort())),
                        "Port " + socket.getLocalPort() + " was outside the range or given twice");
            }
            assertEquals(0, ports.size());

            RuntimeException e = assertThrows(RuntimeException.class,
                    () -> pr.getPort(ServerSocketFactory.getDefault(), null));
            assertEquals("PortRange exhausted", e.getMessage());

            // Freeing a port makes it available again
            ss.close();
            ServerSocket again = pr.getPort(ServerSocketFactory.getDefault(), null);
            sockets.add(again);
            assertEquals(min + 5, again.getLocalPort());
        } finally {
            for (ServerSocket socket : sockets) {
                socket.close();
            }
        }
    }
}
