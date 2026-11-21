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

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class SaslNegotiatorTest {

    private static final String USER = "drftpd";
    private static final String PASS = "s3cret";
    private static final String PAYLOAD = Base64.getEncoder()
            .encodeToString("\0drftpd\0s3cret".getBytes(StandardCharsets.UTF_8));

    private final List<String> sent = new ArrayList<>();

    private SaslNegotiator start(boolean required) {
        SaslNegotiator n = new SaslNegotiator(USER, PASS, required, 30000L);
        sent.addAll(n.start(0L));
        return n;
    }

    private SaslNegotiator.Result feed(SaslNegotiator n, String line) {
        SaslNegotiator.Result r = n.handleLine(line, 1L);
        sent.addAll(r.getLinesToSend());
        return r;
    }

    private long count(String line) {
        return sent.stream().filter(line::equals).count();
    }

    private void negotiateUpToCredentials(SaslNegotiator n) {
        feed(n, ":irc.example.net CAP * LS :multi-prefix sasl");
        feed(n, ":irc.example.net CAP * ACK :sasl");
        feed(n, "AUTHENTICATE +");
    }

    @Test
    public void offeredAndSuccess() {
        SaslNegotiator n = start(false);
        assertEquals(List.of("CAP LS 302"), sent);
        assertTrue(feed(n, ":irc.example.net CAP * LS :multi-prefix sasl=PLAIN,EXTERNAL account-notify").isConsumed());
        assertEquals("CAP REQ :sasl", sent.get(sent.size() - 1));
        feed(n, ":irc.example.net CAP * ACK :sasl");
        assertEquals("AUTHENTICATE PLAIN", sent.get(sent.size() - 1));
        feed(n, "AUTHENTICATE +");
        assertEquals("AUTHENTICATE " + PAYLOAD, sent.get(sent.size() - 1));
        assertTrue(feed(n, ":irc.example.net 900 drftpd drftpd!drftpd@host drftpdacct :You are now logged in as drftpdacct").isConsumed());
        assertTrue(n.isPending());
        feed(n, ":irc.example.net 903 drftpd :SASL authentication successful");
        assertEquals(List.of("CAP LS 302", "CAP REQ :sasl", "AUTHENTICATE PLAIN", "AUTHENTICATE " + PAYLOAD, "CAP END"), sent);
        assertTrue(n.isAuthenticated());
        assertFalse(n.isPending());
        assertEquals("drftpdacct", n.getAccount());
        // After completion, lines are no longer consumed
        assertFalse(feed(n, ":irc.example.net 001 drftpd :Welcome").isConsumed());
        assertEquals(1, count("CAP END"));
    }

    @Test
    public void payloadIsPlainWithEmptyAuthzid() {
        List<String> lines = SaslNegotiator.buildPlainPayload(USER, PASS);
        assertEquals(1, lines.size());
        byte[] decoded = Base64.getDecoder().decode(lines.get(0).substring("AUTHENTICATE ".length()));
        assertEquals("\0drftpd\0s3cret", new String(decoded, StandardCharsets.UTF_8));
    }

    @Test
    public void multiLineLs() {
        SaslNegotiator n = start(false);
        assertTrue(feed(n, ":irc.example.net CAP * LS * :multi-prefix away-notify").isConsumed());
        assertEquals(List.of("CAP LS 302"), sent);
        feed(n, ":irc.example.net CAP * LS * :account-notify extended-join");
        assertEquals(List.of("CAP LS 302"), sent);
        feed(n, ":irc.example.net CAP * LS :sasl=PLAIN server-time");
        assertEquals("CAP REQ :sasl", sent.get(sent.size() - 1));
    }

    @Test
    public void multiLineLsWithoutSasl() {
        SaslNegotiator n = start(false);
        feed(n, ":irc.example.net CAP * LS * :multi-prefix away-notify");
        feed(n, ":irc.example.net CAP * LS :server-time");
        assertEquals(List.of("CAP LS 302", "CAP END"), sent);
        assertEquals(SaslNegotiator.State.FAILED, n.getState());
    }

    @Test
    public void externalOnlyEndsWithoutAuthenticate() {
        SaslNegotiator n = start(false);
        feed(n, ":irc.example.net CAP * LS :sasl=EXTERNAL multi-prefix");
        assertEquals(List.of("CAP LS 302", "CAP END"), sent);
        assertTrue(sent.stream().noneMatch(l -> l.startsWith("AUTHENTICATE")));
        assertFalse(n.isPending());
        assertFalse(n.isAborted());
    }

    @Test
    public void externalOnlyRequiredAborts() {
        SaslNegotiator n = start(true);
        feed(n, ":irc.example.net CAP * LS :sasl=EXTERNAL");
        assertEquals(List.of("CAP LS 302"), sent);
        assertTrue(n.isAborted());
    }

    @Test
    public void nak() {
        SaslNegotiator n = start(false);
        feed(n, ":irc.example.net CAP * LS :sasl");
        feed(n, ":irc.example.net CAP * NAK :sasl");
        assertEquals(List.of("CAP LS 302", "CAP REQ :sasl", "CAP END"), sent);
        assertEquals(SaslNegotiator.State.FAILED, n.getState());
    }

    @Test
    public void failure904NotRequired() {
        SaslNegotiator n = start(false);
        negotiateUpToCredentials(n);
        assertTrue(feed(n, ":irc.example.net 904 drftpd :SASL authentication failed").isConsumed());
        assertEquals("CAP END", sent.get(sent.size() - 1));
        assertEquals(1, count("CAP END"));
        assertEquals(SaslNegotiator.State.FAILED, n.getState());
        assertFalse(n.isAborted());
    }

    @Test
    public void failure904Required() {
        SaslNegotiator n = start(true);
        negotiateUpToCredentials(n);
        feed(n, ":irc.example.net 904 drftpd :SASL authentication failed");
        assertEquals(0, count("CAP END"));
        assertTrue(n.isAborted());
        assertNotNull(n.getFailureReason());
    }

    @Test
    public void mechanisms908Then904() {
        SaslNegotiator n = start(false);
        feed(n, ":irc.example.net CAP * LS :sasl");
        feed(n, ":irc.example.net CAP * ACK :sasl");
        assertTrue(feed(n, ":irc.example.net 908 drftpd EXTERNAL :are available SASL mechanisms").isConsumed());
        assertTrue(n.isPending());
        assertEquals(0, count("CAP END"));
        feed(n, ":irc.example.net 904 drftpd :SASL authentication failed");
        assertEquals(1, count("CAP END"));
        assertFalse(n.isPending());
    }

    @Test
    public void otherFailureNumerics() {
        for (String code : new String[]{"902", "905", "906"}) {
            sent.clear();
            SaslNegotiator n = start(false);
            negotiateUpToCredentials(n);
            feed(n, ":irc.example.net " + code + " drftpd :nope");
            assertEquals(1, count("CAP END"), code);
            assertEquals(SaslNegotiator.State.FAILED, n.getState(), code);
        }
    }

    @Test
    public void alreadyAuthenticated907() {
        SaslNegotiator n = start(false);
        feed(n, ":irc.example.net CAP * LS :sasl");
        feed(n, ":irc.example.net CAP * ACK :sasl");
        feed(n, ":irc.example.net 907 drftpd :You have already authenticated using SASL");
        assertEquals(1, count("CAP END"));
        assertTrue(n.isAuthenticated());
    }

    @Test
    public void chunkingExactMultipleOf400() {
        // "\0" + user + "\0" + pass of 300 bytes -> base64 of exactly 400 chars
        String user = "u";
        String pass = "p".repeat(300 - 2 - user.length());
        List<String> lines = SaslNegotiator.buildPlainPayload(user, pass);
        assertEquals(2, lines.size());
        assertEquals(400, lines.get(0).length() - "AUTHENTICATE ".length());
        assertEquals("AUTHENTICATE +", lines.get(1));

        // 600 bytes -> 800 chars -> two full chunks then +
        lines = SaslNegotiator.buildPlainPayload(user, "p".repeat(600 - 3));
        assertEquals(3, lines.size());
        assertEquals("AUTHENTICATE +", lines.get(2));

        // 301 bytes -> 404 chars -> 400 + 4, no trailing +
        lines = SaslNegotiator.buildPlainPayload(user, "p".repeat(301 - 3));
        assertEquals(2, lines.size());
        assertEquals(400, lines.get(0).length() - "AUTHENTICATE ".length());
        assertEquals(4, lines.get(1).length() - "AUTHENTICATE ".length());

        StringBuilder joined = new StringBuilder();
        for (String l : lines) {
            joined.append(l.substring("AUTHENTICATE ".length()));
        }
        assertEquals("\0u\0" + "p".repeat(298),
                new String(Base64.getDecoder().decode(joined.toString()), StandardCharsets.UTF_8));
    }

    @Test
    public void chunkingThroughNegotiation() {
        String pass = "p".repeat(300 - 2 - USER.length());
        SaslNegotiator n = new SaslNegotiator(USER, pass, false, 30000L);
        sent.addAll(n.start(0L));
        negotiateUpToCredentials(n);
        assertEquals("AUTHENTICATE +", sent.get(sent.size() - 1));
        assertEquals(400, sent.get(sent.size() - 2).length() - "AUTHENTICATE ".length());
    }

    @Test
    public void noCapSupport421() {
        SaslNegotiator n = start(false);
        assertTrue(feed(n, ":irc.example.net 421 * CAP :Unknown command").isConsumed());
        assertEquals(List.of("CAP LS 302"), sent);
        assertFalse(n.isPending());
        assertFalse(n.isAborted());
        // registration continues normally
        assertFalse(feed(n, ":irc.example.net 001 drftpd :Welcome").isConsumed());
    }

    @Test
    public void unrelated421NotConsumed() {
        SaslNegotiator n = start(false);
        assertFalse(feed(n, ":irc.example.net 421 * FOO :Unknown command").isConsumed());
        assertTrue(n.isPending());
    }

    @Test
    public void noCapSupportDirect001() {
        SaslNegotiator n = start(false);
        assertFalse(feed(n, ":irc.example.net 001 drftpd :Welcome to EFnet").isConsumed());
        assertEquals(List.of("CAP LS 302"), sent);
        assertEquals(SaslNegotiator.State.FAILED, n.getState());
    }

    @Test
    public void noCapSupportRequiredAborts() {
        SaslNegotiator n = start(true);
        feed(n, ":irc.example.net 001 drftpd :Welcome to EFnet");
        assertTrue(n.isAborted());
    }

    @Test
    public void timeout() {
        SaslNegotiator n = new SaslNegotiator(USER, PASS, false, 30000L);
        sent.addAll(n.start(1000L));
        feed(n, ":irc.example.net CAP * LS :sasl");
        assertEquals(29000L, n.millisUntilTimeout(2000L));
        assertTrue(n.checkTimeout(30999L).isEmpty());
        assertTrue(n.isPending());
        sent.addAll(n.checkTimeout(31000L));
        assertEquals("CAP END", sent.get(sent.size() - 1));
        assertFalse(n.isPending());
        // a second timeout or late reply never sends CAP END again
        sent.addAll(n.checkTimeout(40000L));
        feed(n, ":irc.example.net CAP * ACK :sasl");
        assertEquals(1, count("CAP END"));
        assertTrue(sent.stream().noneMatch(l -> l.startsWith("AUTHENTICATE")));
    }

    @Test
    public void timeoutRequiredAborts() {
        SaslNegotiator n = start(true);
        assertTrue(n.checkTimeout(30000L).isEmpty());
        assertTrue(n.isAborted());
    }

    @Test
    public void unexpectedChallengeAborts() {
        SaslNegotiator n = start(false);
        feed(n, ":irc.example.net CAP * LS :sasl");
        feed(n, ":irc.example.net CAP * ACK :sasl");
        feed(n, "AUTHENTICATE Zm9v");
        assertEquals("AUTHENTICATE *", sent.get(sent.size() - 1));
        feed(n, ":irc.example.net 906 drftpd :SASL authentication aborted");
        assertEquals(1, count("CAP END"));
    }

    @Test
    public void tagsAndNoPrefixAreParsed() {
        SaslNegotiator n = start(false);
        feed(n, "@time=2026-01-01T00:00:00Z CAP * LS :sasl");
        assertEquals("CAP REQ :sasl", sent.get(sent.size() - 1));
    }

    @Test
    public void pingIsNotConsumed() {
        SaslNegotiator n = start(false);
        assertFalse(feed(n, "PING :irc.example.net").isConsumed());
        assertTrue(n.isPending());
    }

    @Test
    public void requiresCredentials() {
        assertThrows(IllegalArgumentException.class, () -> new SaslNegotiator("", PASS, false, 1L));
        assertThrows(IllegalArgumentException.class, () -> new SaslNegotiator(USER, null, false, 1L));
    }

    @Test
    public void maskForLog() {
        assertEquals("AUTHENTICATE <masked>", SaslNegotiator.maskForLog("AUTHENTICATE " + PAYLOAD));
        assertEquals("authenticate <masked>", SaslNegotiator.maskForLog("authenticate " + PAYLOAD));
        assertEquals(":srv AUTHENTICATE <masked>", SaslNegotiator.maskForLog(":srv AUTHENTICATE abc"));
        assertEquals("AUTHENTICATE +", SaslNegotiator.maskForLog("AUTHENTICATE +"));
        assertEquals("AUTHENTICATE PLAIN", SaslNegotiator.maskForLog("AUTHENTICATE PLAIN"));
        assertEquals("AUTHENTICATE *", SaslNegotiator.maskForLog("AUTHENTICATE *"));
        assertEquals("PRIVMSG #chan :AUTHENTICATE x", SaslNegotiator.maskForLog("PRIVMSG #chan :AUTHENTICATE x"));
        for (String l : SaslNegotiator.buildPlainPayload(USER, "p".repeat(500))) {
            assertFalse(SaslNegotiator.maskForLog(l).contains("cHBw"), l);
        }
    }
}
