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

import org.drftpd.master.RemoteSlave;
import org.drftpd.usermanager.User;

import java.io.FileNotFoundException;

/**
 * @author zubov
 * @version $Id$
 */
public class LinkHandle extends InodeHandle implements LinkHandleInterface {

	public LinkHandle(String path) {
		super(path);
	}

	@Override
	protected VirtualFileSystemLink getInode() throws FileNotFoundException {
		VirtualFileSystemInode inode = super.getInode();
		if (inode instanceof VirtualFileSystemLink) {
			return (VirtualFileSystemLink) inode;
		}
		throw new ClassCastException("LinkHandle object pointing to Inode:"
				+ inode);
	}
	
	public void setTarget(String path) throws FileNotFoundException {
		getInode().setLinkPath(path);
	}

	public DirectoryHandle getTargetDirectory(User user) throws FileNotFoundException,
			ObjectNotValidException {
		DirectoryHandle dir = getParent().getDirectoryUnchecked(getInode().getLinkPath());
		checkHiddenPath(dir, user);
		return dir;
	}
	
	public DirectoryHandle getTargetDirectoryUnchecked() throws FileNotFoundException,
			ObjectNotValidException {
		return getParent().getDirectoryUnchecked(getInode().getLinkPath());
	}
	
	public FileHandle getTargetFileUnchecked() throws FileNotFoundException, ObjectNotValidException {
		return getParent().getFileUnchecked(getInode().getLinkPath());
	}
	
	public FileHandle getTargetFile(User user) throws FileNotFoundException, ObjectNotValidException {
		FileHandle file = getParent().getFileUnchecked(getInode().getLinkPath());
		checkHiddenPath(file.getParent(), user);		
		return file;
	}
	
	public String getTargetString() throws FileNotFoundException {
		return getInode().getLinkPath();
	}

	public String getTargetStringWithSlash() throws FileNotFoundException {
		return getInode().getLinkPath() + VirtualFileSystem.separator;
	}
	
	@Override
	public void removeSlave(RemoteSlave rslave) throws FileNotFoundException {
		// we don't have anything to remove
	}

	@Override
	public boolean isDirectory() {
		return false;
	}

	@Override
	public boolean isFile() {
		return false;
	}

	@Override
	public boolean isLink() {
		return true;
	}

	public InodeHandle getTargetInode(User user) throws FileNotFoundException {
		InodeHandle inode = getTargetInodeUnchecked();
		checkHiddenPath(inode, user);
		return inode;
	}

	public InodeHandle getTargetInodeUnchecked() throws FileNotFoundException {
		return getParent().getInodeHandleUnchecked(getInode().getLinkPath());
	}

}
