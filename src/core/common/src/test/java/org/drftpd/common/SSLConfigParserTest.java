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

import org.drftpd.common.util.SSLConfigParser;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class SSLConfigParserTest {

    private static final String[] PROTOCOLS = {"TLSv1.2", "TLSv1.3"};

    private static final String[] CIPHERS = {
            "TLS_AES_256_GCM_SHA384",
            "TLS_CHACHA20_POLY1305_SHA256",
            "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
            "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256",
            "TLS_RSA_WITH_AES_128_CBC_SHA",
    };

    private static Properties props(String... keyValues) {
        Properties p = new Properties();
        for (int i = 0; i < keyValues.length; i += 2) {
            p.setProperty(keyValues[i], keyValues[i + 1]);
        }
        return p;
    }

    @Test
    public void testProtocolsAsConfigured() {
        // e.g. #433: only TLSv1.2 configured, TLSv1.3 must not be enabled
        assertArrayEquals(new String[]{"TLSv1.2"},
                SSLConfigParser.parseSSLProtocols(props("protocol.1", "TLSv1.2"), PROTOCOLS));
        assertArrayEquals(new String[]{"TLSv1.2", "TLSv1.3"},
                SSLConfigParser.parseSSLProtocols(props("protocol.1", "TLSv1.2", "protocol.2", "TLSv1.3"), PROTOCOLS));
    }

    @Test
    public void testUnsupportedProtocolIsSkipped() {
        // A typo or a protocol the JVM doesn't support must not end up in setEnabledProtocols()
        assertArrayEquals(new String[]{"TLSv1.3"},
                SSLConfigParser.parseSSLProtocols(props("protocol.1", "TLSv1.0", "protocol.2", "TLSv1.3"), PROTOCOLS));
        assertNull(SSLConfigParser.parseSSLProtocols(props("protocol.1", "TLS1.3"), PROTOCOLS));
    }

    @Test
    public void testNoProtocolsMeansDefault() {
        assertNull(SSLConfigParser.parseSSLProtocols(props(), PROTOCOLS));
    }

    @Test
    public void testProtocolsStopAtFirstGap() {
        assertArrayEquals(new String[]{"TLSv1.2"},
                SSLConfigParser.parseSSLProtocols(props("protocol.1", "TLSv1.2", "protocol.3", "TLSv1.3"), PROTOCOLS));
    }

    @Test
    public void testCipherWhitelist() {
        // The whitelist shipped in slave.conf.dist and master.conf.dist
        assertArrayEquals(new String[]{
                        "TLS_AES_256_GCM_SHA384",
                        "TLS_CHACHA20_POLY1305_SHA256",
                        "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256"},
                SSLConfigParser.parseCipherSuites(
                        props("cipher.whitelist.1", ".*_(POLY1305_SHA256|GCM_SHA384|GCM_SHA256).*"), CIPHERS));
    }

    @Test
    public void testCipherBlacklistWinsOverWhitelist() {
        assertArrayEquals(new String[]{"TLS_AES_256_GCM_SHA384", "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256"},
                SSLConfigParser.parseCipherSuites(props(
                        "cipher.whitelist.1", ".*_GCM_.*",
                        "cipher.blacklist.1", ".*CHACHA20.*"), CIPHERS));
        // Blacklist only: removed from the full supported set
        assertArrayEquals(new String[]{
                        "TLS_AES_256_GCM_SHA384",
                        "TLS_CHACHA20_POLY1305_SHA256",
                        "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256"},
                SSLConfigParser.parseCipherSuites(props("cipher.blacklist.1", ".*_CBC_.*"), CIPHERS));
    }

    @Test
    public void testBadWhitelistFallsBackToAllSupported() {
        String[] result = SSLConfigParser.parseCipherSuites(props("cipher.whitelist.1", "NO_SUCH_CIPHER"), CIPHERS);
        assertNotNull(result);
        assertArrayEquals(CIPHERS, result);
    }

    @Test
    public void testNoCiphersLeftMeansDefault() {
        assertNull(SSLConfigParser.parseCipherSuites(props("cipher.blacklist.1", ".*"), CIPHERS));
    }

    @Test
    public void testParseAgainstJvmDoesNotThrow() {
        // Uses the real JVM lists; just make sure a typical configuration parses
        assertNotNull(SSLConfigParser.parseSSLProtocols(props("protocol.1", "TLSv1.2")));
        assertNotNull(SSLConfigParser.parseCipherSuites(props("cipher.whitelist.1", ".*_GCM_.*")));
    }
}
