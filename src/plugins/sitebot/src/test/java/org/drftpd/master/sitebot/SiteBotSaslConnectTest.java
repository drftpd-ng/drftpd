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
package org.drftpd.master.sitebot;

import org.drftpd.master.sitebot.config.SiteBotConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Drives {@link SiteBot#connect()} against a tiny scripted IRC server on a local socket.
 * The SiteBot is built without running {@link SiteBot#run()} (which needs a GlobalContext):
 * only its configuration is injected, which is all connect() needs for a plain (non-ssl) socket.
 */
public class SiteBotSaslConnectTest {

    private static final String PASSWORD = "s3cret";

    enum Mode { SASL_OK, SASL_FAIL, NO_CAP, BARE_451 }

    private ServerSocket _serverSocket;
    private Thread _serverThread;
    private SiteBot _bot;
    /** Lines received by the fake server, one list per accepted connection. */
    private final List<List<String>> _connections = new CopyOnWriteArrayList<>();

    @AfterEach
    public void tearDown() throws Exception {
        if (_bot != null) {
            setField(_bot, "_isTerminated", true);
            _bot.setDisconnected(true);
            try {
                _bot.dispose();
            } catch (Exception e) {
                // ignore
            }
        }
        if (_serverSocket != null) {
            _serverSocket.close();
        }
        if (_serverThread != null) {
            _serverThread.join(5000);
        }
    }

    @Test
    @Timeout(20)
    public void saslSuccess() throws Exception {
        startServer(Mode.SASL_OK);
        connectBot(false);

        List<String> lines = _connections.get(0);
        String payload = Base64.getEncoder().encodeToString(("\0drftpd\0" + PASSWORD).getBytes(StandardCharsets.UTF_8));
        assertEquals(List.of("CAP LS 302", "NICK drftpd", "USER drftpd 8 * :drftpd", "CAP REQ :sasl",
                "AUTHENTICATE PLAIN", "AUTHENTICATE " + payload, "CAP END", "WHOIS drftpd"), lines.subList(0, 8));
        assertEquals(1, Collections.frequency(lines, "CAP END"));
        assertEquals("drftpd!drftpd@test.host", _bot.getHostMask());
        assertEquals(1, _connections.size());
    }

    @Test
    @Timeout(20)
    public void saslFailureNotRequiredContinues() throws Exception {
        startServer(Mode.SASL_FAIL);
        connectBot(false);

        List<String> lines = _connections.get(0);
        assertEquals(1, Collections.frequency(lines, "CAP END"));
        assertTrue(lines.contains("WHOIS drftpd"));
        assertEquals(1, _connections.size());
    }

    @Test
    @Timeout(20)
    public void saslFailureRequiredReconnects() throws Exception {
        // first connection fails SASL, the bot must QUIT and reconnect; the second one succeeds
        startServer(Mode.SASL_FAIL, Mode.SASL_OK);
        connectBot(true);

        assertEquals(2, _connections.size());
        List<String> first = _connections.get(0);
        assertFalse(first.contains("CAP END"));
        assertFalse(first.contains("WHOIS drftpd"));
        assertTrue(first.contains("QUIT :SASL authentication failed"));
        List<String> second = _connections.get(1);
        assertEquals(1, Collections.frequency(second, "CAP END"));
        assertTrue(second.contains("WHOIS drftpd"));
    }

    @Test
    @Timeout(20)
    public void saslFailuresBackOffAndResetOnSuccess() throws Exception {
        // two failures then a success: the bot waits 1s then 2s (connect.delay=0 uses the 1s minimum base)
        startServer(Mode.SASL_FAIL, Mode.SASL_FAIL, Mode.SASL_OK);
        long start = System.nanoTime();
        connectBot(true);
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000L;

        assertEquals(3, _connections.size());
        assertTrue(elapsedMillis >= 3000L, "elapsed " + elapsedMillis + "ms");
        assertTrue(_connections.get(2).contains("WHOIS drftpd"));
        // a successful SASL login resets the backoff
        assertEquals(0, getField(_bot, "_saslFailures"));
    }

    @Test
    @Timeout(20)
    public void saslRefusedWithoutTlsNotRequired() throws Exception {
        // use_ssl=false and no sasl.allow_plaintext: no CAP/AUTHENTICATE at all, plain registration
        startServer(Mode.SASL_OK);
        connectBot(baseProperties(_serverSocket.getLocalPort(), false));

        assertEquals(1, _connections.size());
        List<String> lines = _connections.get(0);
        assertEquals(List.of("NICK drftpd", "USER drftpd 8 * :drftpd", "WHOIS drftpd"), lines.subList(0, 3));
        assertTrue(lines.stream().noneMatch(l -> l.startsWith("CAP") || l.startsWith("AUTHENTICATE")), lines.toString());
        assertEquals("drftpd!drftpd@test.host", _bot.getHostMask());
    }

    @Test
    @Timeout(20)
    public void saslRefusedWithoutTlsRequiredSkipsServer() throws Exception {
        // server 1: plain, required, not allowed -> skipped without even connecting;
        // server 2 (same fake ircd): allow_plaintext=true -> SASL login
        startServer(Mode.SASL_OK);
        Properties p = baseProperties(_serverSocket.getLocalPort(), true);
        p.setProperty("server.2.host", p.getProperty("server.1.host"));
        p.setProperty("server.2.port", p.getProperty("server.1.port"));
        p.setProperty("server.2.password", "");
        p.setProperty("server.2.use_ssl", "false");
        p.setProperty("server.2.sasl.username", "drftpd");
        p.setProperty("server.2.sasl.password", PASSWORD);
        p.setProperty("server.2.sasl.required", "true");
        p.setProperty("server.2.sasl.allow_plaintext", "true");
        connectBot(p);

        assertEquals(1, _connections.size());
        List<String> lines = _connections.get(0);
        assertEquals("CAP LS 302", lines.get(0));
        assertEquals(1, Collections.frequency(lines, "CAP END"));
        assertTrue(lines.contains("WHOIS drftpd"));
        // the refused server counted as a SASL failure, the successful login reset the counter
        assertEquals(0, getField(_bot, "_saslFailures"));
    }

    @Test
    @Timeout(20)
    public void serverWithoutCapSupport() throws Exception {
        startServer(Mode.NO_CAP);
        connectBot(false);

        List<String> lines = _connections.get(0);
        assertEquals("CAP LS 302", lines.get(0));
        assertFalse(lines.contains("CAP END"));
        assertTrue(lines.contains("WHOIS drftpd"));
        assertEquals("drftpd!drftpd@test.host", _bot.getHostMask());
    }

    @Test
    @Timeout(20)
    public void serverAnsweringBare451() {
        // ":srv 451 * :You have not registered" without the command name must not abort the login
        assertDoesNotThrow(() -> {
            startServer(Mode.BARE_451);
            connectBot(false);
        });

        List<String> lines = _connections.get(0);
        assertEquals("CAP LS 302", lines.get(0));
        assertFalse(lines.contains("CAP END"));
        assertTrue(lines.contains("WHOIS drftpd"));
        assertEquals("drftpd!drftpd@test.host", _bot.getHostMask());
        assertEquals(1, _connections.size());
    }

    private void connectBot(boolean required) throws Exception {
        Properties p = baseProperties(_serverSocket.getLocalPort(), required);
        // the fake server is not TLS
        p.setProperty("server.1.sasl.allow_plaintext", "true");
        connectBot(p);
    }

    private void connectBot(Properties p) throws Exception {
        _bot = new SiteBot("unused/");
        setField(_bot, "_config", new SiteBotConfig(p));
        _bot.setEncoding("UTF-8");
        // skip the reconnect delay (wait(0) would block forever)
        _bot.setDisconnected(true);
        _bot.connect();
    }

    /** Minimal irc.conf for one plain (non-ssl) server with SASL configured. */
    static Properties baseProperties(int port, boolean required) {
        Properties p = new Properties();
        p.setProperty("ssl.strictTrust", "false");
        p.setProperty("server.1.host", InetAddress.getLoopbackAddress().getHostAddress());
        p.setProperty("server.1.port", Integer.toString(port));
        p.setProperty("server.1.password", "");
        p.setProperty("server.1.use_ssl", "false");
        p.setProperty("server.1.sasl.username", "drftpd");
        p.setProperty("server.1.sasl.password", PASSWORD);
        p.setProperty("server.1.sasl.required", Boolean.toString(required));
        p.setProperty("connect.delay", "0");
        p.setProperty("message.sendDelay", "0");
        p.setProperty("nick.auto", "false");
        p.setProperty("name", "drftpd");
        p.setProperty("nick", "drftpd");
        p.setProperty("user", "drftpd");
        p.setProperty("services.nickserv.enable", "false");
        p.setProperty("services.nickserv.register.joingroup", "false");
        p.setProperty("services.nickserv.register.new", "false");
        p.setProperty("services.chanserv.enable", "false");
        p.setProperty("channel.autojoin", "false");
        p.setProperty("charset", "UTF-8");
        p.setProperty("blowfish.enable", "false");
        p.setProperty("blowfish.dh1080.enable", "false");
        p.setProperty("blowfish.dh1080.ecb", "false");
        p.setProperty("blowfish.dh1080.cbc", "false");
        p.setProperty("blowfish.unencrypted.punish", "false");
        p.setProperty("bot.name", "testbot");
        p.setProperty("commands.max", "1");
        p.setProperty("commands.full", "block");
        return p;
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = SiteBot.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    private static Object getField(Object target, String name) throws Exception {
        Field f = SiteBot.class.getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }

    private void startServer(Mode... modes) throws IOException {
        _serverSocket = new ServerSocket(0, 5, InetAddress.getLoopbackAddress());
        AtomicInteger index = new AtomicInteger();
        _serverThread = new Thread(() -> {
            try {
                while (!_serverSocket.isClosed()) {
                    Socket s = _serverSocket.accept();
                    int i = index.getAndIncrement();
                    Mode mode = modes[Math.min(i, modes.length - 1)];
                    List<String> received = new CopyOnWriteArrayList<>();
                    _connections.add(received);
                    Thread t = new Thread(() -> serve(s, mode, received), "fake-ircd-conn-" + i);
                    t.setDaemon(true);
                    t.start();
                }
            } catch (IOException e) {
                // server closed
            }
        }, "fake-ircd");
        _serverThread.setDaemon(true);
        _serverThread.start();
    }

    private static void serve(Socket s, Mode mode, List<String> received) {
        try (s) {
            BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8));
            boolean capHeld = false;
            boolean nick = false;
            boolean user = false;
            boolean welcomed = false;
            String line;
            while ((line = in.readLine()) != null) {
                received.add(line);
                List<String> reply = new ArrayList<>();
                if (line.startsWith("CAP LS")) {
                    if (mode == Mode.NO_CAP) {
                        reply.add(":fake.ircd 421 * CAP :Unknown command");
                    } else if (mode == Mode.BARE_451) {
                        reply.add(":fake.ircd 451 * :You have not registered");
                    } else {
                        capHeld = true;
                        reply.add(":fake.ircd CAP * LS * :multi-prefix away-notify");
                        reply.add(":fake.ircd CAP * LS :sasl=PLAIN,EXTERNAL server-time");
                    }
                } else if (line.equals("CAP REQ :sasl")) {
                    reply.add(":fake.ircd CAP * ACK :sasl");
                } else if (line.equals("AUTHENTICATE PLAIN")) {
                    reply.add("AUTHENTICATE +");
                } else if (line.startsWith("AUTHENTICATE ")) {
                    if (mode == Mode.SASL_OK) {
                        reply.add(":fake.ircd 900 drftpd drftpd!drftpd@test.host drftpd :You are now logged in as drftpd");
                        reply.add(":fake.ircd 903 drftpd :SASL authentication successful");
                    } else {
                        reply.add(":fake.ircd 904 drftpd :SASL authentication failed");
                    }
                } else if (line.equals("CAP END")) {
                    capHeld = false;
                } else if (line.startsWith("NICK ")) {
                    nick = true;
                } else if (line.startsWith("USER ")) {
                    user = true;
                } else if (line.startsWith("WHOIS ")) {
                    reply.add(":fake.ircd 311 drftpd drftpd drftpd test.host * :drftpd");
                    reply.add(":fake.ircd 318 drftpd drftpd :End of /WHOIS list.");
                } else if (line.startsWith("QUIT")) {
                    return;
                }
                if (!welcomed && !capHeld && nick && user) {
                    welcomed = true;
                    reply.add(":fake.ircd 001 drftpd :Welcome to the fake network drftpd");
                    reply.add(":fake.ircd 002 drftpd :Your host is fake.ircd");
                    reply.add(":fake.ircd 003 drftpd :This server was created today");
                    reply.add(":fake.ircd 004 drftpd fake.ircd fake-1.0 iow ovb");
                }
                for (String r : reply) {
                    out.write(r + "\r\n");
                }
                out.flush();
            }
        } catch (IOException e) {
            // connection closed
        }
    }
}
