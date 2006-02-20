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

public class VirtualFileSystem {

	private static VirtualFileSystem _vfs = null;

	public static final String dirName = ".dirProperties";

	public static final String fileSystemPath = "files";

	private static final Logger logger = Logger
			.getLogger(VirtualFileSystem.class.getName());

	public static String pathSeparator = File.separator;

	/**
	 * Takes /path/dir/name and returns name
	 */
	public static String getLast(String path) {
		return path.substring(path.lastIndexOf(pathSeparator) + 1);
	}

	public static VirtualFileSystem getVirtualFileSystem() {
		if (_vfs == null) {
			_vfs = new VirtualFileSystem();
		}
		return _vfs;
	}

	/**
	 * Takes /path/dir/name and returns /path/dir
	 */
	public static String stripLast(String path) {
		return path.substring(0, path.lastIndexOf(pathSeparator) - 1);
	}

	private VirtualFileSystemRoot _root = null;

	private VirtualFileSystem() {
		new File(fileSystemPath).mkdirs();
		try {
			_root = (VirtualFileSystemRoot) loadInode("/");
		} catch (FileNotFoundException e) {
			logger.info("Creating new root filesystem");
			logger
					.info("If you have already created your filesystem, then stop removing your "
							+ dirName + " file!");
			_root = new VirtualFileSystemRoot("drftpd", "drftpd");
			File rootFile = new File(fileSystemPath);
			Collection<String> files = new ArrayList<String>();
			Collections.addAll(files, rootFile.list());
			_root.setFiles(files);
		}
	}

	public void deleteXML(String path) {
		recursiveDelete(new File(getRealPath(path)));
	}

	/**
	 * Accepts a String path that starts with "/" and walks through the
	 * structure to get it
	 * 
	 * @param path
	 * @return
	 * @throws FileNotFoundException
	 */
	protected VirtualFileSystemInode getInodeByPath(String path)
			throws FileNotFoundException {
		if (path == "" || path == pathSeparator) {
			return _root;
		}
		VirtualFileSystemDirectory walker = _root;
		VirtualFileSystemInode inode = null;
		String[] values = path.split(pathSeparator);
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
		return inode;
	}

	private String getRealPath(String path) {
		return fileSystemPath + path;
	}

	public VirtualFileSystemRoot getRoot() {
		return _root;
	}

	/**
	 * Accepts a String path that starts with "/" and unserializes the requested
	 * Inode
	 */
	protected VirtualFileSystemInode loadInode(String path)
			throws FileNotFoundException {
		String fullPath = fileSystemPath + path;
		logger.debug("Loading inode - " + fullPath);
		File file = new File(fullPath);
		File dirFile = null;
		if (file.isDirectory()) {
			fullPath = fullPath + pathSeparator + dirName;
			dirFile = file;
			file = new File(fullPath);
		}
		XMLDecoder xmlDec = null;
		try {
			xmlDec = new XMLDecoder(new BufferedInputStream(
					new FileInputStream(fullPath)));
			xmlDec.setExceptionListener(new VFSExceptionListener());
			VirtualFileSystemInode inode = (VirtualFileSystemInode) xmlDec
					.readObject();
			inode.setLastModified(file.lastModified());
			inode.setName(getLast(path));
			if (inode.isDirectory()) {
				VirtualFileSystemDirectory dir = (VirtualFileSystemDirectory) inode;
				Collection<String> files = new ArrayList<String>();
				String[] list = dirFile.list();
				for (String item : list) {
					if (item.equals(dirName)) {
						continue;
					}
					files.add(item);
				}
				dir.setFiles(files);
			}
			return inode;
		} finally {
			if (xmlDec != null) {
				xmlDec.close();
			}
		}
	}

	private void recursiveDelete(File file) {
		if (file.isDirectory()) {
			File[] files = file.listFiles();
			for (int x = 0; x < files.length; x++) {
				recursiveDelete(files[x]);
			}
		}
		if (!file.delete()) {
			logger.error("Could not local entry " + file.getAbsolutePath()
					+ ", check permissions");
		}
	}

	public void renameXML(String source, String destination) {
		File file = new File(getRealPath(source));
		file.renameTo(new File(getRealPath(destination)));
	}

	protected void writeInode(VirtualFileSystemInode inode) {
		String fullPath = getRealPath(inode.getPath());
		if (inode.isDirectory()) {
			new File(fullPath).mkdirs();
			fullPath = fullPath + pathSeparator + dirName;
		}
		XMLEncoder enc = null;
		new File(fileSystemPath).mkdirs();
		try {
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
