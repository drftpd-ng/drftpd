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

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.regex.Pattern;

/**
 * Helpers for handling IPv4 and IPv6 addresses the same way across master and slave.
 */
public class InetAddressUtils {

    /** RFC 2428 network protocol number for IPv4. */
    public static final int AF_INET = 1;

    /** RFC 2428 network protocol number for IPv6. */
    public static final int AF_INET6 = 2;

    private static final Pattern IPV4_LITERAL = Pattern.compile("\\d{1,3}(\\.\\d{1,3}){3}");

    private static final Pattern IPV6_LITERAL = Pattern.compile("[0-9A-Fa-f:.]+");

    private InetAddressUtils() {
    }

    /**
     * @return {@link #AF_INET6} for IPv6 addresses, {@link #AF_INET} otherwise
     */
    public static int getAddressFamily(InetAddress address) {
        return address instanceof Inet6Address ? AF_INET6 : AF_INET;
    }

    /**
     * The address as users and admins expect to see and write it: IPv4 in dotted decimal, IPv6 in
     * the RFC 5952 short form ("2001:db8::1", "::1") without a zone/scope id. Java's
     * {@link InetAddress#getHostAddress()} instead gives IPv6 in full ("0:0:0:0:0:0:0:1"), plus
     * "%scope" for link-local addresses.
     */
    public static String toShortString(InetAddress address) {
        if (!(address instanceof Inet6Address)) {
            return address.getHostAddress();
        }
        byte[] bytes = address.getAddress();
        int[] groups = new int[8];
        for (int i = 0; i < 8; i++) {
            groups[i] = ((bytes[2 * i] & 0xff) << 8) | (bytes[2 * i + 1] & 0xff);
        }
        // Find the longest run of two or more zero groups (the first one if there is a tie)
        int bestStart = -1;
        int bestLength = 0;
        for (int i = 0; i < 8; ) {
            if (groups[i] != 0) {
                i++;
                continue;
            }
            int start = i;
            while (i < 8 && groups[i] == 0) {
                i++;
            }
            if (i - start > bestLength) {
                bestStart = start;
                bestLength = i - start;
            }
        }
        if (bestLength < 2) {
            bestStart = -1;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            if (i == bestStart) {
                sb.append("::");
                i += bestLength - 1;
                continue;
            }
            if (sb.length() > 0 && sb.charAt(sb.length() - 1) != ':') {
                sb.append(':');
            }
            sb.append(Integer.toHexString(groups[i]));
        }
        return sb.toString();
    }

    /**
     * Whether the address is only reachable inside a local network: IPv4 private ranges
     * (10/8, 172.16/12, 192.168/16) and, for IPv6, unique local (fc00::/7), link-local (fe80::/10)
     * and the deprecated site-local (fec0::/10) addresses.
     */
    public static boolean isPrivateAddress(InetAddress address) {
        if (address.isSiteLocalAddress() || address.isLinkLocalAddress()) {
            return true;
        }
        if (address instanceof Inet6Address) {
            return (address.getAddress()[0] & 0xfe) == 0xfc; // fc00::/7
        }
        return false;
    }

    /**
     * Parses an IP address literal, never doing a DNS lookup.
     *
     * @param literal an IPv4 address in dotted decimal or an IPv6 address
     * @throws IllegalArgumentException if it isn't a valid IP address literal
     */
    public static InetAddress parseLiteral(String literal) {
        if (literal == null || literal.isEmpty()) {
            throw new IllegalArgumentException("No address given");
        }
        if (IPV4_LITERAL.matcher(literal).matches()) {
            for (String octet : literal.split("\\.")) {
                if (Integer.parseInt(octet) > 255) {
                    throw new IllegalArgumentException("Invalid IPv4 address: " + literal);
                }
            }
        } else if (!literal.contains(":") || !IPV6_LITERAL.matcher(literal).matches()) {
            // Only allow characters valid in IP literals so getByName() can't do a DNS lookup
            throw new IllegalArgumentException("Not an IP address: " + literal);
        }
        try {
            return InetAddress.getByName(literal);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Invalid IP address: " + literal, e);
        }
    }

    /**
     * Splits "host:port", "ipv6-address:port" or "[ipv6-address]:port" at the last colon.
     *
     * @return {host, port}
     * @throws IllegalArgumentException if there is no port
     */
    public static String[] splitHostPort(String hostPort) {
        int colon = hostPort.lastIndexOf(':');
        if (colon <= 0 || colon == hostPort.length() - 1) {
            throw new IllegalArgumentException("Expected host:port, got " + hostPort);
        }
        String host = hostPort.substring(0, colon);
        if (host.startsWith("[") && host.endsWith("]")) {
            host = host.substring(1, host.length() - 1);
        }
        return new String[]{host, hostPort.substring(colon + 1)};
    }

    /**
     * Thrown by {@link #parseEprt(String)} when the address family isn't supported, so the caller
     * can answer with 522 as RFC 2428 requires.
     */
    public static class UnsupportedNetworkProtocolException extends IllegalArgumentException {
        public UnsupportedNetworkProtocolException(String message) {
            super(message);
        }
    }

    /**
     * Parses the argument of an RFC 2428 EPRT command: {@code <d><net-prt><d><net-addr><d><tcp-port><d>},
     * for example {@code |1|132.235.1.2|6275|} or {@code |2|1080::8:800:200C:417A|5282|}.
     * The delimiter is the first character and can be any printable ASCII character.
     *
     * @throws UnsupportedNetworkProtocolException if the network protocol is not 1 (IPv4) or 2 (IPv6)
     * @throws IllegalArgumentException            if the argument is malformed
     */
    public static InetSocketAddress parseEprt(String argument) {
        if (argument == null || argument.length() < 7) {
            throw new IllegalArgumentException("EPRT argument too short");
        }
        char delimiter = argument.charAt(0);
        if (delimiter < 33 || delimiter > 126 || argument.charAt(argument.length() - 1) != delimiter) {
            throw new IllegalArgumentException("Invalid EPRT delimiter");
        }
        String[] fields = argument.substring(1, argument.length() - 1).split(Pattern.quote(String.valueOf(delimiter)), -1);
        if (fields.length != 3) {
            throw new IllegalArgumentException("EPRT needs a protocol, an address and a port");
        }

        int protocol;
        try {
            protocol = Integer.parseInt(fields[0]);
        } catch (NumberFormatException e) {
            throw new UnsupportedNetworkProtocolException("Unknown network protocol: " + fields[0]);
        }
        if (protocol != AF_INET && protocol != AF_INET6) {
            throw new UnsupportedNetworkProtocolException("Unsupported network protocol: " + protocol);
        }

        InetAddress address = parseLiteral(fields[1]);
        // Java turns IPv4-mapped IPv6 addresses (::ffff:a.b.c.d) into IPv4 addresses, accept those
        if (protocol == AF_INET && !(address instanceof Inet4Address)) {
            throw new IllegalArgumentException("Protocol 1 needs an IPv4 address: " + fields[1]);
        }
        if (protocol == AF_INET6 && !(address instanceof Inet6Address) && !fields[1].contains(":")) {
            throw new IllegalArgumentException("Protocol 2 needs an IPv6 address: " + fields[1]);
        }

        int port;
        try {
            port = Integer.parseInt(fields[2]);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid port: " + fields[2]);
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Invalid port: " + port);
        }
        return new InetSocketAddress(address, port);
    }

    /**
     * The RFC 2428 reply text for EPSV: the port only, the client connects to the address of the
     * control connection.
     */
    public static String formatEpsvReply(int port) {
        return "Entering Extended Passive Mode (|||" + port + "|)";
    }
}
