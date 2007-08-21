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

import org.drftpd.GlobalContext;
import org.drftpd.permissions.PathPermission;
import org.drftpd.permissions.Permission;

/**
 * @author fr0w
 * @version $Id$
 */
public abstract class ConfigHandler {
	protected static GlobalContext getGlobalContext() {
		return GlobalContext.getGlobalContext();
	}
	
	protected void addPathPermission(String directive, PathPermission pathPerm) {
		GlobalContext.getConfig().addPathPermission(directive, pathPerm);
	}
	
	protected void addPermission(String directive, Permission permission) {
		GlobalContext.getConfig().addPermission(directive, permission);
	}
}
