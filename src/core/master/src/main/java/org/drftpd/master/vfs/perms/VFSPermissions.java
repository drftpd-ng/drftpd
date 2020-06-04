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
package org.drftpd.master.vfs.perms;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.drftpd.master.permissions.GlobPathPermission;
import org.drftpd.master.permissions.PathPermission;
import org.drftpd.master.usermanager.User;
import org.drftpd.master.vfs.InodeHandle;
import org.reflections.Reflections;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

/**
 * This object handles all the permissions releated to the VFS.
 *
 * @author fr0w
 * @version $Id$
 */
public class VFSPermissions {
    private static final Logger logger = LogManager.getLogger(VFSPermissions.class);

    private HashMap<String, PermissionWrapper> _handlersMap;

    // HashMap<Type, HashMap<Directive, List<PathPermission>>>
    private final HashMap<String, HashMap<String, LinkedList<PathPermission>>> _pathPerms;
    private HashMap<String, String> _directiveToType;
    private HashMap<String, TreeMap<Integer, String>> _priorities;

    public VFSPermissions() {
        loadExtensions();

        _pathPerms = new HashMap<>();
    }

    public void loadExtensions() {
        _handlersMap = new HashMap<>();
        _directiveToType = new HashMap<>();
        _priorities = new HashMap<>();

        Set<Class<? extends VFSPermHandler>> vfsHandlers = new Reflections("org.drftpd")
                .getSubTypesOf(VFSPermHandler.class);
        List<Class<? extends VFSPermHandler>> vfsProtocols = vfsHandlers.stream()
                .filter(aClass -> !Modifier.isAbstract(aClass.getModifiers())).collect(Collectors.toList());
        try {
            for (Class<? extends VFSPermHandler> vfsProtocol : vfsProtocols) {
                VFSPermHandler vfsPermHandler = vfsProtocol.getConstructor().newInstance();
                Set<Entry<String, String>> entries = vfsPermHandler.getDirectives().entrySet();
                for (Entry<String, String> typeAndDirective : entries) {
                    String type = typeAndDirective.getKey();
                    String directive = typeAndDirective.getValue();
                    if (_handlersMap.containsKey(directive)) {
                        logger.debug("A handler for '{}' already loaded, check your plugin.xml's", directive);
                        continue;
                    }
                    Method handle = vfsProtocol.getMethod("handle", String.class, StringTokenizer.class);
                    PermissionWrapper pw = new PermissionWrapper(vfsPermHandler, handle);
                    _handlersMap.put(directive, pw);
                    _directiveToType.put(directive, type);

                    // building execution order.
                    int priority = vfsPermHandler.getPriority();
                    TreeMap<Integer, String> order = _priorities.computeIfAbsent(type, k -> new TreeMap<>());
                    while (true) {
                        if (order.containsKey(priority)) {
                            logger.debug("The slot that {} is trying to use is already allocated, check the xmls, allocating the next available slot", directive);
                            priority++;
                        } else {
                            order.put(priority, directive);
                            break;
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Failed to load plugins for master extension point 'VFSPerm', possibly the master"
                    + " extension point definition has changed in the plugin.xml", e);
        }
    }

    private boolean verifyType(String type) {
        type = type.toLowerCase();
        return type.equals("upload") || type.equals("makedir") || type.equals("delete")
                || type.equals("deleteown") || type.startsWith("rename") || type.equals("renameown")
                || type.equals("privpath") || type.equals("download");

    }

    public void handleLine(String directive, StringTokenizer st) {
        if (!_handlersMap.containsKey(directive)) {
            throw new UnsupportedOperationException("No VFSPermHandler found for this directive: " + directive);
        }

        PermissionWrapper pw = _handlersMap.get(directive);

        pw.handle(directive, st);
    }

    protected void addPermissionToMap(String directive, PathPermission pathPerm) {
        String type = _directiveToType.get(directive);

        HashMap<String, LinkedList<PathPermission>> map = _pathPerms.computeIfAbsent(type, k -> new HashMap<>());

        LinkedList<PathPermission> list;
        if (!map.containsKey(directive)) {
            list = new LinkedList<>();
            map.put(directive, list);
        } else {
            list = map.get(directive);
        }

        list.add(pathPerm);
    }

    public boolean checkPathPermission(String type, User user, InodeHandle inode) {
        return checkPathPermission(type, user, inode, false);
    }

    public boolean checkPathPermission(String type, User user, InodeHandle inode, boolean defaults) {
        return checkPathPermission(type, user, inode, defaults, false);
    }

    public boolean checkPathPermission(String type, User user, InodeHandle inode, boolean defaults, boolean invertUserSemantic) {

        if (!verifyType(type)) {
            throw new IllegalArgumentException("Invalid VFS perm type.");
        }

        HashMap<String, LinkedList<PathPermission>> map = _pathPerms.get(type);
        TreeMap<Integer, String> order = _priorities.get(type);

        if (map == null) {
            return defaults;
        }

        if (order == null) {
            NullPointerException npe = new NullPointerException("You've got some screwy plugin.xml files!  Blame fr0w!");
            logger.error(npe, npe);
            throw npe;
        }

        for (Entry<Integer, String> entry : order.entrySet()) {
            String directive = entry.getValue();

            LinkedList<PathPermission> perms = map.get(directive);

            if (perms == null) {
                // 'directive' was not found in perms.conf
                continue;
            }

            if (!perms.isEmpty()) {
                for (PathPermission perm : perms) {
                    if (perm.checkPath(inode)) {
                        if (invertUserSemantic) {
                            return !perm.check(user);
                        }
                        return perm.check(user);
                    }
                }
            }
        }

        return defaults;
    }

    public String getPrivPathRegex() {
        return getPrivPathRegex(null);
    }

    public String getPrivPathRegex(User user) {
        StringBuilder sb = new StringBuilder();

        HashMap<String, LinkedList<PathPermission>> map = _pathPerms.get("privpath");
        if (map == null) {
            // No privpath rules found, return null
            return null;
        }

        LinkedList<PathPermission> perms = map.get("privpath");

        sb.append('(');

        for (PathPermission perm : perms) {
            // Make a regex	based on user perms, assume no permission if user=null
            if (user == null || !perm.check(user)) {
                // User do not have permission
                if (sb.length() != 1) {
                    // not first perm to add, add a | prefix
                    sb.append('|');
                }
                sb.append(((GlobPathPermission) perm).getPattern().pattern());
            }
        }

        sb.append(')');

        if (sb.length() == 2) {
            // no regex added
            return null;
        }
        return sb.toString();
    }

    // if you want to debug this class, call this method.
    public void dumpMap() {
        for (Entry<String, HashMap<String, LinkedList<PathPermission>>> e1 : _pathPerms.entrySet()) {
            String type = e1.getKey();
            HashMap<String, LinkedList<PathPermission>> map = e1.getValue();

            logger.debug("{} is handling:", type);
            TreeMap<Integer, String> order = _priorities.get(type);
            for (Entry<Integer, String> e2 : order.entrySet()) {
                String directive = e2.getValue();
                logger.debug("{}. {}", e2.getKey(), directive);
                if (map.get(directive) == null) {
                    // 'directive' was not found in perms.conf
                    continue;
                }
                logger.debug(map.get(directive).toString());
            }
        }
    }
}
