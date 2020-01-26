/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * DrFTPD is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with DrFTPD; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package org.drftpd.config;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.drftpd.GlobalContext;
import org.drftpd.dynamicdata.Key;
import org.drftpd.dynamicdata.KeyedMap;
import org.drftpd.master.config.ConfigInterface;
import org.drftpd.permissions.PathPermission;
import org.drftpd.permissions.Permission;
import org.drftpd.usermanager.User;
import org.drftpd.util.CommonPluginUtils;
import org.drftpd.util.PluginObjectContainer;
import org.drftpd.util.PortRange;
import org.drftpd.vfs.DirectoryHandle;
import org.drftpd.vfs.perms.VFSPermissions;

import javax.net.ssl.SSLContext;
import java.io.*;
import java.net.InetAddress;
import java.util.*;

/**
 * Handles the loading of 'master.conf' and 'conf/perms.conf'<br>
 * The directives that are going to be handled by this class are loaded during
 * the startup process and *MUST* be an extension of the master extension-point "ConfigHandler".<br>
 * No hard coding is needed to handle new directives, simply create a new extension.
 * @author fr0w
 * @version $Id$
 */
public class ConfigManager implements ConfigInterface {
    private static final Logger logger = LogManager.getLogger(ConfigManager.class);
    private static final File permsFile = new File("conf/perms.conf");
    private static final File mainFile = new File("conf/master.conf");

    private static final Key<Hashtable<String, ArrayList<PathPermission>>> PATHPERMS
            = new Key<>(ConfigManager.class, "pathPerms");
    private static final Key<Hashtable<String, Permission>> PERMS
            = new Key<>(ConfigManager.class, "perms");

    private String hideInStats="";

    private HashMap<String, ConfigContainer> _directivesMap;
    private KeyedMap<Key<?>, Object> _keyedMap;
    private Properties _mainCfg;

    private VFSPermissions _vfsPerms;

    private ArrayList<InetAddress> _bouncerIps;
    private String _loginPrompt = GlobalContext.VERSION + " https://github.com/drftpd-ng/drftpd3";
    private String _allowConnectionsDenyReason = "";
    private String _pasvAddr = null;
    private PortRange _portRange = new PortRange(0);
    private boolean _hideIps = true;

    private int _maxUsersTotal = Integer.MAX_VALUE;
    private int _maxUsersExempt = 0;

    private String[] _cipherSuites = null;
    private String[] _sslProtocols = null;

    /**
     * Reload all VFSPermHandlers and ConfigHandlers.
     * Also re-read the config files.
     */
    public void reload() {
        loadVFSPermissions();
        loadConfigHandlers();
        loadMainProperties();
        parseCipherSuites();
        parseSSLProtocols();

        initializeKeyedMap();

        _bouncerIps = new ArrayList<>();

        readConf();
    }

    private void loadVFSPermissions() {
        _vfsPerms = new VFSPermissions();
    }

    /**
     * @return the VFSPermission object.
     * @see VFSPermissions
     */
    public VFSPermissions getVFSPermissions() {
        return _vfsPerms;
    }

    /**
     * Returns the KeyedMap that allow dynamic and persistent storage of the loaded permissions.<br>
     * For a better understanding see how does the 'msgpath' directive works.
     * @see DefaultConfigHandler
     * @see DefaultConfigPostHook
     */
    public KeyedMap<Key<?>, Object> getKeyedMap() {
        return _keyedMap;
    }

    /**
     * Load all connected handlers.
     */
    private void loadConfigHandlers() {
        _directivesMap = new HashMap<>();

        try {
            List<PluginObjectContainer<ConfigHandler>> loadedDirectives =
                    CommonPluginUtils.getPluginObjectsInContainer(this, "master", "ConfigHandler", "Class", "Method",
                            new Class[] { String.class, StringTokenizer.class });
            for (PluginObjectContainer<ConfigHandler> container : loadedDirectives) {
                String directive = container.getPluginExtension().getParameter("Directive").valueAsString();
                if (_directivesMap.containsKey(directive)) {
                    logger.debug("A handler for {} already loaded, check your plugin.xml's", directive);
                    continue;
                }
                ConfigContainer cc = new ConfigContainer(container.getPluginObject(), container.getPluginMethod());
                _directivesMap.put(directive, cc);
            }
        } catch (IllegalArgumentException e) {
            logger.error("Failed to load plugins for master extension point 'Directive', possibly the master"
                    +" extension point definition has changed in the plugin.xml",e);
        }
    }

    /**
     * Reads 'master.conf' and save it in a Properties object.
     * @see #getMainProperties()
     */
    private void loadMainProperties() {
        FileInputStream is = null;
        try {
            _mainCfg = new Properties();
            is = new FileInputStream(mainFile);
            _mainCfg.load(is);
        } catch (IOException e) {
            logger.error("Unable to read {}", mainFile.getPath(), e);
        } finally {
            if (is != null) {
                try { is.close(); }
                catch (IOException e) { }
            }
        }
    }

    private void parseCipherSuites() {
        List<String> cipherSuites = new ArrayList<>();
        List<String> supportedCipherSuites = new ArrayList<>();
        try {
			supportedCipherSuites.addAll(Arrays.asList(SSLContext.getDefault().getSupportedSSLParameters().getCipherSuites()));
        } catch (Exception e) {
            logger.error("Unable to get supported cipher suites, using default.", e);
        }
        // Parse cipher suite whitelist rules
        boolean whitelist = false;
        for (int x = 1;; x++) {
            String whitelistPattern = _mainCfg.getProperty("cipher.whitelist." + x);
            if (whitelistPattern == null) {
                break;
            } else if (whitelistPattern.trim().length() == 0) {
                continue;
            }
            if (!whitelist) whitelist = true;
            for (String cipherSuite : supportedCipherSuites) {
                if (cipherSuite.matches(whitelistPattern)) {
                    cipherSuites.add(cipherSuite);
                }
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
        for (int x = 1;; x++) {
            String blacklistPattern = _mainCfg.getProperty("cipher.blacklist." + x);
            if (blacklistPattern == null) {
                break;
            } else if (blacklistPattern.trim().isEmpty()) {
                continue;
            }
            cipherSuites.removeIf(cipherSuite -> cipherSuite.matches(blacklistPattern));
        }
        if (cipherSuites.isEmpty()) {
            _cipherSuites = null;
        } else {
            _cipherSuites = cipherSuites.toArray(new String[cipherSuites.size()]);
        }
    }

    private void parseSSLProtocols() {
        List<String> sslProtocols = new ArrayList<>();
        List<String> supportedSSLProtocols;
        try {
            supportedSSLProtocols = Arrays.asList(SSLContext.getDefault().getSupportedSSLParameters().getProtocols());
            for (int x = 1;; x++) {
                String sslProtocol = _mainCfg.getProperty("protocol." + x);
                if (sslProtocol == null) {
                    break;
                } else if (supportedSSLProtocols.contains(sslProtocol)) {
                    sslProtocols.add(sslProtocol);
                }
            }
        } catch (Exception e) {
            logger.error("Unable to get supported SSL protocols, using default.", e);
        }
        if (sslProtocols.size() == 0) {
            _sslProtocols = null;
        } else {
            _sslProtocols = sslProtocols.toArray(new String[sslProtocols.size()]);
        }
    }

    /**
     * Initializes the KeyedMap.
     * @see #getKeyedMap()
     */
    private void initializeKeyedMap() {
        _keyedMap = new KeyedMap<>();

        _keyedMap.setObject(PATHPERMS, new Hashtable<>());
        _keyedMap.setObject(PERMS, new Hashtable<>());
    }

    private Hashtable<String, ArrayList<PathPermission>> getPathPermsMap() {
        return _keyedMap.getObject(PATHPERMS, null);
    }

    private Hashtable<String, Permission> getPermissionsMap() {
        return _keyedMap.getObject(PERMS, null);
    }

    /**
     * @return a Properties object containing all data loaded from 'master.conf'.
     */
    public Properties getMainProperties() {
        return _mainCfg;
    }

    /**
     * Reads 'conf/perms.conf' handling what can be handled, ignoring what's does not have an available handler.
     */
    private void readConf() {
        LineNumberReader in = null;
        try {
            in = new LineNumberReader(new FileReader(permsFile));
            String line;

            while ((line = in.readLine()) != null) {
                StringTokenizer st = new StringTokenizer(line);

                if (line.startsWith("#") || !st.hasMoreTokens()) {
                    continue;
                }

                String drct = st.nextToken().toLowerCase();

				/*
				 * Built-in directives.
				 */

                switch (drct) {
                    case "login_prompt":
                        _loginPrompt = line.substring("login_prompt".length()).trim();
                        break;
                    case "max_users":
                        _maxUsersTotal = Integer.parseInt(st.nextToken());
                        _maxUsersExempt = Integer.parseInt(st.nextToken());
                        break;
                    case "pasv_addr":
                        _pasvAddr = st.nextToken();
                        break;
                    case "pasv_ports":
                        String[] temp = st.nextToken().split("-");
                        _portRange = new PortRange(Integer.parseInt(temp[0]), Integer.parseInt(temp[1]), 0);
                        break;
                    case "hide_ips":
                        _hideIps = st.nextToken().equalsIgnoreCase("true");
                        break;
                    case "allow_connections":
                        getPermissionsMap().put("allow_connections", new Permission(Permission.makeUsers(st)));
                        break;
                    case "allow_connections_deny_reason":
                        _allowConnectionsDenyReason = line.substring("allow_connections_deny_reason".length()).trim();

                        break;
                    case "exempt":
                        getPermissionsMap().put("exempt", new Permission(Permission.makeUsers(st)));
                        break;
                    case "bouncer_ips":
                        ArrayList<InetAddress> ips = new ArrayList<>();
                        while (st.hasMoreTokens()) {
                            ips.add(InetAddress.getByName(st.nextToken()));
                        }
                        _bouncerIps = ips;
                        break;
                    case "hideinstats":
                        while (st.hasMoreTokens())
                            hideInStats = hideInStats + st.nextToken() + " ";
                        break;
                    default:
                        handleLine(drct, st);
                        break;
                }
            }
        } catch (IOException e) {
            logger.info("Unable to parse {}", permsFile.getName(), e);
        } finally {
            if (in != null) {
                try { in.close(); }
                catch (IOException e) { }
            }
        }
    }

    private void handleLine(String directive, StringTokenizer st) {
        try {
            getVFSPermissions().handleLine(directive, st);
            return; // successfully handled by VFSPermissions, stop!
        } catch (UnsupportedOperationException e) {
            // could not be handled by VFSPermissions, let's try the other ones!
        }

        ConfigContainer cc = _directivesMap.get(directive);

        if (cc == null) {
            logger.debug("No handler found for '{}' ignoring line", directive);
            return;
        }

        try {
            cc.getMethod().invoke(cc.getInstance(), directive, st);
        } catch (Exception e) {
            logger.debug("Error while handling directive: {}", directive, e);
        }
    }

    public void addPathPermission(String directive, PathPermission perm) {
        ArrayList<PathPermission> list;
        if (!getPathPermsMap().containsKey(directive)) {
            list = new ArrayList<>();
            getPathPermsMap().put(directive, list);
        } else {
            list = getPathPermsMap().get(directive);
        }

        list.add(perm);
    }


    public boolean checkPathPermission(String directive, User user, DirectoryHandle path) {
        return checkPathPermission(directive, user, path, false);
    }


    public boolean checkPathPermission(String directive, User user,
                                       DirectoryHandle path, boolean defaults) {
        ArrayList<PathPermission> perms = getPathPermsMap().get(directive);

        if (perms != null && !perms.isEmpty()) {
            for (PathPermission perm : perms) {
                if (perm.checkPath(path)) {
                    return perm.check(user);
                }
            }
        }

        return defaults;
    }

    public void addPermission(String directive, Permission permission) {
        boolean alreadyExists = getPermissionsMap().containsKey(directive);

        if (alreadyExists) {
            // TODO what's best replace the existing one or keep it?
            logger.info("The directive '{}' is already on the permission map, check out your {}", directive, permsFile.getName());
            return;
        }

        getPermissionsMap().put(directive, permission);
    }

    public boolean checkPermission(String key, User user) {
        Permission perm = getPermissionsMap().get(key);
        return (perm != null) && perm.check(user);
    }

    public List<InetAddress> getBouncerIps() {
        return _bouncerIps;
    }

    public boolean getHideIps() {
        return _hideIps;
    }

    public String getLoginPrompt() {
        return _loginPrompt;
    }

    public String getAllowConnectionsDenyReason() {
        return _allowConnectionsDenyReason;
    }

    public int getMaxUsersExempt() {
        return _maxUsersExempt;
    }

    public int getMaxUsersTotal() {
        return _maxUsersTotal;
    }

    public String getPasvAddress() throws NullPointerException {
        if (_pasvAddr == null)
            throw new NullPointerException("pasv_addr not configured");
        return _pasvAddr;
    }

    public PortRange getPortRange() {
        return _portRange;
    }

    public boolean isLoginAllowed(User user) {
        Permission perm = getPermissionsMap().get("allow_connections");

        if (perm == null) {
            return true;
        }
        return perm.check(user);
    }

    public boolean isLoginExempt(User user) {
        Permission perm = getPermissionsMap().get("exempt");

        if (perm == null) {
            return true;
        }
        return perm.check(user);
    }

    public String[] getCipherSuites() {
        return _cipherSuites;
    }

    public String[] getSSLProtocols() {
        return _sslProtocols;
    }

    public String getHideInStats() {
        return hideInStats;
    }
}
