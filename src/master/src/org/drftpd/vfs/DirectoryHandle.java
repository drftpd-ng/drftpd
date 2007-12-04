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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.drftpd.GlobalContext;
import org.drftpd.exceptions.FileExistsException;
import org.drftpd.exceptions.ObjectNotFoundException;
import org.drftpd.exceptions.SlaveUnavailableException;
import org.drftpd.master.RemoteSlave;
import org.drftpd.slave.LightRemoteInode;
import org.drftpd.usermanager.User;

import se.mog.io.PermissionDeniedException;

/**
 * @author zubov
 * @version $Id$
 */
public class DirectoryHandle extends InodeHandle implements
		DirectoryHandleInterface {

	public DirectoryHandle(String path) {
		super(path);
	}
	
	/**
	 * @param reason
	 * @throws FileNotFoundException if this Directory does not exist
	 */
	public void abortAllTransfers(String reason) throws FileNotFoundException {
		for (FileHandle file : getFilesUnchecked()) {
			try {
				file.abortTransfers(reason);
			} catch (FileNotFoundException e) {
			}
		}
	}
	
	/**
	 * Returns a DirectoryHandle for a possibly non-existant directory in this path
	 * No verification to its existence is made
	 * @param name
	 * @return
	 */
	public DirectoryHandle getNonExistentDirectoryHandle(String name) {
		if (name.startsWith(VirtualFileSystem.separator)) {
			// absolute path, easy to handle
			return new DirectoryHandle(name);
		}
		// path must be relative
		return new DirectoryHandle(getPath() + VirtualFileSystem.separator + name);
	}
	
	/**
	 * Returns a LinkHandle for a possibly non-existant directory in this path
	 * No verification to its existence is made
	 * @param name
	 * @return
	 */
	public LinkHandle getNonExistentLinkHandle(String name) {
		return new LinkHandle(getPath() + VirtualFileSystem.separator + name);
	}

	/**
	 * @see org.drftpd.vfs.InodleHandle#getInode()
	 */
	@Override
	public VirtualFileSystemDirectory getInode()
			throws FileNotFoundException {
		VirtualFileSystemInode inode = super.getInode();
		if (inode instanceof VirtualFileSystemDirectory) {
			return (VirtualFileSystemDirectory) inode;
		}
		throw new ClassCastException(
				"DirectoryHandle object pointing to Inode:" + inode);
	}

	/**
	 * @return all InodeHandles inside this dir.
	 * @throws FileNotFoundException
	 */
	public Set<InodeHandle> getInodeHandles(User user) throws FileNotFoundException {
		Set<InodeHandle> inodes = getInodeHandlesUnchecked();
		
		for (Iterator<InodeHandle> iter = inodes.iterator(); iter.hasNext();) {
			InodeHandle inode = iter.next();			
			try {
				checkHiddenPath(inode, user);
			} catch (FileNotFoundException e) {
				// file is hidden or a race just happened.
				iter.remove();
			}
		}
		
		return inodes;
	}
	
	/**
	 * @return all InodeHandles inside this dir.
	 * @throws FileNotFoundException
	 */
	public Set<InodeHandle> getInodeHandlesUnchecked() throws FileNotFoundException {
		return getInode().getInodes();
	}

	/**
	 * @return a set containing only the files of this dir.
	 * (no links or directories included.)
	 * @throws FileNotFoundException
	 */
	public Set<FileHandle> getFiles(User user) throws FileNotFoundException {
		return getFilesUnchecked(getInodeHandles(user));
	}
	
	public Set<FileHandle> getFilesUnchecked() throws FileNotFoundException {
		return getFilesUnchecked(getInodeHandlesUnchecked());
	}
	
	private Set<FileHandle> getFilesUnchecked(Set<InodeHandle> inodes) throws FileNotFoundException {
		Set<FileHandle> set = new HashSet<FileHandle>();
		for (InodeHandle handle : getInode().getInodes()) {
			if (handle instanceof FileHandle) {
				set.add((FileHandle) handle);
			}
		}
		return (Set<FileHandle>) set;
	}

	/**.
	 * This method *does* check for hiddens paths.
	 * @return a set containing only the directories of this dir. (no links or files included.)
	 * @throws FileNotFoundException
	 */
	public Set<DirectoryHandle> getDirectories(User user) throws FileNotFoundException {
		return getDirectoriesUnchecked(getInodeHandles(user));
	}
	
	/**
	 * This method does not check for hiddens paths.
	 * @return a set containing only the directories of this dir. (no links or files included.)
	 * @throws FileNotFoundException
	 */
	public Set<DirectoryHandle> getDirectoriesUnchecked() throws FileNotFoundException {
		return getDirectoriesUnchecked(getInodeHandlesUnchecked());
	}
	
	/**
	 * This method iterates through the given Set, removing non-Directory objects.
	 * @return a set containing only the directories of this dir. (no links or files included.)
	 * @throws FileNotFoundException
	 */
	private Set<DirectoryHandle> getDirectoriesUnchecked(Set<InodeHandle> inodes)
		throws FileNotFoundException {
		Set<DirectoryHandle> set = new HashSet<DirectoryHandle>();
		
		for (InodeHandle handle : inodes) {
			if (handle instanceof DirectoryHandle) {
				set.add((DirectoryHandle) handle);
			}
		}
		return (Set<DirectoryHandle>) set;
	}

	/**
	 * @return a set containing only the links of this dir.
	 * (no directories or files included.)
	 * @throws FileNotFoundException
	 */
	
	public Set<LinkHandle> getLinks(User user) throws FileNotFoundException {
		return getLinksUnchecked(getInodeHandles(user));
	}
	
	public Set<LinkHandle> getLinksUnchecked() throws FileNotFoundException {
		return getLinksUnchecked(getInodeHandlesUnchecked());
	}
	
	private Set<LinkHandle> getLinksUnchecked(Set<InodeHandle> inodes) throws FileNotFoundException {
		Set<LinkHandle> set = new HashSet<LinkHandle>();
		for (Iterator<InodeHandle> iter = inodes.iterator(); iter
				.hasNext();) {
			InodeHandle handle = iter.next();
			if (handle instanceof LinkHandle) {
				set.add((LinkHandle) handle);
			}
		}
		return (Set<LinkHandle>) set;
	}
	
	/**
	 * @return true if the dir has offline files.
	 * @throws FileNotFoundException
	 */
	public boolean hasOfflineFiles() throws FileNotFoundException {
		return getOfflineFiles().size() != 0;
	}
	
	/**
	 * @return a set containing only the offline files of this dir.
	 * @throws FileNotFoundException
	 */
	private Set<FileHandle> getOfflineFiles() throws FileNotFoundException {
		Set<FileHandle> allFiles = getFilesUnchecked();
		Set<FileHandle> offlineFiles = new HashSet<FileHandle>(allFiles.size());
		
		for (FileHandle file : allFiles) {
			if (!file.isAvailable())
				offlineFiles.add(file);
		}
		
		return offlineFiles;
	}

	/**
	 * @param name
	 * @throws FileNotFoundException
	 */
	public InodeHandle getInodeHandle(String name, User user) throws FileNotFoundException {
		InodeHandle inode = getInodeHandleUnchecked(name);
		
		checkHiddenPath(inode, user);
		
		return inode;
	}
	
	public InodeHandle getInodeHandleUnchecked(String name) throws FileNotFoundException {		
		VirtualFileSystemInode inode = getInode().getInodeByName(name);
		if (inode.isDirectory()) {
			return new DirectoryHandle(inode.getPath());
		} else if (inode.isFile()) {
			return new FileHandle(inode.getPath());
		} else if (inode.isLink()) {
			return new LinkHandle(inode.getPath());
		}
		throw new IllegalStateException(
				"Not a directory, file, or link -- punt");
	}

	public DirectoryHandle getDirectory(String name, User user) 
			throws FileNotFoundException, ObjectNotValidException {
		DirectoryHandle dir = getDirectoryUnchecked(name);
		
		checkHiddenPath(dir, user);
		
		return dir;
	}
	
	public DirectoryHandle getDirectoryUnchecked(String name)
			throws FileNotFoundException, ObjectNotValidException {
		if (name.equals(VirtualFileSystem.separator)) {
			return new DirectoryHandle("/");
		}
		logger.debug("getDirectory(" + name + ")");
		if (name.equals("..")) {
			return getParent();
		} else if (name.startsWith("../")) {
			// strip off the ../
			return getParent().getDirectoryUnchecked(name.substring(3));
		} else if (name.equals(".")) {
			return this;
		} else if (name.startsWith("./")) {
			return getDirectoryUnchecked(name.substring(2));
		}

		InodeHandle handle = getInodeHandleUnchecked(name);
		if (handle.isDirectory()) {
			return (DirectoryHandle) handle;
		}
		if (handle.isLink()) {
			return ((LinkHandle) handle).getTargetDirectoryUnchecked();
		}
		throw new ObjectNotValidException(name + " is not a directory");
	}
	
	public FileHandle getFile(String name, User user) throws FileNotFoundException, ObjectNotValidException {
		FileHandle file = getFileUnchecked(name);
		
		checkHiddenPath(file.getParent(), user);
		
		return file;
	}

	public FileHandle getFileUnchecked(String name) throws FileNotFoundException,
			ObjectNotValidException {
		InodeHandle handle = getInodeHandleUnchecked(name);
		if (handle.isFile()) {
			return (FileHandle) handle;
		} else if (handle.isLink()) {
			LinkHandle link = (LinkHandle) handle;
			return link.getTargetFileUnchecked();
		}
		throw new ObjectNotValidException(name + " is not a file");
	}
	
	public LinkHandle getLink(String name, User user) throws FileNotFoundException,
			ObjectNotValidException {
		LinkHandle link = getLinkUnchecked(name);
		
		checkHiddenPath(link.getTargetInode(user), user);
		
		return link;
	}

	public LinkHandle getLinkUnchecked(String name) throws FileNotFoundException,
			ObjectNotValidException {
		InodeHandle handle = getInodeHandleUnchecked(name);
		if (handle.isLink()) {
			return (LinkHandle) handle;
		}
		throw new ObjectNotValidException(name + " is not a link");
	}

	private void createRemergedFile(LightRemoteInode lrf, RemoteSlave rslave,
			boolean collision) throws IOException, SlaveUnavailableException {
		String name = lrf.getName();
		if (collision) {
			name = lrf.getName() + ".collision." + rslave.getName();
			rslave.simpleRename(getPath() + lrf.getPath(), getPath(), name);
		}
		FileHandle newFile = createFileUnchecked(name, "drftpd", "drftpd", rslave);
		newFile.setLastModified(lrf.lastModified());
		newFile.setSize(lrf.length());
		//newFile.setCheckSum(rslave.getCheckSumForPath(newFile.getPath()));
		// TODO Implement a Checksum queue on remerge
		newFile.setCheckSum(0);
	}

	public void remerge(List<LightRemoteInode> files, RemoteSlave rslave)
			throws IOException, SlaveUnavailableException {
		Iterator<LightRemoteInode> sourceIter = files.iterator();
		// source comes pre-sorted from the slave
		List<InodeHandle> destinationList = null;
		try {
			destinationList = new ArrayList<InodeHandle>(getInodeHandlesUnchecked());
		} catch (FileNotFoundException e) {
			logger.debug("FileNotFoundException during remerge", e);
			// create directory for merging
			getParent().createDirectoryRecursive(getName());
			
			// lets try this again, this time, if it doesn't work, we throw an
			// IOException up the chain
			destinationList = new ArrayList<InodeHandle>(getInodeHandlesUnchecked());
		}
		Collections.sort(destinationList,
				VirtualFileSystem.INODE_HANDLE_CASE_INSENSITIVE_COMPARATOR);
		Iterator<InodeHandle> destinationIter = destinationList.iterator();
		LightRemoteInode source = null;
		InodeHandle destination = null;
		if (sourceIter.hasNext()) {
			source = sourceIter.next();
		}
		if (destinationIter.hasNext()) {
			destination = destinationIter.next();
		}
		while (true) {
/*			logger.debug("Starting remerge() loop, [destination="
					+ (destination == null ? "null" : destination.getName())
					+ "][source="
					+ (source == null ? "null" : source.getName()) + "]");*/
			// source & destination are set at the "next to process" one OR are
			// null and at the end of that list

			// case1 : source list is out, remove slave from all remaining
			// files/directories
			if (source == null) {
				while (destination != null) {
					// can removeSlave()'s from all types of Inodes, no type
					// checking needed
					destination.removeSlave(rslave);

					if (destinationIter.hasNext()) {
						destination = destinationIter.next();
					} else {
						destination = null;
					}
				}
				// all done, both lists are empty
				return;
			}

			// case2: destination list is out, add files
			if (destination == null) {

				while (source != null) {
					if (source.isFile()) {
						createRemergedFile(source, rslave, false);
					} else {
						throw new IOException(
								source
										+ ".isDirectory() -- this shouldn't happen, this directory should already be created through a previous remerge process");
					}
					if (sourceIter.hasNext()) {
						source = sourceIter.next();
					} else {
						source = null;
					}
				}
				// all done, both lists are empty
				return;
			}

			// both source and destination are non-null
			// we don't know which one is first alphabetically
			int compare = source.getName().compareToIgnoreCase(
					destination.getName());
			// compare is < 0, source comes before destination
			// compare is > 0, source comes after destination
			// compare is == 0, they have the same name

			if (compare < 0) {
				// add the file
				createRemergedFile(source, rslave, false);
				// advance one runner
				if (sourceIter.hasNext()) {
					source = sourceIter.next();
				} else {
					source = null;
				}
			} else if (compare > 0) {
				// remove the slave
				destination.removeSlave(rslave);
				// advance one runner
				if (destinationIter.hasNext()) {
					destination = destinationIter.next();
				} else {
					destination = null;
				}
			} else if (compare == 0) {
				if (destination.isLink()) {
					// this is bad, links don't exist on slaves
					// name collision
					if (source.isFile()) {
						createRemergedFile(source, rslave, true);
						logger.warn("In remerging " + rslave.getName()
								+ ", a file on the slave (" + getPath()
								+ VirtualFileSystem.separator
								+ source.getName()
								+ ") collided with a link on the master");
						// set crc now?
					} else { // source.isDirectory()
						logger.warn("In remerging " + rslave.getName()
								+ ", a directory on the slave (" + getPath()
								+ VirtualFileSystem.separator
								+ source.getName()
								+ ") collided with a link on the master");
					}
				} else if (source.isFile() && destination.isFile()) {
					// both files
					FileHandle destinationFile = (FileHandle) destination;
/*					long sourceCRC = rslave.getCheckSumForPath(getPath()
							+ VirtualFileSystem.separator + source.getName());
					long destinationCRC;
					try {
						destinationCRC = destinationFile.getCheckSum();
					} catch (NoAvailableSlaveException e) {
						destinationCRC = 0L;
					}
*/					if (source.length() != destinationFile.getSize()) {
//							|| (sourceCRC != destinationCRC && destinationCRC != 0L)) {
						// handle collision
						createRemergedFile(source, rslave, true);
						logger.warn("In remerging " + rslave.getName()
								+ ", a file on the slave (" + getPath()
								+ VirtualFileSystem.separator
								+ source.getName()
								+ ") collided with a file on the master");
						// set crc now?
					} else {
						destinationFile.addSlave(rslave);
					}
				} else if (source.isDirectory() && destination.isDirectory()) {
					// this is good, do nothing other than take up this case
				} else {
					// we have a directory/name collission, let's find which one
					// :)
					if (source.isDirectory()) { // & destination.isFile()
						// we don't care about directories on the slaves, let's
						// just skip it
						logger.warn("In remerging " + rslave.getName()
								+ ", a directory on the slave (" + getPath()
								+ VirtualFileSystem.separator
								+ source.getName()
								+ ") collided with a file on the master");
					} else {
						// source.isFile() && destination.isDirectory()
						// handle collision
						createRemergedFile(source, rslave, true);
						// set crc now?
					}
				}
				// advance both runners, they were equal
				if (destinationIter.hasNext()) {
					destination = destinationIter.next();
				} else {
					destination = null;
				}
				if (sourceIter.hasNext()) {
					source = sourceIter.next();
				} else {
					source = null;
				}
			}
		}
	}
	
	/**
	 * Shortcut to create "owner-less" directories.
	 */
	public DirectoryHandle createDirectorySystem(String name) throws FileExistsException, FileNotFoundException {
		return createDirectoryUnchecked(name, "drftpd", "drftpd");
	}

	/**
	 * Given a DirectoryHandle, it makes sure that this directory and all of its parent(s) exist
	 * @param name
	 * @throws FileExistsException
	 * @throws FileNotFoundException
	 */
	public void createDirectoryRecursive(String name)
			throws FileExistsException, FileNotFoundException {
		DirectoryHandle dir = null;
		try {
			dir = createDirectorySystem(name);
		} catch (FileNotFoundException e) {
			getParent().createDirectoryRecursive(getName());
		} catch (FileExistsException e) {
			logger.debug("Object already exists -- " + getPath()
					+ VirtualFileSystem.separator + name, e);
			throw new FileExistsException("Object already exists -- "
					+ getPath() + VirtualFileSystem.separator + name);
		}
		if (dir == null) {
			dir = createDirectorySystem(name);
		}
		logger.debug("Created directory " + dir);
	}

	/**
	 * Creates a Directory object in the FileSystem with this directory as its parent.<br>
	 * This method does not check for permissions, so be careful while using it.<br>
	 * @see For a checked way of creating dirs {@link #createFile(User, String, RemoteSlave)};
	 * @param user
	 * @param group
	 * @return the created directory.
	 * @throws FileNotFoundException
	 * @throws FileExistsException
	 */
	public DirectoryHandle createDirectoryUnchecked(String name, String user,
			String group) throws FileExistsException, FileNotFoundException {
		getInode().createDirectory(name, user, group);
		try {
			return getDirectoryUnchecked(name);
		} catch (FileNotFoundException e) {
			throw new RuntimeException("Something really funky happened, we just created it", e);
		} catch (ObjectNotValidException e) {
			throw new RuntimeException("Something really funky happened, we just created it", e);
		}
	}
	
	/**
	 * Attempts to create a Directory in the FileSystem with this directory as parent.
	 * @see For an unchecked way of creating dirs: {@link #createDirectoryUnchecked(String, String, String)}
	 * @param user
	 * @param name
	 * @return the created directory.
	 * @throws PermissionDeniedException if the given user is not allowed to create dirs.
	 * @throws FileExistsException
	 * @throws FileNotFoundException
	 */
	public DirectoryHandle createDirectory(User user, String name) 
			throws PermissionDeniedException, FileExistsException, FileNotFoundException {		
		if (user == null) {
			throw new PermissionDeniedException("User cannot be null");
		}
		
		DirectoryHandle newDir = getNonExistentDirectoryHandle(name);
		
		checkHiddenPath(newDir, user);
		
		if (!getVFSPermissions().checkPathPermission("makedir", user, newDir)) {
			throw new PermissionDeniedException("You are not allowed to create a directory at "+ newDir.getParent());
		}
		
		return createDirectoryUnchecked(name, user.getName(), user.getGroup());
	}

	/**
	 * Creates a File object in the FileSystem with this directory as its parent.<br>
	 * This method does not check for permissions, so be careful while using it.<br>
	 * @see For unchecked creating of files {@link #createFileUnchecked(String, String, String, RemoteSlave)}
	 * @param name
	 * @param user
	 * @param group
	 * @param initialSlave
	 * @return the created file.
	 * @throws FileExistsException
	 * @throws FileNotFoundException
	 */
	public FileHandle createFileUnchecked(String name, String user, String group,
			RemoteSlave initialSlave) throws FileExistsException,
			FileNotFoundException {
		getInode().createFile(name, user, group, initialSlave.getName());
		try {
			return getFileUnchecked(name);
		} catch (FileNotFoundException e) {
			throw new RuntimeException("Something really funky happened, we just created it", e);
		} catch (ObjectNotValidException e) {
			throw new RuntimeException("Something really funky happened, we just created it", e);
		}
	}
	
	/**
	 * Attempts to create a File in the FileSystem having this directory as parent.
	 * @param user
	 * @param name
	 * @param initialSlave
	 * @return
	 * @throws PermissionDeniedException if the user is not allowed to create a file in this dir.
	 * @throws FileExistsException
	 * @throws FileNotFoundException
	 */
	public FileHandle createFile(User user, String name, RemoteSlave initialSlave) 
			throws PermissionDeniedException, FileExistsException, FileNotFoundException {
		if (user == null) {
			throw new PermissionDeniedException("User cannot be null");
		}
		
		checkHiddenPath(this, user);
		
		if (!getVFSPermissions().checkPathPermission("upload", user, this)) {
			throw new PermissionDeniedException("You are not allowed to upload to "+ getParent());
		}
		
		return createFileUnchecked(name, user.getName(), user.getGroup(), initialSlave);
	}

	/**
	 * Creates a Link object in the FileSystem with this directory as its parent
	 */
	public LinkHandle createLinkUnchecked(String name, String target, String user,
			String group) throws FileExistsException, FileNotFoundException {
		getInode().createLink(name, target, user, group);
		try {
			return getLinkUnchecked(name);
		} catch (FileNotFoundException e) {
			throw new RuntimeException("Something really funky happened, we just created it", e);
		} catch (ObjectNotValidException e) {
			throw new RuntimeException("Something really funky happened, we just created it", e);
		}
	}
	
	public LinkHandle createLink(User user, String name, String target)
		throws FileExistsException, FileNotFoundException, PermissionDeniedException {
		if (user == null) {
			throw new PermissionDeniedException("User cannot be null");
		}
		
		// check if this dir is hidden.
		checkHiddenPath(this, user);

		InodeHandle inode = getInodeHandle(target, user);
		
		// check if the target is hidden
		checkHiddenPath(inode, user);
		
		if (inode.isLink()) {
			throw new PermissionDeniedException("Impossible to point a link to a link");
		}
		
		return createLinkUnchecked(name, target, user.getName(), user.getGroup());	
	}

	public boolean isRoot() {
		return equals(GlobalContext.getGlobalContext().getRoot());
	}

	/**
	 * For use during PRET
	 * Returns a FileHandle for a possibly non-existant directory in this path
	 * No verification to its existence is made
	 * @param name
	 * @return
	 */
	public FileHandle getNonExistentFileHandle(String argument) {
		if (argument.startsWith(VirtualFileSystem.separator)) {
			// absolute path, easy to handle
			return new FileHandle(argument);
		}
		// path must be relative
		return new FileHandle(getPath() + VirtualFileSystem.separator
				+ argument);
	}

	public void removeSlave(RemoteSlave rslave) throws FileNotFoundException {
		boolean empty = isEmptyUnchecked();
		for (InodeHandle inode : getInodeHandlesUnchecked()) {
			inode.removeSlave(rslave);
		}
		if (!empty && isEmptyUnchecked()) { // if it wasn't empty before, but is now, delete it
			deleteUnchecked();
		}
	}

	public boolean isEmptyUnchecked() throws FileNotFoundException {
		return getInodeHandlesUnchecked().size() == 0;
	}
	
	public boolean isEmpty(User user) throws FileNotFoundException, PermissionDeniedException {
		// let's fetch the list of existent files inside this dir
		// if the dir does not exist, FileNotFoundException is thrown
		// if the dir exists the operation continues smoothly.
		getInode();
		
		try {
			checkHiddenPath(this, user);
		} catch (FileNotFoundException e) {
			// either a race condition happened or the dir is hidden
			// cuz we just checked and the dir was here.
			throw new PermissionDeniedException("Unable to check if the directory is empty.");
		}
		
		return isEmptyUnchecked();
	}

	@Override
	public boolean isDirectory() {
		return true;
	}

	@Override
	public boolean isFile() {
		return false;
	}

	@Override
	public boolean isLink() {
		return false;
	}
	
	@Override
	public void deleteUnchecked() throws FileNotFoundException {
		abortAllTransfers("Directory " + getPath() + " is being deleted");
		GlobalContext.getGlobalContext().getSlaveManager().deleteOnAllSlaves(this);
		super.deleteUnchecked();
	}

	public long validateSizeRecursive() throws FileNotFoundException {
		Set<InodeHandle> inodes = getInodeHandlesUnchecked();
		long newSize = 0;
		long oldSize = getSize();
		for (InodeHandle inode : inodes) {
			if (inode.isDirectory()) {
				((DirectoryHandle) inode).validateSizeRecursive();
			}
			newSize += inode.getSize();
		}
		getInode().setSize(newSize);
		return oldSize - newSize;
	}
}
