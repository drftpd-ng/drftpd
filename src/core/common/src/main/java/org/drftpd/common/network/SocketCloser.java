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
package org.drftpd.common.network;

import javax.net.ssl.SSLSocket;
import java.io.IOException;
import java.net.Socket;

/**
 * Helpers for closing data connections.
 */
public final class SocketCloser {
    private static final int CLOSE_TIMEOUT_MS = 1000;

    private SocketCloser() {
    }

    /**
     * Closes a socket after a transfer that ended normally. For an SSLSocket the TLS close_notify alert is
     * sent before the TCP close, so strict clients can tell a complete transfer from a truncated one.
     * Any pending output must already have been flushed by the caller. Never blocks waiting for the
     * peer's close_notify. Do not use this on abort/error paths, plain close() is fine there.
     *
     * @param sock the socket to close, may be null
     */
    public static void closeGracefully(Socket sock) {
        if (sock == null) {
            return;
        }
        try {
            if (sock instanceof SSLSocket && !sock.isClosed() && !sock.isOutputShutdown()) {
                sock.setSoTimeout(CLOSE_TIMEOUT_MS);
                sock.shutdownOutput(); // sends close_notify
            }
        } catch (IOException | UnsupportedOperationException ignored) {
            // fall through to close()
        } finally {
            try {
                sock.close();
            } catch (IOException ignored) {
                // nothing more we can do
            }
        }
    }
}
