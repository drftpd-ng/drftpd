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

package org.drftpd.zipscript.master;

import org.drftpd.common.util.ConfigLoader;
import org.drftpd.master.GlobalContext;
import org.drftpd.master.vfs.DirectoryHandle;

import java.util.HashSet;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;

/**
 * Maps user and group names shown in race output (IRC announces, race stats in
 * listings and CWD) to a placeholder, as configured by the hideuser.* settings
 * in zipscript.conf. Unless hideuser.enabled is true every name is returned unchanged.
 */
public final class RaceNameMasker {

    public static final String DEFAULT_PLACEHOLDER = "Anonymous";

    private final boolean _enabled;
    private final Set<String> _groups = new HashSet<>();
    private final Set<String> _users = new HashSet<>();
    private final Set<String> _sections = new HashSet<>();
    private final String _userPlaceholder;
    private final String _groupPlaceholder;

    public RaceNameMasker(Properties props) {
        _enabled = "true".equalsIgnoreCase(props.getProperty("hideuser.enabled", "false").trim());
        fill(_groups, props.getProperty("hideuser.groups", ""));
        fill(_users, props.getProperty("hideuser.users", ""));
        fill(_sections, props.getProperty("hideuser.sections", ""));
        String placeholder = props.getProperty("hideuser.placeholder", DEFAULT_PLACEHOLDER).trim();
        _userPlaceholder = placeholder.isEmpty() ? DEFAULT_PLACEHOLDER : placeholder;
        String groupPlaceholder = props.getProperty("hideuser.group.placeholder", "").trim();
        _groupPlaceholder = groupPlaceholder.isEmpty() ? _userPlaceholder : groupPlaceholder;
    }

    /**
     * Loads the current zipscript.conf.
     */
    public static RaceNameMasker load() {
        return new RaceNameMasker(ConfigLoader.loadPluginConfig("zipscript.conf"));
    }

    /**
     * @return the name of the section the directory is in
     */
    public static String sectionOf(DirectoryHandle dir) {
        return GlobalContext.getGlobalContext().getSectionManager().lookup(dir).getName();
    }

    private static void fill(Set<String> set, String value) {
        for (String s : value.split("[\\s,]+")) {
            if (!s.isEmpty()) {
                set.add(s.toLowerCase(Locale.ROOT));
            }
        }
    }

    private static boolean contains(Set<String> set, String name) {
        return name != null && set.contains(name.toLowerCase(Locale.ROOT));
    }

    private boolean active(String section) {
        if (!_enabled || (_groups.isEmpty() && _users.isEmpty())) {
            return false;
        }
        return _sections.isEmpty() || contains(_sections, section);
    }

    /**
     * @param user    the user name
     * @param group   the user's primary group, may be null
     * @param section the section name, may be null
     * @return the user name, or the placeholder if the user or the user's group is hidden
     */
    public String user(String user, String group, String section) {
        if (active(section) && (contains(_users, user) || contains(_groups, group))) {
            return _userPlaceholder;
        }
        return user;
    }

    /**
     * For a group shown on its own (group stats): hidden if the group is listed.
     *
     * @param group   the group name
     * @param section the section name, may be null
     */
    public String group(String group, String section) {
        if (active(section) && group != null && !group.isEmpty() && contains(_groups, group)) {
            return _groupPlaceholder;
        }
        return group;
    }

    /**
     * For the group shown next to a user: hidden if the group is listed or the user is hidden.
     */
    public String groupOf(String user, String group, String section) {
        if (active(section) && group != null && !group.isEmpty()
                && (contains(_groups, group) || contains(_users, user))) {
            return _groupPlaceholder;
        }
        return group;
    }
}
