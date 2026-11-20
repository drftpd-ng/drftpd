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

/**
 * Resolves the per-user home directory template ({@code homedir} in master.conf,
 * or the per-user override set with {@code SITE CHANGE <user> homedir <path>}).
 * <p>
 * This class is pure string handling so it can be unit tested without a VFS.
 * A home directory never grants any permission: perms.conf still applies.
 */
public final class HomeDirectory {

    /** master.conf key holding the home directory template, empty/absent = disabled. */
    public static final String CONF_HOMEDIR = "homedir";

    /** master.conf key, when true a missing home directory is created at login. */
    public static final String CONF_CREATE = "homedir.create";

    public static final String USER_PLACEHOLDER = "${user}";

    public static final String GROUP_PLACEHOLDER = "${group}";

    private HomeDirectory() {
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
