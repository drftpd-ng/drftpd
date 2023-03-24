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
package org.drftpd.common.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.net.ServerSocketFactory;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.security.SecureRandom;

/**
 * @author mog
 * @version $Id$
 */
public class PortRange {
    private static final Logger logger = LogManager.getLogger(PortRange.class);
    SecureRandom rand = new SecureRandom();
    private final int _minPort;
    private final int _maxPort;
    private int _bufferSize = 0;

    /**
     * Creates a default port range for port 49152 to 65535.
     */
    public PortRange(int bufferSize) {
        _minPort = 0;
        _maxPort = 0;
        _bufferSize = bufferSize;
    }

    public PortRange(int minPort, int maxPort, int bufferSize) {
        if (0 >= minPort || minPort > maxPort || maxPort > 65535) {
            throw new RuntimeException("0 < minPort <= maxPort <= 65535");
        }
        if (bufferSize < 0) {
            throw new RuntimeException("BufferSize cannot be < 0");
        }

        _minPort = minPort;
        _maxPort = maxPort;
        _bufferSize = bufferSize;
    }

    private ServerSocket createServerSocket(int port, ServerSocketFactory ssf, InetAddress bindIP) throws IOException, java.net.SocketException {
        ServerSocket ss = ssf.createServerSocket();
        if (_bufferSize > 0) {
            ss.setReceiveBufferSize(_bufferSize);
        }
        if (bindIP == null) {
            ss.bind(new InetSocketAddress(port), 1);
        } else {
            ss.bind(new InetSocketAddress(bindIP, port), 1);
        }
        return ss;
    }

    /**
     * Function to get a tcp port to to use on the ServerSocketFactory based on the input to this PortRange class
     *
     * @param ssf The Server Socket Factory we need to create a listening socket on
     * @param bindIP The Internet IP Address. if Null we will listen on all ip's
     *
     * @return A newly initialize ServerSocket or null
     */
    public ServerSocket getPort(ServerSocketFactory ssf, InetAddress bindIP) {
        ServerSocket ss = null;
        if (_minPort != 0) {
            int pos = rand.nextInt(_maxPort - _minPort + 1) + _minPort;
            int initPos = pos;
            boolean retry = true;
            while (true) {
                try {
                    ss = createServerSocket(pos, ssf, bindIP);
                    break;
                } catch (IOException ignored) {
                    logger.debug("Tried to open a socket on port {} and it is in use", pos);
                }
                pos++;
                if (pos > _maxPort) {
                    pos = _minPort;
                }
                if (pos == initPos) {
                    if (!retry) {
                        throw new RuntimeException("PortRange exhausted");
                    }
                    System.runFinalization();
                    retry = false;
                }
            }
        } else
        {
            try {
                ss = createServerSocket(0, ssf, bindIP);
            } catch (IOException e) {
                logger.error("Unable to bind anonymous port", e);
                throw new RuntimeException(e);
            }
        }

        return ss;
    }
}
