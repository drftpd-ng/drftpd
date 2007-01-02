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

import net.sf.drftpd.FileExistsException;
import net.sf.drftpd.NoAvailableSlaveException;
import net.sf.drftpd.SlaveUnavailableException;

import org.drftpd.GlobalContext;
import org.drftpd.master.RemoteSlave;
import org.drftpd.slave.LightRemoteInode;

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
	 * Returns a DirectoryHandle for a possibly non-existant directory in this path
	 * No verification to its existence is made
	 * @param name
	 * @return
	 */
	public DirectoryHandle getNonExistentDirectoryHandle(String name) {
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

	@Override
	protected VirtualFileSystemDirectory getInode()
			throws FileNotFoundException {
		VirtualFileSystemInode inode = super.getInode();
		if (inode instanceof VirtualFileSystemDirectory) {
			return (VirtualFileSystemDirectory) inode;
		}
		throw new ClassCastException(
				"DirectoryHandle object pointing to Inode:" + inode);
	}

	public Set<FileHandle> getFiles() throws FileNotFoundException {
		Set<FileHandle> set = new HashSet<FileHandle>();
		for (Iterator<InodeHandle> iter = getInode().getInodes().iterator(); iter
				.hasNext();) {
			InodeHandle handle = iter.next();
			if (handle instanceof FileHandle) {
				set.add((FileHandle) handle);
			}
		}
		return (Set<FileHandle>) set;
	}

	public Set<InodeHandle> getInodeHandles() throws FileNotFoundException {
		return getInode().getInodes();
	}

	public Set<DirectoryHandle> getDirectories() throws FileNotFoundException {
		Set<DirectoryHandle> set = new HashSet<DirectoryHandle>();
		for (Iterator<InodeHandle> iter = getInode().getInodes().iterator(); iter
				.hasNext();) {
			InodeHandle handle = iter.next();
			if (handle instanceof DirectoryHandle) {
				set.add((DirectoryHandle) handle);
			}
		}
		return (Set<DirectoryHandle>) set;
	}

	public InodeHandle getInodeHandle(String name) throws FileNotFoundException {
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

	public DirectoryHandle getDirectory(String name)
			throws FileNotFoundException, ObjectNotValidException {
		if (name.equals(VirtualFileSystem.separator)) {
			return new DirectoryHandle("/");
		}
		logger.debug("getDirectory(" + name + ")");
		if (name.equals("..")) {
			return getParent();
		} else if (name.startsWith("../")) {
			// strip off the ../
			return getParent().getDirectory(name.substring(3));
		} else if (name.equals(".")) {
			return this;
		} else if (name.startsWith("./")) {
			return getDirectory(name.substring(2));
		}

		InodeHandle handle = getInodeHandle(name);
		if (handle.isDirectory()) {
			return (DirectoryHandle) handle;
		}
		if (handle.isLink()) {
			return ((LinkHandle) handle).getTargetDirectory();
		}
		throw new ObjectNotValidException(name + " is not a directory");
	}

	public FileHandle getFile(String name) throws FileNotFoundException,
			ObjectNotValidException {
		InodeHandle handle = getInodeHandle(name);
		if (handle.isFile()) {
			return (FileHandle) handle;
		}
		throw new ObjectNotValidException(name + " is not a file");
	}

	public LinkHandle getLink(String name) throws FileNotFoundException,
			ObjectNotValidException {
		InodeHandle handle = getInodeHandle(name);
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
		}
		FileHandle newFile = createFile(name, "drftpd", "drftpd", rslave);
		newFile.setLastModified(lrf.lastModified());
		newFile.setSize(lrf.length());
		newFile.setCheckSum(rslave.getCheckSumForPath(newFile.getPath()));
	}

	public void remerge(List<LightRemoteInode> files, RemoteSlave rslave)
			throws IOException, SlaveUnavailableException {
		Iterator<LightRemoteInode> sourceIter = files.iterator();
		// source comes pre-sorted from the slave
		List<InodeHandle> destinationList = null;
		try {
			destinationList = new ArrayList<InodeHandle>(getInodeHandles());
		} catch (FileNotFoundException e) {
			// create directory for merging
			getParent().createDirectoryForRemergeRecursive(getName());
			// lets try this again, this time, if it doesn't work, we throw an
			// IOException up the chain
			destinationList = new ArrayList<InodeHandle>(getInodeHandles());
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
			logger.debug("Starting remerge() loop, [destination="
					+ (destination == null ? "null" : destination.getName())
					+ "][source="
					+ (source == null ? "null" : source.getName()) + "]");
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
					long sourceCRC = rslave.getCheckSumForPath(getPath()
							+ VirtualFileSystem.separator + source.getName());
					long destinationCRC;
					try {
						destinationCRC = destinationFile.getCheckSum();
					} catch (NoAvailableSlaveException e) {
						destinationCRC = 0L;
					}
					if (source.length() != destinationFile.getSize()
							|| (sourceCRC != destinationCRC && destinationCRC != 0L)) {
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

	private void createDirectoryForRemergeRecursive(String name)
			throws FileExistsException, FileNotFoundException {
		DirectoryHandle dir = null;
		try {
			dir = createDirectory(name, "drftpd", "drftpd");
		} catch (FileNotFoundException e) {
			getParent().createDirectoryForRemergeRecursive(getName());
		}
		if (dir == null) {
			dir = createDirectory(name, "drftpd", "drftpd");
		}
		logger.debug("Created directory " + dir);
	}

	/**
	 * Creates a Directory object in the FileSystem with this directory as its
	 * parent
	 * 
	 * @param user
	 * @param group
	 * @return
	 * @throws FileNotFoundException
	 * @throws FileExistsException
	 */
	public DirectoryHandle createDirectory(String name, String user,
			String group) throws FileExistsException, FileNotFoundException {
		getInode().createDirectory(name, user, group);
		try {
			return getDirectory(name);
		} catch (FileNotFoundException e) {
			throw new RuntimeException(
					"somethin really funky happened, we just created it", e);
		} catch (ObjectNotValidException e) {
			throw new RuntimeException(
					"somethin really funky happened, we just created it", e);
		}
	}

	/**
	 * Creates a File object in the FileSystem with this directory as its parent
	 * 
	 */
	public FileHandle createFile(String name, String user, String group,
			RemoteSlave initialSlave) throws FileExistsException,
			FileNotFoundException {
		getInode().createFile(name, user, group, initialSlave.getName());
		try {
			return getFile(name);
		} catch (FileNotFoundException e) {
			throw new RuntimeException(
					"somethin really funky happened, we just created it", e);
		} catch (ObjectNotValidException e) {
			throw new RuntimeException(
					"somethin really funky happened, we just created it", e);
		}
	}

	/**
	 * Creates a Link object in the FileSystem with this directory as its parent
	 * 
	 */
	public LinkHandle createLink(String name, String target, String user,
			String group) throws FileExistsException, FileNotFoundException {
		getInode().createLink(name, target, user, group);
		try {
			return getLink(name);
		} catch (FileNotFoundException e) {
			throw new RuntimeException(
					"somethin really funky happened, we just created it", e);
		} catch (ObjectNotValidException e) {
			throw new RuntimeException(
					"somethin really funky happened, we just created it", e);
		}
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
		for (InodeHandle inode : getInodeHandles()) {
			inode.removeSlave(rslave);
		}
		if (isEmpty()) {
			delete();
		}
	}

	private boolean isEmpty() throws FileNotFoundException {
		return getInodeHandles().size() == 0;
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
}
