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
package org.drftpd.vfs;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.drftpd.dynamicdata.Key;
import org.drftpd.dynamicdata.KeyNotFoundException;
import org.drftpd.slave.DIZInfo;

public class VirtualFileSystemFileDIZ extends VirtualFileSystemFile {

	public static final Key DIZ = new Key(VirtualFileSystemFileDIZ.class,
			"diz", DIZInfo.class);

	public VirtualFileSystemFileDIZ(String username, String group, long size,
			Set<String> slaves) {
		super(username, group, size, slaves);
	}

	public VirtualFileSystemFileDIZ(String username, String group, long size,
			String initialSlave) {
		this(username, group, size, new HashSet<String>(Arrays
				.asList(new String[] { initialSlave })));
	}

	public DIZInfo getDIZFile() {
		try {
			return (DIZInfo) getKeyedMap().getObject(DIZ);
		} catch (KeyNotFoundException e) {
			return null;
		}
	}

	public void setDIZFile(DIZInfo dizFile) {
		getKeyedMap().setObject(DIZ, dizFile);
	}

}
