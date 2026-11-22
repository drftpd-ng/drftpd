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

import javax.net.ssl.SSLContext;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

/**
 * Parses the TLS settings shared by master.conf and slave.conf:
 * <ul>
 *   <li><tt>protocol.N</tt>: enabled TLS protocols</li>
 *   <li><tt>cipher.whitelist.N</tt> / <tt>cipher.blacklist.N</tt>: regular expressions selecting the
 *       enabled cipher suites (the blacklist wins over the whitelist)</li>
 * </ul>
 * Both methods return <tt>null</tt> when nothing usable is configured, meaning "use the JVM's
 * defaults", which is what the socket code expects.
 */
public class SSLConfigParser {
    private static final Logger logger = LogManager.getLogger(SSLConfigParser.class);

    private SSLConfigParser() {
    }

    /**
     * Parses <tt>protocol.N</tt> against the protocols the JVM supports.
     */
    public static String[] parseSSLProtocols(Properties cfg) {
        try {
            return parseSSLProtocols(cfg, SSLContext.getDefault().getSupportedSSLParameters().getProtocols());
        } catch (Exception e) {
            logger.error("Unable to get supported SSL protocols, using default.", e);
            return null;
        }
    }

    /**
     * Parses <tt>protocol.N</tt> (N = 1, 2, ... until the first missing entry). Protocols that are
     * not in <tt>supported</tt> are skipped with a warning, so a typo or a protocol disabled in the
     * JVM does not make the socket setup fail.
     *
     * @return the configured protocols that are supported, or <tt>null</tt> if there are none
     */
    public static String[] parseSSLProtocols(Properties cfg, String[] supported) {
        List<String> supportedSSLProtocols = Arrays.asList(supported);
        List<String> sslProtocols = new ArrayList<>();
        for (int x = 1; ; x++) {
            String sslProtocol = cfg.getProperty("protocol." + x);
            if (sslProtocol == null) {
                break;
            }
            sslProtocol = sslProtocol.trim();
            logger.debug("Found protocol.{} as {}", x, sslProtocol);
            if (!supportedSSLProtocols.contains(sslProtocol)) {
                logger.warn("Found unsupported protocol configuration: protocol.{} -> {}", x, sslProtocol);
            } else {
                sslProtocols.add(sslProtocol);
            }
        }
        if (sslProtocols.isEmpty()) {
            return null;
        }
        return sslProtocols.toArray(new String[0]);
    }

    /**
     * Parses <tt>cipher.whitelist.N</tt> and <tt>cipher.blacklist.N</tt> against the cipher suites
     * the JVM supports.
     */
    public static String[] parseCipherSuites(Properties cfg) {
        String[] supported;
        try {
            supported = SSLContext.getDefault().getSupportedSSLParameters().getCipherSuites();
        } catch (Exception e) {
            logger.error("Unable to get supported cipher suites, using default.", e);
            supported = new String[0];
        }
        return parseCipherSuites(cfg, supported);
    }

    /**
     * Selects the cipher suites from <tt>supported</tt>: all suites matching a whitelist pattern (or
     * all supported suites if there is no usable whitelist pattern), minus those matching a
     * blacklist pattern.
     *
     * @return the selected cipher suites, or <tt>null</tt> if none are left
     */
    public static String[] parseCipherSuites(Properties cfg, String[] supported) {
        List<String> supportedCipherSuites = Arrays.asList(supported);
        List<String> cipherSuites = new ArrayList<>();
        // Parse cipher suite whitelist rules
        boolean whitelist = false;
        for (int x = 1; ; x++) {
            String whitelistPattern = cfg.getProperty("cipher.whitelist." + x);
            // No cipher.whitelist.(x) entry means we are done
            if (whitelistPattern == null) {
                break;
            }
            logger.debug("Found cipher.whitelist.{} as {}", x, whitelistPattern);
            if (whitelistPattern.trim().isEmpty()) {
                continue;
            }
            whitelist = true;
            boolean found = false;
            for (String cipherSuite : supportedCipherSuites) {
                if (cipherSuite.matches(whitelistPattern)) {
                    logger.debug("Adding {} as it matches whitelist pattern {}", cipherSuite, whitelistPattern);
                    cipherSuites.add(cipherSuite);
                    found = true;
                }
            }
            if (!found) {
                logger.warn("Did not find a match for whitelist {} in supported cipher suites", whitelistPattern);
            }
        }
        if (cipherSuites.isEmpty()) {
            // No whitelist rule or whitelist pattern bad, add default set
            cipherSuites.addAll(supportedCipherSuites);
            if (whitelist) {
                // There are at least one whitelist pattern specified
                logger.warn("Bad whitelist pattern, no matching ciphers found. " +
                        "Adding default cipher set before continuing with blacklist check");
            }
        }
        // Parse cipher suite blacklist rules and remove matching ciphers from set
        for (int x = 1; ; x++) {
            String blacklistPattern = cfg.getProperty("cipher.blacklist." + x);
            // No cipher.blacklist.(x) entry means we are done
            if (blacklistPattern == null) {
                break;
            }
            logger.debug("Found cipher.blacklist.{} as {}", x, blacklistPattern);
            if (blacklistPattern.trim().isEmpty()) {
                continue;
            }
            cipherSuites.removeIf(cipherSuite -> cipherSuite.matches(blacklistPattern));
        }
        if (cipherSuites.isEmpty()) {
            return null;
        }
        return cipherSuites.toArray(new String[0]);
    }
}
