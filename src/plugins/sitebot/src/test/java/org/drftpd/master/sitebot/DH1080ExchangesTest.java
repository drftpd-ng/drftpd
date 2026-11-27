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

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

public class DH1080ExchangesTest {

    private final AtomicLong _now = new AtomicLong(1_000_000L);

    private final DH1080Exchanges _exchanges = new DH1080Exchanges(_now::get);

    @Test
    public void testSecondStartWhilePendingIsIgnored() {
        DH1080 first = _exchanges.start("Nick");
        assertNotNull(first);
        _now.addAndGet(1000);
        assertNull(_exchanges.start("nick"));
        assertNull(_exchanges.start("NICK"));
        // The reply is matched with the first exchange, whatever the nick case
        assertSame(first, _exchanges.finish("nICK"));
        assertNull(_exchanges.finish("nick"));
    }

    @Test
    public void testReplyCompletesWithFirstExchange() {
        DH1080 first = _exchanges.start("nick");
        assertNull(_exchanges.start("nick"));
        DH1080 peer = new DH1080();
        DH1080 pending = _exchanges.finish("nick");
        assertEquals(peer.getSharedSecret(first.getPublicKey()), pending.getSharedSecret(peer.getPublicKey()));
    }

    @Test
    public void testExpiredExchange() {
        DH1080 first = _exchanges.start("nick");
        _now.addAndGet(DH1080Exchanges.TIMEOUT_MILLIS);
        // A late reply is not used
        assertNull(_exchanges.finish("nick"));
        // A stale exchange can be replaced by a new one
        _exchanges.start("nick");
        _now.addAndGet(DH1080Exchanges.TIMEOUT_MILLIS);
        DH1080 second = _exchanges.start("nick");
        assertNotNull(second);
        assertNotSame(first, second);
        assertSame(second, _exchanges.finish("nick"));
    }

    @Test
    public void testUnknownNick() {
        assertNull(_exchanges.finish("nobody"));
        assertNotNull(_exchanges.start("a"));
        assertNotNull(_exchanges.start("b"));
        assertNull(_exchanges.finish("c"));
    }

    @Test
    public void testCancelDropsPendingExchange() {
        DH1080 first = _exchanges.start("Nick");
        assertNotNull(first);
        // The peer sent its own DH1080_INIT: a late reply to ours must not be used
        _exchanges.cancel("nick");
        assertNull(_exchanges.finish("nick"));
        // and the bot may start a new exchange later
        DH1080 second = _exchanges.start("nick");
        assertNotNull(second);
        assertNotSame(first, second);
        // Cancelling an unknown nick does nothing
        _exchanges.cancel("nobody");
        assertSame(second, _exchanges.finish("nick"));
    }

    @Test
    public void testRefusedExchangeBlocksNewStartUntilTimeout() {
        DH1080 first = _exchanges.start("nick");
        assertNotNull(first);
        _now.addAndGet(10_000);
        assertTrue(_exchanges.refuse("Nick"));
        // Only the first refusal of an exchange reports true, so it is logged once
        assertFalse(_exchanges.refuse("nick"));
        // The refused exchange cannot be completed
        assertNull(_exchanges.finish("nick"));
        // and no new exchange starts before the original one times out
        _now.addAndGet(DH1080Exchanges.TIMEOUT_MILLIS - 10_001);
        assertNull(_exchanges.start("nick"));
        assertNull(_exchanges.finish("nick"));
        _now.addAndGet(1);
        DH1080 second = _exchanges.start("nick");
        assertNotNull(second);
        assertNotSame(first, second);
        assertSame(second, _exchanges.finish("nick"));
    }

    @Test
    public void testRefuseWithoutPendingExchange() {
        assertFalse(_exchanges.refuse("nobody"));
        assertNotNull(_exchanges.start("nick"));
        _now.addAndGet(DH1080Exchanges.TIMEOUT_MILLIS);
        assertFalse(_exchanges.refuse("nick"));
        assertNotNull(_exchanges.start("nick"));
    }

    @Test
    public void testCancelClearsRefusedMarker() {
        assertNotNull(_exchanges.start("nick"));
        assertTrue(_exchanges.refuse("nick"));
        _exchanges.cancel("nick");
        assertNotNull(_exchanges.start("nick"));
    }
}
