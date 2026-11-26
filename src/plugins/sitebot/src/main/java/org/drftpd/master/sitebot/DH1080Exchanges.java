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

import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Key exchanges started by the bot that wait for the peer's DH1080_FINISH.
 * <p>
 * At most one exchange per nick is in progress: a second start while one is pending
 * (and younger than {@link #TIMEOUT_MILLIS}) is ignored, otherwise the peer's reply to
 * the first DH1080_INIT would be combined with the private key of the second one.
 * Exchanges older than the timeout are dropped. Safe for use from several threads.
 */
public class DH1080Exchanges {

    public static final long TIMEOUT_MILLIS = 60_000L;

    private record Pending(DH1080 exchange, long startedMillis) {
    }

    private final ConcurrentHashMap<String, Pending> _pending = new ConcurrentHashMap<>();

    private final LongSupplier _clock;

    public DH1080Exchanges() {
        this(System::currentTimeMillis);
    }

    DH1080Exchanges(LongSupplier clock) {
        _clock = clock;
    }

    private static String key(String nick) {
        return nick.toLowerCase(Locale.ROOT);
    }

    private static boolean isExpired(Pending pending, long now) {
        return now - pending.startedMillis() >= TIMEOUT_MILLIS;
    }

    /**
     * Starts a new exchange with the given nick unless one is already in progress.
     *
     * @return the new exchange whose public key must be sent in DH1080_INIT, or null
     * if an exchange with this nick is still pending
     */
    public DH1080 start(String nick) {
        long now = _clock.getAsLong();
        _pending.values().removeIf(p -> isExpired(p, now));
        boolean[] created = {false};
        Pending pending = _pending.compute(key(nick), (k, old) -> {
            if (old != null && !isExpired(old, now)) {
                return old;
            }
            created[0] = true;
            return new Pending(new DH1080(), now);
        });
        return created[0] ? pending.exchange() : null;
    }

    /**
     * Removes and returns the pending exchange with the given nick.
     *
     * @return the exchange, or null if none is pending or it has expired
     */
    public DH1080 finish(String nick) {
        Pending pending = _pending.remove(key(nick));
        if (pending == null || isExpired(pending, _clock.getAsLong())) {
            return null;
        }
        return pending.exchange();
    }
}
