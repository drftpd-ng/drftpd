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
package org.drftpd.master.config;

import java.util.List;
import java.util.Properties;

import org.drftpd.dynamicdata.Key;
import org.drftpd.dynamicdata.KeyedMap;
import org.drftpd.permissions.PathPermission;
import org.drftpd.permissions.Permission;
import org.drftpd.usermanager.User;
import org.drftpd.util.PortRange;
import org.drftpd.vfs.DirectoryHandle;
import org.drftpd.vfs.perms.VFSPermissions;

/**
 * @author mog
 * @author fr0w
 * @version $Id$
 */
public interface ConfigInterface {
	public abstract KeyedMap<Key, Object> getKeyedMap();
	
	public abstract Properties getMainProperties();	
	
	public abstract VFSPermissions getVFSPermissions();
	
	public abstract void reload();
	
	public abstract boolean checkPathPermission(String directive, User fromUser, DirectoryHandle path);

	public abstract boolean checkPathPermission(String directive, User fromUser, DirectoryHandle path, boolean defaults);

	public abstract boolean checkPermission(String directive, User user);

	public abstract void addPathPermission(String directive, PathPermission permission);
	
	public abstract void addPermission(String directive, Permission permission);
	
	public abstract List getBouncerIps();

	public abstract boolean getHideIps();

	public abstract String getLoginPrompt();

	public abstract int getMaxUsersExempt();

	public abstract int getMaxUsersTotal();

	public abstract boolean isLoginAllowed(User user);

	public abstract PortRange getPortRange();

	public abstract String getPasvAddress() throws NullPointerException;

	public abstract float getCreditCheckRatio(DirectoryHandle path,	User fromUser);
	
	public abstract float getCreditLossRatio(DirectoryHandle path, User fromUser);

	public abstract String[] getCipherSuites();

}