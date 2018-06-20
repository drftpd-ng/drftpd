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
package org.drftpd.master;

import org.drftpd.Bytes;
import org.drftpd.GlobalContext;
import org.drftpd.commands.UserManagement;
import org.drftpd.dynamicdata.Key;
import org.drftpd.dynamicdata.KeyedMap;
import org.drftpd.usermanager.NoSuchUserException;
import org.drftpd.usermanager.User;
import org.drftpd.usermanager.UserFileException;
import org.drftpd.util.ReplacerUtils;
import org.tanesha.replacer.ReplacerEnvironment;

import java.util.*;

/**
 * @author djb61
 * @version $Id$
 */
@SuppressWarnings("serial")
public abstract class Session extends KeyedMap<Key<?>, Object> {

	public static final Key<HashMap<String, Properties>> COMMANDS = new Key<>(Session.class, "commands");

	private boolean _aborted = false;

	public void setCommands(HashMap<String,Properties> commands) {
		setObject(Session.COMMANDS, commands);
	}

	public HashMap<String, Properties> getCommands() {
		return getObject(Session.COMMANDS, null);
	}

	public ReplacerEnvironment getReplacerEnvironment(
			ReplacerEnvironment env, User user) {
		env = new ReplacerEnvironment(env);

		if (user != null) {
			for (Map.Entry<Key<?>, Object> entry : user.getKeyedMap().getAllObjects().entrySet()) {
				String key = entry.getKey().toString();
				String value = entry.getValue().toString();
				if (key.equals("org.drftpd.commands.nuke.metadata.NukeUserData@nukedBytes"))
					value = Bytes.formatBytes(Long.parseLong(value));
				env.add(key, value);
			}
			env.add("user", user.getName());
			env.add("username", user.getName());
			env.add("idletime", "" + user.getIdleTime());
			env.add("credits", Bytes.formatBytes(user.getCredits()));
			env.add("ratio", ""+ user.getKeyedMap().get(UserManagement.RATIO));
			env.add("tagline", user.getKeyedMap().get(UserManagement.TAGLINE));
			env.add("uploaded", Bytes.formatBytes(user.getUploadedBytes()));
			env.add("downloaded", Bytes.formatBytes(user.getDownloadedBytes()));
			env.add("group", user.getGroup());
			env.add("groups", user.getGroups());
			env.add("averagespeed", Bytes.formatBytes((user.getDownloadedBytes()+user.getUploadedBytes())
					/ (((user.getDownloadedTime()+user.getUploadedTime())/1000)+1)));
			env.add("ipmasks", user.getHostMaskCollection().toString());
			env.add("isbanned",""+ (user.getKeyedMap().getObject(UserManagement.BAN_TIME, new Date()).getTime() > System.currentTimeMillis()));
		}
		return env;
	}
	
	public User getUserNull(String user) {
		if (user == null) {
			return null;
		}
		try {
			return GlobalContext.getGlobalContext().getUserManager().getUserByNameUnchecked(user);
		} catch (NoSuchUserException e) {
			return null;
		} catch (UserFileException e) {
			return null;
		}
	}
	
	protected User getUserObject(String user) throws NoSuchUserException, UserFileException {
		return GlobalContext.getGlobalContext().getUserManager().getUserByName(user);
	}

	public String jprintf(ResourceBundle bundle, String key, String user) {
		return ReplacerUtils.jprintf(key, getReplacerEnvironment(null, getUserNull(user)), bundle);
	}
	
	public String jprintf(ResourceBundle bundle, ReplacerEnvironment env, String key) {
		return ReplacerUtils.jprintf(key, getReplacerEnvironment(env, null), bundle);
	}

	public String jprintf(ResourceBundle bundle, String key, ReplacerEnvironment env, String user) {
		return ReplacerUtils.jprintf(key, getReplacerEnvironment(env, getUserNull(user)), bundle);
	}

	public String jprintf(ResourceBundle bundle, String key, ReplacerEnvironment env, User user) {
		return ReplacerUtils.jprintf(key, getReplacerEnvironment(env, user), bundle);
	}

	public String jprintf(Class<?> baseName, String key, ReplacerEnvironment env, User user) {
		ResourceBundle bundle = ResourceBundle.getBundle(baseName.getName());

		return ReplacerUtils.jprintf(key, getReplacerEnvironment(env, user), bundle);
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
