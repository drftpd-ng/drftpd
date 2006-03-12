/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 *
 * DrFTPD is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * DrFTPD; if not, write to the Free Software Foundation, Inc., 59 Temple Place,
 * Suite 330, Boston, MA 02111-1307 USA
 */
package org.drftpd.vfs;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;
import java.util.Set;

import net.sf.drftpd.NoAvailableSlaveException;

import org.drftpd.master.RemoteSlave;
import org.drftpd.slave.CaseInsensitiveHashtable;

public class InodeHandle {
	String _path = null;

	public InodeHandle(String path) {
		_path = path;
	}
	
	private VirtualFileSystemInode getInode() throws FileNotFoundException {
		return VirtualFileSystem.getVirtualFileSystem().getInodeByPath(_path);
	}

	public Set<RemoteSlave> getSlaves() {
		// TODO Auto-generated method stub
		return null;
	}

	public String getPath() {
		// TODO Auto-generated method stub
		return null;
	}

	public void setCheckSum(long checksum) {
		// TODO Auto-generated method stub
		
	}

	public String getName() {
		// TODO Auto-generated method stub
		return null;
	}

	public void removeSlave(RemoteSlave sourceSlave) {
		// TODO Auto-generated method stub
		
	}

	public long getCheckSum() throws NoAvailableSlaveException {
		// TODO Auto-generated method stub
		return 0;
	}

	public void addSlave(RemoteSlave destinationSlave) {
		// TODO Auto-generated method stub
		
	}

	public Set<InodeHandle> getFiles() {
		// TODO Auto-generated method stub
		return null;
	}

	public boolean isDirectory() {
		// TODO Auto-generated method stub
		return false;
	}

	public long lastModified() {
		// TODO Auto-generated method stub
		return 0;
	}

	public boolean isAvailable() {
		// TODO Auto-generated method stub
		return false;
	}

	public boolean isDeleted() {
		// TODO Auto-generated method stub
		return false;
	}

	public boolean isFile() {
		// TODO Auto-generated method stub
		return false;
	}

	public InodeHandle getFile(String string) throws FileNotFoundException {
		// TODO Auto-generated method stub
		return null;
	}

	public Collection<InodeHandle> getDirectories() {
		// TODO Auto-generated method stub
		return null;
	}

	public void delete() {
		// TODO Auto-generated method stub
		
	}

	public void createDirectory() {
		// TODO Auto-generated method stub
		
	}

	public long length() {
		// TODO Auto-generated method stub
		return 0;
	}

	public Collection<RemoteSlave> getAvailableSlaves() throws NoAvailableSlaveException {
		// TODO Auto-generated method stub
		return null;
	}

	public String getUsername() {
		// TODO Auto-generated method stub
		return null;
	}

	public void unmergeDir(RemoteSlave rslave) {
		// TODO Auto-generated method stub
		
	}

	public String getGroupname() {
		// TODO Auto-generated method stub
		return null;
	}

	public long getXfertime() {
		// TODO Auto-generated method stub
		return 0;
	}

	public void remerge(CaseInsensitiveHashtable files, RemoteSlave rslave) throws IOException {
		// TODO Auto-generated method stub
		
	}

}
