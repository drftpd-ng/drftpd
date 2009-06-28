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

import java.beans.XMLDecoder;
import java.beans.XMLEncoder;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import org.apache.log4j.Logger;
import org.drftpd.io.SafeFileOutputStream;
import org.drftpd.util.CommonPluginUtils;

import se.mog.io.PermissionDeniedException;

public class VirtualFileSystem {

	protected static final InodeHandleCaseInsensitiveComparator INODE_HANDLE_CASE_INSENSITIVE_COMPARATOR = new InodeHandleCaseInsensitiveComparator();

	static class InodeHandleCaseInsensitiveComparator extends
			CaseInsensitiveComparator<InodeHandle> {

		/*
		 * (non-Javadoc)
		 * 
		 * @see org.drftpd.vfs.CaseInsensitiveComparator#compare(java.lang.Object,
		 *      java.lang.Object)
		 */
		@Override
		public int compare(Object arg0, Object arg1) {
			if (!(arg0 instanceof InodeHandle)
					|| !(arg1 instanceof InodeHandle)) {
				throw new IllegalArgumentException(
						"Can only compare classes of type InodeHandle");
			}
			InodeHandle ih0 = (InodeHandle) arg0;
			InodeHandle ih1 = (InodeHandle) arg1;
			return super.compare(ih0.getName(), ih1.getName());
		}
	}

	private static VirtualFileSystem _vfs = null;

	public static final String dirName = ".dirProperties";

	public static final String fileSystemPath = "files";

	private static final Logger logger = Logger
			.getLogger(VirtualFileSystem.class.getName());

	public static final String separator = "/";

	/**
	 * Takes /path/dir/name and returns name
	 */
	public static String getLast(String path) {
		String toReturn = path.substring(path.lastIndexOf(separator) + 1);
		if (toReturn.equals("")) {
			return separator;
		}
		return toReturn;
	}

	/**
	 * Singleton method.
	 * 
	 * @return a VirtualFileSystem object, core of the FS.
	 */
	public static VirtualFileSystem getVirtualFileSystem() {
		if (_vfs == null) {
			_vfs = new VirtualFileSystem();
			_vfs._root.commit();
		}
		return _vfs;
	}

	/**
	 * Takes /path/dir/name and returns /path/dir
	 */
	public static String stripLast(String path) {
		// logger.debug("stripLast(" + path + ")");
		if (!(path.startsWith(VirtualFileSystem.separator))) {
			throw new IllegalArgumentException(
					"stripLast() needs to be supplied with a full path, i.e, start with \"/\"");
		}
		String toReturn = path.substring(0, path.lastIndexOf(separator));
		if (toReturn.equals("")) {
			return "/";
		}
		return toReturn;
	}

	private VirtualFileSystemRoot _root = null;

	/**
	 * Create a VirtualFileSystem object, creating or not a new directory tree.
	 * If there's a pre-existing tree, it loads the data, if not, it creates a
	 * new one.<br>
	 * 
	 * <br>
	 * This constructor is private due to the Singleton architecture.
	 */
	private VirtualFileSystem() {
		new File(fileSystemPath).mkdirs();
		try {
			_root = (VirtualFileSystemRoot) loadInode(separator);
		} catch (FileNotFoundException e) {
			createRootDirectory();
		}
	}

	private VirtualFileSystemRoot createRootDirectory() {
			logger.info("Creating new root filesystem");
		logger
				.info("If you have already created your filesystem, then stop removing or corrupting your "
						+ dirName + " file!");
		new File(fileSystemPath).mkdirs();
		_root = new VirtualFileSystemRoot("drftpd", "drftpd");
		File rootFile = new File(fileSystemPath);
		Collection<String> files = new ArrayList<String>();
		Collections.addAll(files, rootFile.list());
		_root.setFiles(files);
		_root.commit();
		return _root;
	}

	/**
	 * Deletes a directory or a file from the dir tree, deleting data from the
	 * disk also.
	 * 
	 * @param path
	 */
	protected void deleteInode(String path) {
		recursiveDelete(new File(getRealPath(path)));
	}

	/**
	 * Accepts a String path that starts with "/" and walks through the
	 * structure to get it.
	 * 
	 * @param path
	 * @return the requested Inode, if it exists.
	 * @throws FileNotFoundException
	 *             if the inode doesnt exist.
	 */
	protected VirtualFileSystemInode getInodeByPath(String path)
			throws FileNotFoundException {
		if (path.startsWith("//")) {
			path = path.substring(1);
			// this is a hack, i haven't figured out the problem yet
		}
		if (path.equals(separator)) {
			return _root;
		}
		path = path.substring(1);
		VirtualFileSystemDirectory walker = _root;
		VirtualFileSystemInode inode = null;
		String[] values = path.split(separator);
		for (int x = 0; x < values.length; x++) {
			inode = walker.getInodeByName(values[x]);
			if (inode.isDirectory()) {
				walker = (VirtualFileSystemDirectory) inode;
			} else { // We better be at the end of the array
				if (x != values.length - 1) {
					// Can't get /path/name/file when /path/name is a File
					throw new FileNotFoundException("Inode does not exist");
				}
			}
		}
		// logger.debug("getInodeByPath(/" + path + ")--returning--" + inode);
		return inode;
	}

	/**
	 * @param path
	 * @return the real path of the file on the disk.<br>
	 *         Ex: getRealPath('PICS/me.jpg') would return 'files/PICS/me.jpg'
	 */
	private String getRealPath(String path) {
		return fileSystemPath + path;
	}

	/**
	 * @return the root directory.
	 */
	protected VirtualFileSystemRoot getRoot() {
		return _root;
	}

	/**
	 * Accepts a String path that starts with "/" and unserializes the requested
	 * Inode
	 */
	protected VirtualFileSystemInode loadInode(String path)
			throws FileNotFoundException {
		String fullPath = fileSystemPath + path;
		//logger.debug("Loading inode - " + fullPath);
		File xmlFile = new File(fullPath);
		File realDirectory = null;
		if (xmlFile.isDirectory()) {
			realDirectory = xmlFile;
			fullPath = fullPath + separator + dirName;
			xmlFile = new File(fullPath);
		}
		XMLDecoder xmlDec = null;
		try {
			xmlDec = new XMLDecoder(new BufferedInputStream(
					new FileInputStream(fullPath)));
			xmlDec.setExceptionListener(new VFSExceptionListener());
			ClassLoader prevCL = Thread.currentThread().getContextClassLoader();
			Thread.currentThread().setContextClassLoader(CommonPluginUtils.getClassLoaderForObject(this));
			VirtualFileSystemInode inode = (VirtualFileSystemInode) xmlDec
					.readObject();
			Thread.currentThread().setContextClassLoader(prevCL);
			inode.setName(getLast(path));
			if (inode.isDirectory()) {
				VirtualFileSystemDirectory dir = (VirtualFileSystemDirectory) inode;
				Collection<String> files = new ArrayList<String>();
				String[] list = realDirectory.list();
				for (String item : list) {
					if (item.equals(dirName)) {
						continue;
					}
					files.add(item);
				}
				dir.setFiles(files);
			}
			return inode;
		} catch (Exception e) {
			boolean corruptedXMLFile = xmlFile.exists();
			if (corruptedXMLFile) {
				// parsing error! Let's get rid of the offending bugger
				xmlFile.delete();
			}
			// if this object is the Root object, let's create it and get outta
			// here
			if (getLast(path).equals(separator)) {
				return createRootDirectory();
			}

			VirtualFileSystemDirectory parentInode = null;
			{
				VirtualFileSystemInode inode = getInodeByPath(stripLast(path));
				if (inode.isDirectory()) {
					parentInode = (VirtualFileSystemDirectory) inode;
				} else {
					// the parent is a Directory on the REAL filesystem and
					// a something else on our virtual one...
					throw new FileNotFoundException(
							"You're filesystem is really messed up");
				}
			}
			if (realDirectory != null && realDirectory.exists()) {
				// let's create the .dirProperties file from what we know since
				// it should be there
				parentInode.createDirectoryRaw(getLast(path), "drftpd",
						"drftpd");
				return parentInode.getInodeByName(getLast(path));
			}
			if (corruptedXMLFile) {
				// we already deleted the file, but we need to tell the parent
				// directory that it doesn't exist anymore
				logger
						.debug("Error loading " + fullPath + ", deleting file",
								e);
				parentInode.removeMissingChild(getLast(path));
			}
			throw new FileNotFoundException();
		} finally {
			if (xmlDec != null) {
				xmlDec.close();
			}
		}
	}

	/**
	 * If 'file' is a directory, it recurses through it and deletes, everything
	 * inside it.<br>
	 * If 'file' is an actual file, it simply deletes it.
	 * 
	 * @param file
	 */
	private void recursiveDelete(File file) {
		if (file.isDirectory()) {
			File[] files = file.listFiles();
			for (int x = 0; x < files.length; x++) {
				recursiveDelete(files[x]);
			}
		}
		if (file.exists() && !file.delete()) {
			logger.error("Could not delete local entry "
					+ file.getAbsolutePath() + ", check permissions");
		}
	}

	/**
	 * Rename the file/directory.
	 * 
	 * @param source
	 * @param destination
	 * @throws FileNotFoundException
	 *             if there's no such file/dir.
	 * @throws PermissionDeniedException
	 *             if there's no permission to rename.
	 */
	protected void renameInode(String source, String destination)
			throws FileNotFoundException, PermissionDeniedException {
		File file = new File(getRealPath(source));
		if (!file.exists()) {
			throw new FileNotFoundException(source + " cannot be found");
		}
		boolean result = file.renameTo(new File(getRealPath(destination)));
		if (!result) {
			throw new PermissionDeniedException("Cannot rename " + source
					+ " to " + destination);
		}
	}

	/**
	 * Write the Inode data to the disk.
	 * 
	 * @param inode
	 */
	protected void writeInode(VirtualFileSystemInode inode) {
		String fullPath = getRealPath(inode.getPath());
		XMLEncoder enc = null;
		try {
			if (inode instanceof VirtualFileSystemRoot) {
				new File(fileSystemPath).mkdirs();
				fullPath = fullPath + separator + dirName;
			} else if (inode.isDirectory()) {
				new File(fullPath).mkdirs();
				fullPath = fullPath + separator + dirName;
			} else {
				new File(getRealPath(inode.getParent().getPath())).mkdirs();
			}
			enc = new XMLEncoder(new BufferedOutputStream(
					new SafeFileOutputStream(fullPath)));
			inode.setupXML(enc);
			enc.setExceptionListener(new VFSExceptionListener());
			enc.writeObject(inode);
		} catch (IOException e) {
			logger.error("Unable to write " + fullPath + " to disk", e);
		} finally {
			if (enc != null) {
				enc.close();
			}
		}
		logger.debug("Wrote fullPath " + fullPath);
	}

	/**
	 * Accepts a path and makes sure it doesn't end with /, (except for Root)
	 * Example: Given "directory/subdir/file/" returns "directory/subdir/file"
	 * 
	 * @param path
	 * @return
	 */
	public static String fixPath(String path) {
		if (path.equals(VirtualFileSystem.separator)) {
			return VirtualFileSystem.separator;
		}
		if (path.endsWith(VirtualFileSystem.separator)) {
			return path.substring(0, path.length() - 1);
		}
		return path;
	}

	/*
	 * protected synchronized VirtualFileSystemInode getFile(String path) throws
	 * FileNotFoundException { WeakReference ref = _loadedFiles.get(path);
	 * VirtualFileSystemInode inode = null; if (ref == null || ref.get() ==
	 * null) { _loadedFiles.remove(path); inode = loadFile(path);
	 * _loadedFiles.put(path, new WeakReference<VirtualFileSystemInode>(inode)); }
	 * return _loadedFiles.get(path).get(); }
	 */

	/*
	 * private synchronized VirtualFileSystemInode loadFile(String path) throws
	 * FileNotFoundException { boolean isRootDirectory = !(path.equals("/") ||
	 * path.equals("")); if (isRootDirectory) { path =
	 * FtpConfig.getFtpConfig().getProperties().getProperty("filesystem.location","files") +
	 * File.pathSeparator + path; } else { path =
	 * FtpConfig.getFtpConfig().getProperties().getProperty("filesystem.root","root"); }
	 * File file = new File(path); XMLDecoder decode; try { decode = new
	 * XMLDecoder(new BufferedInputStream( new FileInputStream(file))); return
	 * (VirtualFileSystemInode) decode.readObject(); } catch
	 * (FileNotFoundException e) { if (isRootDirectory) { logger.warn("Loading
	 * new root VirtualFileSystemInode", e); return new
	 * VirtualFileSystemDirectory("/", "drftpd", "drftpd", 0); } throw e; }
	 * catch (Exception e) { logger.warn(path + " was unable to be read from
	 * file", e); file.delete(); throw new FileNotFoundException(path); } }
	 */

	/*
	 * public void createFile(String path, User user) throws FileExistsException {
	 * if (!ListUtils.isLegalFileName(path)) { throw new
	 * IllegalArgumentException("Illegal filename - " + path); }
	 * VirtualFileSystemInode vfsi = null; synchronized (this) { try { vfsi =
	 * getFile(path); throw new FileExistsException(path); } catch
	 * (FileNotFoundException e) { // This is good, continue } vfsi = new
	 * VirtualFileSystemFile(path, user.getName(), user .getGroup(), 0);
	 * _loadedFiles.put(path, new WeakReference<VirtualFileSystemInode>(
	 * vfsi)); vfsi.getParent().addChild(path); } }
	 */
}
