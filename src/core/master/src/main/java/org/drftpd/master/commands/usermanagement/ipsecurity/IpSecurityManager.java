/*
 *  This file is part of DrFTPD, Distributed FTP Daemon.
 *
 *   DrFTPD is free software; you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as
 *   published by
 *   the Free Software Foundation; either version 2 of the
 *   License, or
 *   (at your option) any later version.
 *
 *   DrFTPD is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied
 *   warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *   See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General
 *   Public License
 *   along with DrFTPD; if not, write to the Free
 *   Software
 *   Foundation, Inc., 59 Temple Place, Suite 330,
 *   Boston, MA  02111-1307  USA
 */

package org.drftpd.master.commands.usermanagement.ipsecurity;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.bushe.swing.event.annotation.EventSubscriber;
import org.drftpd.common.extensibility.PluginInterface;
import org.drftpd.common.network.InetAddressUtils;
import org.drftpd.common.util.ConfigLoader;
import org.drftpd.master.GlobalContext;
import org.drftpd.master.event.ReloadEvent;
import org.drftpd.master.permissions.Permission;
import org.drftpd.master.usermanager.User;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.StringTokenizer;

/**
 * @author : CyBeR
 * @version : v1.0
 */

public class IpSecurityManager implements PluginInterface {
    private static final Logger logger = LogManager.getLogger(IpSecurityManager.class);

    private ArrayList<Integer> _ident;
    private ArrayList<String> _octets;
    private ArrayList<Integer> _numip;
    private ArrayList<Integer> _hostm;
    private ArrayList<String> _perms;

    // ipsecurity6 rules: ident, minimum fixed leading groups, number of IPs, perms
    private ArrayList<Integer> _ident6;
    private ArrayList<Integer> _groups6;
    private ArrayList<Integer> _numip6;
    private ArrayList<String> _perms6;

    /*
     * Get the ipSecurity Plugin
     */
    public static IpSecurityManager getIpSecurity() {
        for (PluginInterface plugin : GlobalContext.getGlobalContext().getPlugins()) {
            if (plugin instanceof IpSecurityManager) {
                return (IpSecurityManager) plugin;
            }
        }
        throw new RuntimeException("IpSecurity plugin is not loaded.");
    }

    @Override
    public void startPlugin() {
        AnnotationProcessor.process(this);
        loadConf();
    }

    @Override
    public void stopPlugin(String reason) {
        AnnotationProcessor.unprocess(this);
    }

    @EventSubscriber
    public void onReloadEvent(ReloadEvent event) {
        logger.info("Received reload event, reloading");
        loadConf();
    }

    /*
     * Loads configs by reading file line by line.
     * Must be done this way as we read the same "command" string multiple times
     */
    private void loadConf() {
        _ident = new ArrayList<>();
        _octets = new ArrayList<>();
        _numip = new ArrayList<>();
        _hostm = new ArrayList<>();
        _perms = new ArrayList<>();
        _ident6 = new ArrayList<>();
        _groups6 = new ArrayList<>();
        _numip6 = new ArrayList<>();
        _perms6 = new ArrayList<>();
        LineNumberReader inRead = null;

        try {
            File file = ConfigLoader.loadConfigFile("ipsecurity.conf", false);
            inRead = new LineNumberReader(new FileReader(file));

            String line;
            while ((line = inRead.readLine()) != null) {
                StringTokenizer st = new StringTokenizer(line);

                if (!st.hasMoreTokens()) {
                    continue;
                }
                String cmd = st.nextToken();

                if (cmd.equals("ipsecurity")) {
                    try {
                        int ident = Integer.parseInt(st.nextToken().trim());
                        String octets = st.nextToken().trim();
                        int hostm = Integer.parseInt(st.nextToken().trim());
                        int numip = Integer.parseInt(st.nextToken().trim());
                        String perms = st.nextToken("").trim();

                        _ident.add(ident);
                        _octets.add(octets);
                        _hostm.add(hostm);
                        _numip.add(numip);
                        _perms.add(perms);
                    } catch (NumberFormatException e) {
                        logger.debug("Error while parsing ipsecurity line {}", inRead.getLineNumber());
                    }
                } else if (cmd.equals("ipsecurity6")) {
                    try {
                        int ident = Integer.parseInt(st.nextToken().trim());
                        int groups = Integer.parseInt(st.nextToken().trim());
                        int numip = Integer.parseInt(st.nextToken().trim());
                        String perms = st.nextToken("").trim();
                        if (groups < 1 || groups > 8) {
                            logger.warn("ipsecurity6 on line {}: groups must be 1 to 8, got {}", inRead.getLineNumber(), groups);
                            continue;
                        }

                        _ident6.add(ident);
                        _groups6.add(groups);
                        _numip6.add(numip);
                        _perms6.add(perms);
                    } catch (NumberFormatException e) {
                        logger.debug("Error while parsing ipsecurity6 line {}", inRead.getLineNumber());
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Exception when reading ipsecurity conf", e);
        } finally {
            try {
                if (inRead != null) {
                    inRead.close();
                }
            } catch (IOException ex) {
                //couldn't close file - ignore
            }
        }
    }

    /*
     * Checks if host has an Ident line and returns if permitted
     */
    private boolean checkIdent(String ident, int currentident) {
        return (currentident != 1) || (!ident.equals("*"));
    }

    /*
     * This checks the octects to make sure they are correct and not a insecure set
     * Also checks hostmask if using that, and compares it to the setup in conf file
     */
    private boolean checkOctets(String octets, String currentoctet, int isHost) {
        String[] split_octets = octets.split("\\.");
        String[] split_currentoctets = currentoctet.split("\\.");

        if (isHost == 1) {
            if (split_currentoctets.length > 0) {

                /*
                 * Makes sure the length of the dns name does not exceed
                 * the size of the conf
                 */
                if (split_octets.length > split_currentoctets.length) {
                    logger.debug("Invalid Input Host Mask: {}", octets);
                    return false;
                }

                /*
                 * This makes sure something is besides a * in the hostmask.
                 */
                for (int i = 0; i < split_currentoctets.length; i++) {
                    if (i < split_octets.length) {
                        if ((!split_currentoctets[i].equals("*")) &&
                                split_octets[i].equals("*")) {
                            return false;
                        }
                    } else {
                        if (!split_currentoctets[i].equals("*")) {
                            return false;
                        }
                    }
                }
                return true;
            }
        } else {
            if (split_currentoctets.length == 4) {
                /*
                 * This checks to make sure if the ip is smaller than 4 octets
                 * that the last octets are * and allowed
                 */
                for (int i = 4 - split_octets.length; i > 0; --i) {
                    if (!split_currentoctets[4 - i].equals("*")) {
                        return false;
                    }
                }

                /*
                 * Make sure the input of the hostmask is IPv4
                 * Will have to make a set for IPv6
                 */
                if (split_octets.length > split_currentoctets.length) {
                    logger.debug("Invalid Input Host Mask: {}", octets);
                    return false;
                }

                /*
                 * Checks to make sure that the octets are correct if
                 * it is not a *
                 */
                for (int i = 0; i < 4; i++) {
                    if (i < split_octets.length) {
                        if (!split_currentoctets[i].equals("*")) {
                            if (!split_octets[i].matches("^(\\d{1,3})$")) {
                                return false;
                            }
                        }
                    } else {
                        if (!split_currentoctets[i].equals("*")) {
                            return false;
                        }
                    }
                }
                return true;
            }
            logger.debug("Invalid Octet in conf file: {}", currentoctet);
        }
        return false;
    }

    /*
     * Makes sure user is not over max number of ips
     */
    private boolean checkNumIPS(int numips, int currentips) {
        if (currentips == 0) {
            return true;
        }
        return numips < currentips;
    }

    /*
     * Checks perms if this ip applies to user
     */
    private boolean checkPermission(User user, String perm) {
        if (user == null) {
            return perm.equals("*");
        }

        Permission perms = new Permission(perm);
        return perms.check(user);
    }

    /**
     * How many leading 16-bit groups an IPv6 host mask fixes: 8 for a full address
     * ("2001:db8::1"), n for a prefix of n explicit groups followed by "*" ("2001:db8:1:2:*").
     * Returns -1 for anything else (e.g. other wildcards, or "::" before the "*", which makes the
     * number of groups ambiguous).
     */
    static int getIPv6FixedGroups(String mask) {
        if (mask.indexOf('*') == -1 && mask.indexOf('?') == -1 && mask.indexOf('[') == -1) {
            try {
                InetAddress address = InetAddressUtils.parseLiteral(mask);
                return address instanceof Inet6Address ? 8 : -1;
            } catch (IllegalArgumentException e) {
                return -1;
            }
        }
        if (!mask.endsWith(":*") || mask.contains("::")) {
            return -1;
        }
        String[] groups = mask.substring(0, mask.length() - 2).split(":", -1);
        if (groups.length > 7) {
            return -1;
        }
        for (String group : groups) {
            if (!group.matches("[0-9A-Fa-f]{1,4}")) {
                return -1;
            }
        }
        return groups.length;
    }

    private boolean checkIPv6(String ident, String mask, int numip, User user) {
        int fixedGroups = getIPv6FixedGroups(mask);
        if (fixedGroups == -1) {
            logger.debug("Invalid IPv6 host mask: {}", mask);
            return false;
        }
        for (int i = 0; i < _ident6.size(); i++) {
            if (checkPermission(user, _perms6.get(i)) && fixedGroups >= _groups6.get(i)
                    && checkNumIPS(numip, _numip6.get(i)) && checkIdent(ident, _ident6.get(i))) {
                return true;
            }
        }
        return false;
    }

    /*
     * Main method to check both Octets/Hostname and number of IPs
     */
    public boolean checkIP(String ident, String octets, int numip, User user) {
        if (octets.contains(":")) {
            // IPv6 masks are checked against the ipsecurity6 rules only
            return checkIPv6(ident, octets, numip, user);
        }
        for (int i = 0; i < _ident.size(); i++) {
            if (checkPermission(user, _perms.get(i))) {
                if ((checkOctets(octets, _octets.get(i), _hostm.get(i))) &&
                        (checkNumIPS(numip, _numip.get(i))) &&
                        (checkIdent(ident, _ident.get(i)))) {
                    return true;
                }
            }
        }
        return false;
    }

    /*
     * Returns a string representative on how to add IPs and their restrictions
     */
    public String outputConfs(User user) {
        StringBuilder returnstring = new StringBuilder("Unable to add IP, must be of the following form(s):");
        int numip = 0;
        for (int i = 0; i < _ident.size(); i++) {
            if (checkPermission(user, _perms.get(i))) {
                if (_ident.get(i) == 1) {
                    returnstring.append("\nident@");
                } else {
                    returnstring.append("\n*@");
                }

                returnstring.append(_octets.get(i));

                if (_hostm.get(i) == 1) {
                    returnstring.append(" - (Replace X's with ispprovider.com)");
                }

                if (!(_numip.get(i) == 0)) {
                    try {
                        numip = _numip.get(i);
                    } catch (NumberFormatException e) {
                        // cannot parse integer....ignore
                    }
                }
            }
        }

        for (int i = 0; i < _ident6.size(); i++) {
            if (checkPermission(user, _perms6.get(i))) {
                returnstring.append(_ident6.get(i) == 1 ? "\nident@" : "\n*@");
                int groups = _groups6.get(i);
                if (groups >= 8) {
                    returnstring.append("XXXX:XXXX:XXXX:XXXX:XXXX:XXXX:XXXX:XXXX (a full IPv6 address)");
                } else {
                    returnstring.append("XXXX:".repeat(groups)).append("* (at least ").append(groups)
                            .append(" groups of an IPv6 address)");
                }
                if (_numip6.get(i) != 0) {
                    numip = _numip6.get(i);
                }
            }
        }

        if (numip > 0) {
            returnstring.append("\nAccount restricted to " + numip + " IP(s)");
        }

        return returnstring.toString();

    }

}
