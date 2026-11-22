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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.net.ssl.*;
import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.concurrent.*;

public class SocketCloserTest {
    private static final char[] PASS = "changeit".toCharArray();

    private static SSLContext context(Path dir) throws Exception {
        Path ks = dir.resolve("test.p12");
        String keytool = Path.of(System.getProperty("java.home"), "bin", "keytool").toString();
        Process p = new ProcessBuilder(keytool, "-genkeypair", "-alias", "t", "-keyalg", "RSA", "-keysize", "2048",
                "-dname", "CN=localhost", "-validity", "2", "-storetype", "PKCS12", "-keystore", ks.toString(),
                "-storepass", "changeit", "-keypass", "changeit").redirectErrorStream(true).start();
        p.getInputStream().readAllBytes();
        Assertions.assertEquals(0, p.waitFor(), "keytool failed");
        KeyStore store = KeyStore.getInstance("PKCS12");
        try (InputStream in = new FileInputStream(ks.toFile())) {
            store.load(in, PASS);
        }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(store, PASS);
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(store);
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
        return ctx;
    }

    private static void runCase(Path dir, String protocol, boolean graceful) throws Exception {
        SSLContext ctx = context(dir);
        byte[] payload = new byte[200_000];
        new java.util.Random(1).nextBytes(payload);
        try (SSLServerSocket server = (SSLServerSocket) ctx.getServerSocketFactory()
                .createServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            server.setEnabledProtocols(new String[]{protocol});
            ExecutorService ex = Executors.newSingleThreadExecutor();
            Future<?> serverSide = ex.submit(() -> {
                try (Socket s = server.accept()) {
                    ((SSLSocket) s).startHandshake();
                    s.setSoTimeout(100); // same as the slave uses
                    OutputStream out = s.getOutputStream();
                    out.write(payload);
                    out.flush();
                    if (graceful) {
                        SocketCloser.closeGracefully(s);
                    }
                }
                return null;
            });
            SSLSocket client = (SSLSocket) ctx.getSocketFactory()
                    .createSocket(InetAddress.getLoopbackAddress(), server.getLocalPort());
            client.setEnabledProtocols(new String[]{protocol});
            client.setSoTimeout(5000);
            ByteArrayOutputStream got = new ByteArrayOutputStream();
            try (client) {
                // readAllBytes() ends with read() == -1, which JSSE only returns after close_notify
                got.write(client.getInputStream().readAllBytes());
            } finally {
                serverSide.get(10, TimeUnit.SECONDS);
                ex.shutdownNow();
            }
            Assertions.assertArrayEquals(payload, got.toByteArray());
        }
    }

    @Test
    public void tls13ClientSeesCloseNotify(@TempDir Path dir) throws Exception {
        runCase(dir, "TLSv1.3", true);
    }

    @Test
    public void tls12ClientSeesCloseNotify(@TempDir Path dir) throws Exception {
        runCase(dir, "TLSv1.2", true);
    }

    @Test
    public void nullAndPlainSocketsAreHandled() throws Exception {
        SocketCloser.closeGracefully(null);
        try (java.net.ServerSocket ss = new java.net.ServerSocket(0, 1, InetAddress.getLoopbackAddress());
             Socket c = new Socket(InetAddress.getLoopbackAddress(), ss.getLocalPort());
             Socket a = ss.accept()) {
            SocketCloser.closeGracefully(a);
            Assertions.assertTrue(a.isClosed());
            Assertions.assertEquals(-1, c.getInputStream().read());
        }
    }
}
