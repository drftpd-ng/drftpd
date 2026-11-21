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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * IRCv3 capability negotiation (CAP LS 302 / REQ / END) and SASL PLAIN
 * authentication state machine used while the sitebot registers with a server.
 * <p>
 * This class performs no I/O: the caller feeds it every line received from the
 * server during registration and sends the lines it returns. It guarantees that
 * {@code CAP END} is emitted exactly once on every path that ends the negotiation
 * while the server is holding registration (success, failure, SASL not offered,
 * {@code CAP NAK}, timeout), unless SASL is required and failed, in which case
 * {@link #isAborted()} becomes true and the caller must drop the connection.
 *
 * @see <a href="https://ircv3.net/specs/extensions/capability-negotiation">Capability negotiation</a>
 * @see <a href="https://ircv3.net/specs/extensions/sasl-3.1">SASL 3.1</a>
 */
public class SaslNegotiator {

    private static final Logger logger = LogManager.getLogger(SaslNegotiator.class);

    /** Default time allowed for the whole CAP/SASL exchange. */
    public static final long DEFAULT_TIMEOUT_MILLIS = 30000L;

    /** Maximum length of a single AUTHENTICATE payload chunk (SASL 3.1). */
    static final int CHUNK_SIZE = 400;

    public enum State {
        /** start() not called yet. */
        NEW,
        /** CAP LS 302 sent, waiting for (the last line of) the LS reply. */
        CAP_LS,
        /** CAP REQ :sasl sent, waiting for ACK/NAK. */
        CAP_REQ,
        /** AUTHENTICATE PLAIN sent, waiting for "AUTHENTICATE +". */
        AUTH_MECHANISM,
        /** Credentials sent, waiting for 903 or a failure numeric. */
        AUTH_RESULT,
        /** Finished: authenticated, CAP END sent. */
        SUCCEEDED,
        /** Finished without authentication, registration continues (CAP END sent if needed). */
        FAILED,
        /** Finished without authentication and SASL is required: the caller must disconnect. */
        ABORTED
    }

    /** Outcome of handing one server line to the negotiator. */
    public static final class Result {
        private final boolean _consumed;
        private final List<String> _toSend;

        Result(boolean consumed, List<String> toSend) {
            _consumed = consumed;
            _toSend = toSend;
        }

        /** True when the line belonged to the CAP/SASL exchange and must not be processed further. */
        public boolean isConsumed() {
            return _consumed;
        }

        /** Raw lines (without CRLF) to send to the server, in order. */
        public List<String> getLinesToSend() {
            return _toSend;
        }
    }

    private static final Result IGNORED = new Result(false, Collections.emptyList());

    private final String _username;
    private final String _password;
    private final boolean _required;
    private final long _timeoutMillis;

    private State _state = State.NEW;
    private long _deadline;
    private boolean _capEndSent = false;
    private final StringBuilder _lsCaps = new StringBuilder();
    private String _failureReason;
    private String _account;

    public SaslNegotiator(String username, String password, boolean required, long timeoutMillis) {
        if (username == null || username.isEmpty() || password == null || password.isEmpty()) {
            throw new IllegalArgumentException("SASL username and password must both be set");
        }
        _username = username;
        _password = password;
        _required = required;
        _timeoutMillis = timeoutMillis;
    }

    /**
     * Starts the negotiation.
     *
     * @return the lines to send before NICK/USER (after PASS, if any)
     */
    public List<String> start(long now) {
        if (_state != State.NEW) {
            throw new IllegalStateException("Negotiation already started");
        }
        _state = State.CAP_LS;
        _deadline = now + _timeoutMillis;
        return List.of("CAP LS 302");
    }

    public State getState() {
        return _state;
    }

    /** True while the exchange is still running. */
    public boolean isPending() {
        return _state == State.CAP_LS || _state == State.CAP_REQ
                || _state == State.AUTH_MECHANISM || _state == State.AUTH_RESULT;
    }

    public boolean isAuthenticated() {
        return _state == State.SUCCEEDED;
    }

    /** True when SASL is required and failed: the caller must close the connection. */
    public boolean isAborted() {
        return _state == State.ABORTED;
    }

    public boolean isCapEndSent() {
        return _capEndSent;
    }

    public String getFailureReason() {
        return _failureReason;
    }

    /** Account name reported by 900 RPL_LOGGEDIN, if any. */
    public String getAccount() {
        return _account;
    }

    /** Milliseconds left before {@link #checkTimeout(long)} fires, never negative. */
    public long millisUntilTimeout(long now) {
        return Math.max(0L, _deadline - now);
    }

    /**
     * Must be called when no line arrived before the deadline.
     *
     * @return the lines to send (CAP END when falling back), empty when nothing to do
     */
    public List<String> checkTimeout(long now) {
        if (!isPending() || now < _deadline) {
            return Collections.emptyList();
        }
        return fail("SASL negotiation timed out after " + _timeoutMillis + "ms", true);
    }

    /**
     * Handles one line received from the server.
     */
    public Result handleLine(String rawLine, long now) {
        if (!isPending() || rawLine == null) {
            return IGNORED;
        }
        List<String> params = new ArrayList<>();
        String command = parse(rawLine, params);
        if (command == null) {
            return IGNORED;
        }

        switch (command) {
            case "CAP":
                return new Result(true, handleCap(params));
            case "AUTHENTICATE":
                return new Result(true, handleAuthenticate(params));
            case "001":
                // The server ignored CAP and completed registration: it does not support capability negotiation.
                // No CAP END is needed (nor wise: it would only produce an error).
                fail("server completed registration without capability negotiation (no CAP support)", false);
                return IGNORED;
            case "421":
            case "451":
                // 421 ERR_UNKNOWNCOMMAND / 451 ERR_NOTREGISTERED for CAP: no CAP support, registration not held.
                // While waiting for the LS reply any 421/451 counts, as some servers answer a bare
                // ":srv 451 * :You have not registered" without naming the command.
                if (_state == State.CAP_LS
                        || (params.size() >= 2 && params.get(1).equalsIgnoreCase("CAP"))) {
                    fail("server does not support capability negotiation (" + command + ")", false);
                    return new Result(true, Collections.emptyList());
                }
                return IGNORED;
            case "410":
                // ERR_INVALIDCAPCMD
                return new Result(true, fail("server rejected CAP command (410)", true));
            case "900":
                // RPL_LOGGEDIN <nick> <nick>!<ident>@<host> <account> :You are now logged in as <user>
                if (params.size() >= 3) {
                    _account = params.get(2);
                }
                logger.info("SASL: logged in as account [{}]", _account);
                return new Result(true, Collections.emptyList());
            case "901":
                // RPL_LOGGEDOUT
                return new Result(true, Collections.emptyList());
            case "903":
                // RPL_SASLSUCCESS
                if (_state == State.AUTH_RESULT || _state == State.AUTH_MECHANISM) {
                    _state = State.SUCCEEDED;
                    logger.info("SASL authentication successful");
                    return new Result(true, capEnd());
                }
                return new Result(true, Collections.emptyList());
            case "907":
                // ERR_SASLALREADY: we are already authenticated, nothing more to do.
                _state = State.SUCCEEDED;
                logger.info("SASL: already authenticated");
                return new Result(true, capEnd());
            case "908":
                // RPL_SASLMECHS: informational, a 904 follows.
                logger.warn("SASL: server supports mechanisms [{}] only", params.size() >= 2 ? params.get(1) : "");
                return new Result(true, Collections.emptyList());
            case "902":
            case "904":
            case "905":
            case "906":
                String text = params.isEmpty() ? "" : params.get(params.size() - 1);
                return new Result(true, fail("SASL authentication failed (" + command + ": " + text + ")", true));
            default:
                return IGNORED;
        }
    }

    private List<String> handleCap(List<String> params) {
        // CAP <target> <subcommand> [*] :<caps>
        if (params.size() < 2) {
            return Collections.emptyList();
        }
        String sub = params.get(1).toUpperCase(Locale.ROOT);
        switch (sub) {
            case "LS": {
                if (_state != State.CAP_LS) {
                    return Collections.emptyList();
                }
                boolean more = params.size() >= 4 && params.get(2).equals("*");
                String caps = params.size() >= 3 ? params.get(params.size() - 1) : "";
                if (_lsCaps.length() > 0) {
                    _lsCaps.append(' ');
                }
                _lsCaps.append(caps);
                if (more) {
                    return Collections.emptyList();
                }
                return onCapLsComplete(_lsCaps.toString());
            }
            case "ACK": {
                if (_state != State.CAP_REQ) {
                    return Collections.emptyList();
                }
                String caps = params.size() >= 3 ? params.get(params.size() - 1) : "";
                if (containsCap(caps, "sasl") != null) {
                    _state = State.AUTH_MECHANISM;
                    return List.of("AUTHENTICATE PLAIN");
                }
                return fail("server did not acknowledge the sasl capability", true);
            }
            case "NAK":
                if (_state != State.CAP_REQ) {
                    return Collections.emptyList();
                }
                return fail("server refused the sasl capability (CAP NAK)", true);
            default:
                // NEW, DEL, LIST... are irrelevant here
                return Collections.emptyList();
        }
    }

    private List<String> onCapLsComplete(String caps) {
        String value = containsCap(caps, "sasl");
        if (value == null) {
            return fail("server does not offer the sasl capability", true);
        }
        if (!value.isEmpty()) {
            boolean plain = false;
            for (String mech : value.split(",")) {
                if (mech.trim().equalsIgnoreCase("PLAIN")) {
                    plain = true;
                    break;
                }
            }
            if (!plain) {
                return fail("server does not offer SASL PLAIN (offered: " + value + ")", true);
            }
        }
        _state = State.CAP_REQ;
        return List.of("CAP REQ :sasl");
    }

    private List<String> handleAuthenticate(List<String> params) {
        if (_state != State.AUTH_MECHANISM) {
            return Collections.emptyList();
        }
        if (!params.isEmpty() && params.get(0).equals("+")) {
            _state = State.AUTH_RESULT;
            return buildPlainPayload(_username, _password);
        }
        // Unexpected challenge for PLAIN: abort the exchange, the server answers with 906.
        _state = State.AUTH_RESULT;
        return List.of("AUTHENTICATE *");
    }

    /**
     * Ends the negotiation without authentication.
     *
     * @param needCapEnd whether the server is holding registration and needs CAP END
     */
    private List<String> fail(String reason, boolean needCapEnd) {
        _failureReason = reason;
        if (_required) {
            _state = State.ABORTED;
            logger.error("{}; sasl.required=true, disconnecting", reason);
            return Collections.emptyList();
        }
        _state = State.FAILED;
        logger.warn("{}; continuing registration without SASL", reason);
        return needCapEnd ? capEnd() : Collections.emptyList();
    }

    private List<String> capEnd() {
        if (_capEndSent) {
            return Collections.emptyList();
        }
        _capEndSent = true;
        return List.of("CAP END");
    }

    /**
     * Builds the AUTHENTICATE lines for SASL PLAIN: base64(authzid NUL authcid NUL password) with an
     * empty authzid, split in 400 byte chunks, followed by "AUTHENTICATE +" when the last chunk is
     * exactly 400 bytes long.
     */
    static List<String> buildPlainPayload(String username, String password) {
        byte[] user = username.getBytes(StandardCharsets.UTF_8);
        byte[] pass = password.getBytes(StandardCharsets.UTF_8);
        byte[] raw = new byte[user.length + pass.length + 2];
        raw[0] = 0;
        System.arraycopy(user, 0, raw, 1, user.length);
        raw[user.length + 1] = 0;
        System.arraycopy(pass, 0, raw, user.length + 2, pass.length);
        String encoded = Base64.getEncoder().encodeToString(raw);

        List<String> lines = new ArrayList<>();
        for (int i = 0; i < encoded.length(); i += CHUNK_SIZE) {
            lines.add("AUTHENTICATE " + encoded.substring(i, Math.min(encoded.length(), i + CHUNK_SIZE)));
        }
        if (encoded.length() % CHUNK_SIZE == 0) {
            lines.add("AUTHENTICATE +");
        }
        return lines;
    }

    /**
     * Masks AUTHENTICATE payloads so credentials never reach the logs.
     */
    public static String maskForLog(String line) {
        if (line == null) {
            return null;
        }
        int start = 0;
        // skip optional tags and prefix
        while (start < line.length() && (line.charAt(start) == '@' || line.charAt(start) == ':')) {
            int sp = line.indexOf(' ', start);
            if (sp < 0) {
                return line;
            }
            start = sp + 1;
        }
        if (line.regionMatches(true, start, "AUTHENTICATE ", 0, 13)) {
            String arg = line.substring(start + 13).trim();
            if (arg.equals("+") || arg.equals("*") || arg.equalsIgnoreCase("PLAIN")) {
                return line;
            }
            return line.substring(0, start + 13) + "<masked>";
        }
        return line;
    }

    /**
     * Returns the value of the capability (empty string when it has none) or null when absent.
     */
    private static String containsCap(String caps, String name) {
        for (String cap : caps.trim().split(" +")) {
            if (cap.isEmpty()) {
                continue;
            }
            String key = cap;
            String value = "";
            int eq = cap.indexOf('=');
            if (eq >= 0) {
                key = cap.substring(0, eq);
                value = cap.substring(eq + 1);
            }
            if (key.equalsIgnoreCase(name)) {
                return value;
            }
        }
        return null;
    }

    /**
     * Minimal IRC line parser: strips tags and prefix, fills params (trailing included) and
     * returns the upper-cased command, or null for an empty line.
     */
    static String parse(String line, List<String> params) {
        int pos = 0;
        int len = line.length();
        if (pos < len && line.charAt(pos) == '@') {
            int sp = line.indexOf(' ', pos);
            if (sp < 0) {
                return null;
            }
            pos = sp + 1;
        }
        while (pos < len && line.charAt(pos) == ' ') {
            pos++;
        }
        if (pos < len && line.charAt(pos) == ':') {
            int sp = line.indexOf(' ', pos);
            if (sp < 0) {
                return null;
            }
            pos = sp + 1;
        }
        String command = null;
        while (pos < len) {
            while (pos < len && line.charAt(pos) == ' ') {
                pos++;
            }
            if (pos >= len) {
                break;
            }
            if (command != null && line.charAt(pos) == ':') {
                params.add(line.substring(pos + 1));
                break;
            }
            int sp = line.indexOf(' ', pos);
            String token = sp < 0 ? line.substring(pos) : line.substring(pos, sp);
            if (command == null) {
                command = token.toUpperCase(Locale.ROOT);
            } else {
                params.add(token);
            }
            pos = sp < 0 ? len : sp + 1;
        }
        return command;
    }
}
