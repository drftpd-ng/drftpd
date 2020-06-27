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
package org.drftpd.master.network;

import org.drftpd.common.dynamicdata.Key;
import org.drftpd.common.dynamicdata.KeyedMap;
import org.drftpd.common.util.Bytes;
import org.drftpd.master.GlobalContext;
import org.drftpd.master.commands.usermanagement.UserManagement;
import org.drftpd.master.usermanager.*;
import org.drftpd.master.util.ReplacerUtils;

import java.util.*;

/**
 * @author djb61
 * @version $Id$
 */
@SuppressWarnings("serial")
public abstract class Session extends KeyedMap<Key<?>, Object> {

    public static final Key<HashMap<String, Properties>> COMMANDS = new Key<>(Session.class, "commands");

    private boolean _aborted = false;

    public HashMap<String, Properties> getCommands() {
        return getObject(Session.COMMANDS, null);
    }

    public void setCommands(HashMap<String, Properties> commands) {
        setObject(Session.COMMANDS, commands);
    }

    public Map<String, Object> getReplacerEnvironment(Map<String, Object> inheritedEnv, User user) {
        Map<String, Object> env = new HashMap<>();
        if (inheritedEnv != null) env.putAll(inheritedEnv);
        if (user != null) {
            for (Map.Entry<Key<?>, Object> entry : user.getKeyedMap().getAllObjects().entrySet()) {
                String key = entry.getKey().toString();
                String value = entry.getValue().toString();
                if (key.equals("org.drftpd.master.commands.nuke.metadata.NukeUserData@nukedBytes"))
                    value = Bytes.formatBytes(Long.parseLong(value));
                env.put(key, value);
            }
            env.put("user", user.getName());
            env.put("username", user.getName());
            env.put("idletime", "" + user.getIdleTime());
            env.put("credits", Bytes.formatBytes(user.getCredits()));
            env.put("ratio", "" + user.getKeyedMap().get(UserManagement.RATIO));
            env.put("tagline", user.getKeyedMap().get(UserManagement.TAGLINE));
            env.put("uploaded", Bytes.formatBytes(user.getUploadedBytes()));
            env.put("downloaded", Bytes.formatBytes(user.getDownloadedBytes()));
            env.put("group", user.getGroup());
            env.put("groups", user.getGroups());
            env.put("averagespeed", Bytes.formatBytes((user.getDownloadedBytes() + user.getUploadedBytes())
                    / (((user.getDownloadedTime() + user.getUploadedTime()) / 1000) + 1)));
            env.put("ipmasks", user.getHostMaskCollection().toString());
            env.put("isbanned", "" + (user.getKeyedMap().getObject(UserManagement.BANTIME, new Date()).getTime() > System.currentTimeMillis()));
        }
        return env;
    }

    public User getUserNull(String user) {
        if (user == null) {
            return null;
        }
        try {
            return GlobalContext.getGlobalContext().getUserManager().getUserByNameUnchecked(user);
        } catch (NoSuchUserException | UserFileException e) {
            return null;
        }
    }

    protected User getUserObject(String user) throws NoSuchUserException, UserFileException {
        return GlobalContext.getGlobalContext().getUserManager().getUserByName(user);
    }

    public Group getGroupNull(String group) {
        if (group == null) {
            return null;
        }
        try {
            return GlobalContext.getGlobalContext().getUserManager().getGroupByNameUnchecked(group);
        } catch (NoSuchGroupException | GroupFileException e) {
            return null;
        }
    }

    protected Group getGroupObject(String group) throws NoSuchGroupException, GroupFileException {
        return GlobalContext.getGlobalContext().getUserManager().getGroupByName(group);
    }

    public String jprintf(ResourceBundle bundle, String key, String user) {
        return ReplacerUtils.jprintf(key, getReplacerEnvironment(null, getUserNull(user)), bundle);
    }

    public String jprintf(ResourceBundle bundle, Map<String, Object> inheritedEnv, String key) {
        return ReplacerUtils.jprintf(key, getReplacerEnvironment(inheritedEnv, null), bundle);
    }

    public String jprintf(ResourceBundle bundle, String key, Map<String, Object> env, String user) {
        return ReplacerUtils.jprintf(key, getReplacerEnvironment(env, getUserNull(user)), bundle);
    }

    public String jprintf(ResourceBundle bundle, String key, Map<String, Object> inheritedEnv, User user) {
        return ReplacerUtils.jprintf(key, getReplacerEnvironment(inheritedEnv, user), bundle);
    }

    public abstract boolean isSecure();

    public abstract void printOutput(Object o);

    public abstract void printOutput(int code, Object o);

    public void abortCommand() {
        _aborted = true;
    }

    public void clearAborted() {
        _aborted = false;
    }

    public boolean isAborted() {
        return _aborted;
    }
}
