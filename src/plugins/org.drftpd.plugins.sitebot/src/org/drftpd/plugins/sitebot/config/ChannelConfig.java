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
package org.drftpd.plugins.sitebot.config;

import org.drftpd.permissions.Permission;
import org.drftpd.usermanager.User;

import java.util.StringTokenizer;

/**
 * @author djb61
 * @version $Id$
 */
public class ChannelConfig {

	private String _name;

	private String _blowKey;

	private String _blowMode;

	private String _chanKey;

	private Permission _perms;

	public ChannelConfig(String name, String blowKey, String blowMode, String chanKey, String perms) {
		_name = name;
		_blowKey = blowKey;
		_blowMode = blowMode;
		_chanKey = chanKey;
		_perms = new Permission(Permission.makeUsers(new StringTokenizer(perms)));
	}

	public String getName() {
		return _name;
	}

	public String getBlowKey() {
		return _blowKey;
	}

	public String getBlowMode() {
		return _blowMode;
	}

	public String getChanKey() {
		return _chanKey;
	}

	public boolean isPermitted(User user) {
		return _perms.check(user);
	}
}
