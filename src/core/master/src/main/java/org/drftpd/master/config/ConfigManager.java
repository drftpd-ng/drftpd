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
package org.drftpd.master.config;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.drftpd.common.dynamicdata.Key;
import org.drftpd.common.dynamicdata.KeyedMap;
import org.drftpd.common.util.ConfigLoader;
import org.drftpd.common.util.PortRange;
import org.drftpd.common.util.SSLConfigParser;
import org.drftpd.common.util.Version;
import org.drftpd.master.GlobalContext;
import org.drftpd.master.permissions.ExtendedPermissions;
import org.drftpd.master.permissions.PathPermission;
import org.drftpd.master.permissions.Permission;
import org.drftpd.master.permissions.PermissionDefinition;
import org.drftpd.master.usermanager.User;
import org.drftpd.master.vfs.DirectoryHandle;
import org.drftpd.master.vfs.perms.VFSPermissions;
import org.reflections.Reflections;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles the loading of 'config/master.conf' and 'config/perms.conf'<br>
 * The directives that are going to be handled by this class are loaded during
 * the startup process and *MUST* be an extension of the master extension-point "ConfigHandler".<br>
 * No hard coding is needed to handle new directives, simply create a new extension.
 *
 * @author fr0w
 * @version $Id$
 */
public class ConfigManager implements ConfigInterface {
    private static final Logger logger = LogManager.getLogger(ConfigManager.class);
    private static final String permsFileName = "perms.conf";
    private static final String masterConfigFile = "master.conf";

    private static final Key<Map<String, ArrayList<PathPermission>>> PATHPERMS = new Key<>(ConfigManager.class, "pathPerms");
    private static final Key<Map<String, Permission>> PERMS = new Key<>(ConfigManager.class, "perms");

    /**
     * The state being built by {@link #reload()} on the current thread. The handlers call back into this
     * class while perms.conf is read, and those calls must land in the new state and not in the live one.
     */
    private static final ThreadLocal<State> BUILDING = new ThreadLocal<>();

    /**
     * Everything that is derived from perms.conf and the config handlers. {@link #reload()} builds a complete
     * new instance and then swaps it in at once, so a reader never sees a half loaded (or empty) configuration.
     */
    private static final class State {
        private HashMap<String, ConfigContainer> directivesMap = new HashMap<>();
        private final KeyedMap<Key<?>, Object> keyedMap = new KeyedMap<>();
        private VFSPermissions vfsPerms;
        private ArrayList<InetAddress> bouncerIps = new ArrayList<>();
        private String hideInStats = "";
        private String loginPrompt = GlobalContext.VERSION + " https://github.com/drftpd-ng/drftpd";
        private String allowConnectionsDenyReason = "";
        private String pasvAddr = null;
        private PortRange portRange = new PortRange(0);
        private boolean hideIps = true;
        private int maxUsersTotal = 1;
        private int maxUsersExempt = 0;

        private State() {
            keyedMap.setObject(PATHPERMS, new ConcurrentHashMap<>());
            keyedMap.setObject(PERMS, new ConcurrentHashMap<>());
        }
    }

    private volatile State _state;
    private volatile Properties _mainCfg;
    private volatile String[] _cipherSuites = null;
    private volatile String[] _sslProtocols = null;

    private State state() {
        State building = BUILDING.get();
        return building != null ? building : _state;
    }

    /**
     * Resolves, on every call, the file to read: 'config/perms.conf' when it exists, otherwise
     * 'config/perms.conf.dist'. Resolving it again on each reload picks up a perms.conf that was created or
     * removed after startup.
     */
    File resolvePermsFile() {
        return ConfigLoader.loadConfigFile(permsFileName, false);
    }

    Properties readMainProperties() {
        return ConfigLoader.loadConfig(masterConfigFile);
    }

    /**
     * Reload all VFSPermHandlers and ConfigHandlers.
     * Also re-read the config files, choosing again between perms.conf and perms.conf.dist.
     * The new configuration is built completely before it replaces the current one, if it can not be
     * read the current one stays active.
     */
    public synchronized void reload() {
        Properties mainCfg = readMainProperties();
        String[] cipherSuites = SSLConfigParser.parseCipherSuites(mainCfg);
        String[] sslProtocols = SSLConfigParser.parseSSLProtocols(mainCfg);

        State previous = _state;
        State fresh = new State();
        boolean readable;
        BUILDING.set(fresh);
        try {
            fresh.vfsPerms = new VFSPermissions();
            fresh.directivesMap = loadConfigHandlers(previous);
            readable = readConf(fresh, resolvePermsFile());
        } finally {
            BUILDING.remove();
        }

        _mainCfg = mainCfg;
        _cipherSuites = cipherSuites;
        _sslProtocols = sslProtocols;
        if (readable || previous == null) {
            _state = fresh;
        } else {
            logger.warn("Keeping the previous permissions configuration");
        }
    }

    /**
     * @return the VFSPermission object.
     * @see VFSPermissions
     */
    public VFSPermissions getVFSPermissions() {
        return state().vfsPerms;
    }

    /**
     * Returns the KeyedMap that allow dynamic and persistent storage of the loaded permissions.<br>
     * For a better understanding see how does the 'msgpath' directive works.
     */
    public KeyedMap<Key<?>, Object> getKeyedMap() {
        return state().keyedMap;
    }

    /**
     * Load all connected handlers.
     *
     * @param previous the state to take the handlers from if they can not be loaded, may be null
     */
    private HashMap<String, ConfigContainer> loadConfigHandlers(State previous) {
        HashMap<String, ConfigContainer> directivesMap = new HashMap<>();

        Set<Class<? extends ExtendedPermissions>> extendedPermissions = new Reflections("org.drftpd").getSubTypesOf(ExtendedPermissions.class);
        try {
            for (Class<? extends ExtendedPermissions> extendedPermission : extendedPermissions) {
                ExtendedPermissions perms = extendedPermission.getConstructor().newInstance();
                for (PermissionDefinition permission : perms.permissions()) {
                    Class<? extends ConfigHandler> handler = permission.getHandler();
                    String directive = permission.getDirective();
                    if (directivesMap.containsKey(directive)) {
                        logger.debug("A handler for {} already loaded, check your plugin.xml's", directive);
                        continue;
                    }
                    String method = permission.getMethod();
                    ConfigHandler handlerInstance = handler.getConstructor().newInstance();
                    Method methodInstance = handler.getMethod(method, String.class, StringTokenizer.class);
                    ConfigContainer cc = new ConfigContainer(handlerInstance, methodInstance);
                    directivesMap.put(directive, cc);
                    logger.debug("Added directive {} to our directives map", directive);
                }
            }
        } catch (Exception e) {
            logger.error("Failed to load plugins for master extension point 'ExtendedPermissions. The previous handlers are kept, or none if there were none", e);
            directivesMap = previous != null ? previous.directivesMap : new HashMap<>();
        }
        logger.debug("Loaded {} ExtendedPermissions classes", directivesMap.size());
        return directivesMap;
    }

    /**
     * @return a Properties object containing all data loaded from 'master.conf'.
     */
    public Properties getMainProperties() {
        return _mainCfg;
    }

    private Map<String, ArrayList<PathPermission>> getPathPermsMap() {
        return getKeyedMap().getObject(PATHPERMS, null);
    }

    private Map<String, Permission> getPermissionsMap() {
        return getKeyedMap().getObject(PERMS, null);
    }

    /**
     * Reads the given perms file handling what can be handled, ignoring what's does not have an available handler.
     *
     * @return false if the file could not be read
     */
    private boolean readConf(State s, File permsFile) {
        try (LineNumberReader in = new LineNumberReader(new FileReader(permsFile))) {
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
                    case "login_prompt" -> s.loginPrompt = Version.expandPlaceholder(line.substring("login_prompt".length()).trim());
                    case "max_users" -> {
                        s.maxUsersTotal = Integer.parseInt(st.nextToken());
                        s.maxUsersExempt = Integer.parseInt(st.nextToken());
                    }
                    case "pasv_addr" -> s.pasvAddr = st.nextToken();
                    case "pasv_ports" -> {
                        String[] temp = st.nextToken().split("-");
                        s.portRange = new PortRange(Integer.parseInt(temp[0]), Integer.parseInt(temp[1]), 0);
                    }
                    case "hide_ips" -> s.hideIps = st.nextToken().equalsIgnoreCase("true");
                    case "allow_connections" ->
                            getPermissionsMap().put("allow_connections", new Permission(Permission.makeUsers(st)));
                    case "allow_connections_deny_reason" ->
                            s.allowConnectionsDenyReason = line.substring("allow_connections_deny_reason".length()).trim();
                    case "exempt" -> getPermissionsMap().put("exempt", new Permission(Permission.makeUsers(st)));
                    case "bouncer_ips" -> {
                        ArrayList<InetAddress> ips = new ArrayList<>();
                        while (st.hasMoreTokens()) {
                            ips.add(InetAddress.getByName(st.nextToken()));
                        }
                        s.bouncerIps = ips;
                    }
                    case "hideinstats" -> {
                        while (st.hasMoreTokens())
                            s.hideInStats = s.hideInStats + st.nextToken() + " ";
                    }
                    default -> handleLine(drct, st);
                }
            }
            return true;
        } catch (IOException e) {
            logger.info("Unable to parse {}", permsFile.getName(), e);
            return false;
        }
    }

    private void handleLine(String directive, StringTokenizer st) {
        try {
            getVFSPermissions().handleLine(directive, st);
            return; // successfully handled by VFSPermissions, stop!
        } catch (UnsupportedOperationException e) {
            // could not be handled by VFSPermissions, let's try the other ones!
        }

        ConfigContainer cc = state().directivesMap.get(directive);

        if (cc == null) {
            logger.error("No handler found for '{}' ignoring line", directive);
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

    public boolean checkPathPermission(String directive, User user, DirectoryHandle path, boolean defaults) {
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
            logger.info("The directive '{}' is already on the permission map, check out your {}", directive, permsFileName);
            return;
        }

        getPermissionsMap().put(directive, permission);
    }

    public boolean checkPermission(String key, User user) {
        Permission perm = getPermissionsMap().get(key);
        return (perm != null) && perm.check(user);
    }

    public List<InetAddress> getBouncerIps() {
        return state().bouncerIps;
    }

    public boolean getHideIps() {
        return state().hideIps;
    }

    public String getLoginPrompt() {
        return state().loginPrompt;
    }

    public String getAllowConnectionsDenyReason() {
        return state().allowConnectionsDenyReason;
    }

    public int getMaxUsersExempt() {
        return state().maxUsersExempt;
    }

    public int getMaxUsersTotal() {
        return state().maxUsersTotal;
    }

    public String getPasvAddress() throws NullPointerException {
        String pasvAddr = state().pasvAddr;
        if (pasvAddr == null)
            throw new NullPointerException("pasv_addr not configured");
        return pasvAddr;
    }

    public PortRange getPortRange() {
        return state().portRange;
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

    public String getDefaultPreUser() {
        return _mainCfg.getProperty("default.pre.user", "drftpd");
    }

    public String getDefaultPreGroup() {
        return _mainCfg.getProperty("default.pre.group", "drftpd");
    }

    public String[] getCipherSuites() {
        return _cipherSuites;
    }

    public String[] getSSLProtocols() {
        return _sslProtocols;
    }

    public String getHideInStats() {
        return state().hideInStats;
    }
}
