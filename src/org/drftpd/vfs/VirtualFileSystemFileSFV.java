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

import org.drftpd.SFVFile;
import org.drftpd.dynamicdata.Key;
import org.drftpd.dynamicdata.KeyNotFoundException;

public class VirtualFileSystemFileSFV extends VirtualFileSystemFile {

	public static final Key SFV = new Key(VirtualFileSystemFileSFV.class, "sfv", SFVFile.class);

	public VirtualFileSystemFileSFV(String username, String group, long size, Set<String> slaves) {
		super(username, group, size, slaves);
	}
	
	public VirtualFileSystemFileSFV(String username, String group, long size, String initialSlave) {
		this(username, group, size, new HashSet<String>(Arrays.asList(new String[] { initialSlave })));
	}

	public SFVFile getSFVFile() {
		try {
			return (SFVFile) getKeyedMap().getObject(SFV);
		} catch (KeyNotFoundException e) {
			return null;
		}
	}

	public void setSFVFile(SFVFile sfvFile) {
		getKeyedMap().setObject(SFV, sfvFile);
	}

}
