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
package net.sf.drftpd.master.config;

import java.util.Collection;

import net.sf.drftpd.remotefile.LinkedRemoteFileInterface;

/**
 * @author mog
 *
 * @version $Id: StringPathPermission.java,v 1.6 2004/02/23 01:14:38 mog Exp $
 */
public class StringPathPermission extends PathPermission {
	private String path;

	public StringPathPermission(String path, Collection users) {
		super(users);
		this.path = path;
	}

	public boolean checkPath(LinkedRemoteFileInterface path) {
		assert path.isDirectory() : "Should be a directory";
		return (path.getPath()+"/").startsWith(this.path);
	}
}