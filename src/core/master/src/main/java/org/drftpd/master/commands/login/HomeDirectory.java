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
package org.drftpd.master.commands.login;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.drftpd.common.io.PermissionDeniedException;
import org.drftpd.master.vfs.ObjectNotValidException;
import org.drftpd.slave.exceptions.FileExistsException;

import java.io.FileNotFoundException;

/**
 * Resolves the per-user home directory template ({@code homedir} in master.conf,
 * or the per-user override set with {@code SITE CHANGE <user> homedir <path>}),
 * and decides at login whether the user lands in it, whether it is created or
 * whether the user stays in the root directory.
 * <p>
 * The VFS is only reached through {@link Access} so this can be unit tested.
 * A home directory never grants any permission: perms.conf still applies.
 */
public final class HomeDirectory {

    private static final Logger logger = LogManager.getLogger(HomeDirectory.class);

    /**
     * The VFS operations needed by {@link #locate}, all performed as the user logging in.
     *
     * @param <D> the directory type
     */
    public interface Access<D> {
        /**
         * Looks up the directory as the user, so privpath applies.
         *
         * @throws FileNotFoundException  if it does not exist or is hidden from the user
         * @throws ObjectNotValidException if it is not a directory
         */
        D lookup(String path) throws FileNotFoundException, ObjectNotValidException;

        /** @return true if anything exists at the path, whether or not the user can see it */
        boolean exists(String path);

        /** @return true if the user has the 'makedir' permission for the path, as checked by MKD */
        boolean canMakeDir(String path);

        /**
         * Creates the directory as the user would with MKD (permission check and events).
         *
         * @throws FileExistsException if it was created meanwhile (e.g. by a concurrent login)
         * @throws FileNotFoundException if the parent does not exist or is hidden from the user
         */
        D create(String path) throws FileExistsException, FileNotFoundException,
                ObjectNotValidException, PermissionDeniedException;
    }

    /** master.conf key holding the home directory template, empty/absent = disabled. */
    public static final String CONF_HOMEDIR = "homedir";

    /** master.conf key, when true a missing home directory is created at login. */
    public static final String CONF_CREATE = "homedir.create";

    public static final String USER_PLACEHOLDER = "${user}";

    public static final String GROUP_PLACEHOLDER = "${group}";

    private HomeDirectory() {
    }

    /**
     * Decides where the user lands after login.
     * <ul>
     * <li>the home directory exists and is visible to the user: land in it</li>
     * <li>it is missing and {@code create} is false: stay in the root directory</li>
     * <li>it is missing and the user has 'makedir' for it: create it, like MKD would</li>
     * <li>it is missing and the user lacks 'makedir': stay in the root directory</li>
     * <li>it was created meanwhile by a concurrent login: land in it</li>
     * </ul>
     *
     * @param path   the resolved home directory, see {@link #resolve}
     * @param create whether 'homedir.create' is enabled
     * @param user   the user name, only used for logging
     * @return the home directory, or null to stay in the root directory
     */
    public static <D> D locate(String path, boolean create, String user, Access<D> access) {
        try {
            return access.lookup(path);
        } catch (ObjectNotValidException e) {
            logger.warn("Home directory '{}' of user '{}' is not a directory", path, user);
            return null;
        } catch (FileNotFoundException e) {
            // Does not exist (or is hidden from the user), create it below if allowed
        }
        if (!create) {
            logger.debug("Home directory '{}' of user '{}' does not exist, staying at root", path, user);
            return null;
        }
        if (access.exists(path)) {
            // Something exists there that the user is not allowed to see, do not touch it
            logger.debug("Home directory '{}' of user '{}' is hidden from the user, staying at root", path, user);
            return null;
        }
        if (!access.canMakeDir(path)) {
            logger.info("Not creating home directory '{}', user '{}' has no 'makedir' permission there, staying at root", path, user);
            return null;
        }
        try {
            D homeDir = access.create(path);
            logger.info("Created home directory '{}' for user '{}'", path, user);
            return homeDir;
        } catch (FileExistsException e) {
            // Created meanwhile, e.g. by a concurrent login of the same user
            try {
                return access.lookup(path);
            } catch (FileNotFoundException | ObjectNotValidException e2) {
                logger.warn("Home directory '{}' of user '{}' was created meanwhile but cannot be used", path, user);
            }
        } catch (FileNotFoundException | ObjectNotValidException e) {
            logger.warn("Cannot create home directory '{}' for user '{}', its parent is missing or hidden", path, user);
        } catch (PermissionDeniedException e) {
            logger.info("Not creating home directory '{}' for user '{}': {}", path, user, e.getMessage());
        }
        return null;
    }

    /**
     * Expands the placeholders of the given template and validates the result.
     *
     * @param template the configured template, may be null or empty
     * @param user     the user name substituted for {@code ${user}}
     * @param group    the primary group name substituted for {@code ${group}}, may be null
     * @return the absolute, normalized home directory path, or null if the template
     * is empty or the result is not a safe absolute path
     */
    public static String resolve(String template, String user, String group) {
        if (template == null || template.isBlank()) {
            return null;
        }
        String path = template.trim();
        if (path.contains(USER_PLACEHOLDER)) {
            if (!isSafeValue(user)) {
                return null;
            }
            path = path.replace(USER_PLACEHOLDER, user);
        }
        if (path.contains(GROUP_PLACEHOLDER)) {
            if (!isSafeValue(group)) {
                return null;
            }
            path = path.replace(GROUP_PLACEHOLDER, group);
        }
        // Unknown placeholders are almost certainly a configuration mistake
        if (path.contains("${")) {
            return null;
        }
        return normalize(path);
    }

    /**
     * Checks whether a template can produce a valid path, used to validate
     * values given to {@code SITE CHANGE <user> homedir}.
     */
    public static boolean isValidTemplate(String template) {
        return resolve(template, "user", "group") != null;
    }

    /**
     * Parses the value of {@code SITE CHANGE <user> homedir <path|none>}. The value is
     * the rest of the command line, so paths may contain spaces.
     *
     * @param argument everything after 'homedir'
     * @return the template to store, an empty string to remove the per-user override
     * ('none'), or null if the value is missing or not a valid template
     */
    public static String parseChangeArgument(String argument) {
        if (argument == null || argument.isBlank()) {
            return null;
        }
        String value = argument.trim();
        if (value.equalsIgnoreCase("none")) {
            return "";
        }
        return isValidTemplate(value) ? value : null;
    }

    /**
     * A substituted value must be a single path segment: no separators, no
     * relative segments, no placeholder syntax and no control characters.
     */
    static boolean isSafeValue(String value) {
        if (value == null || value.isEmpty() || value.equals(".") || value.equals("..")
                || value.contains("${")) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '/' || c == '\\' || Character.isISOControl(c)) {
                return false;
            }
        }
        return true;
    }

    /**
     * @return the path without trailing separator, or null if it is not absolute
     * or contains empty, '.' or '..' segments or control characters.
     */
    static String normalize(String path) {
        if (!path.startsWith("/") || path.contains("\\")) {
            return null;
        }
        while (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        if (path.equals("/")) {
            return path;
        }
        for (String segment : path.substring(1).split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                return null;
            }
            for (int i = 0; i < segment.length(); i++) {
                if (Character.isISOControl(segment.charAt(i))) {
                    return null;
                }
            }
        }
        return path;
    }
}
