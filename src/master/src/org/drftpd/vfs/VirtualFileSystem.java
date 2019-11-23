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

import com.cedarsoftware.util.io.JsonIoException;
import com.cedarsoftware.util.io.JsonReader;
import com.cedarsoftware.util.io.JsonWriter;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.drftpd.GlobalContext;
import org.drftpd.io.PermissionDeniedException;
import org.drftpd.io.SafeFileOutputStream;
import org.drftpd.vfs.event.*;

import java.io.*;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class VirtualFileSystem {

	protected static final InodeHandleCaseInsensitiveComparator INODE_HANDLE_CASE_INSENSITIVE_COMPARATOR = 
		new InodeHandleCaseInsensitiveComparator();

	static class InodeHandleCaseInsensitiveComparator implements Comparator<InodeHandle> {

		public int compare(InodeHandle inode0, InodeHandle inode1) {
			return String.CASE_INSENSITIVE_ORDER.compare(inode0.getName(), inode1.getName());
		}
	}

	static class DirInodeFilenameFilter implements FilenameFilter {

		@Override
		public boolean accept(File dir, String file) {
			return !file.equals(dirName);
		}
		
	}

	private static VirtualFileSystem _vfs = null;

	public static final String dirName = ".dirProperties";

	public static final String fileSystemPath = "userdata/vfs";

	private static final Logger logger = LogManager.getLogger(VirtualFileSystem.class);

	private static final DirInodeFilenameFilter dirFilter = new DirInodeFilenameFilter();

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
		logger.info("If you have already created your filesystem, then stop removing or corrupting your "
						+ dirName + " file!");
		new File(fileSystemPath).mkdirs();
		_root = new VirtualFileSystemRoot("drftpd", "drftpd");
		File rootFile = new File(fileSystemPath);
		_root.setFiles(rootFile.list(dirFilter));
		_root.commit();
		_root.inodeLoadCompleted();
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
			} else if (inode.isLink() && (x != values.length - 1)) {
				walker = (VirtualFileSystemDirectory) getInodeByPath(((VirtualFileSystemLink)inode).getLinkPath());
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
        logger.debug("Loading inode - {}", fullPath);
		File jsonFile = new File(fullPath);
		File realDirectory = null;
		if (jsonFile.isDirectory()) {
			realDirectory = jsonFile;
			fullPath = fullPath + separator + dirName;
			jsonFile = new File(fullPath);
		}
		try (InputStream in = new FileInputStream(fullPath);
			 JsonReader reader = new JsonReader(in)) {
			VirtualFileSystemInode inode = (VirtualFileSystemInode) reader.readObject();
			inode.setName(getLast(path));
			if (inode.isDirectory()) {
				VirtualFileSystemDirectory dir = (VirtualFileSystemDirectory) inode;
				dir.setFiles(realDirectory.list(dirFilter));
			}
			inode.inodeLoadCompleted();
			return inode;
		} catch (Exception e) {
			boolean corruptedJsonFile = jsonFile.exists();
			if (corruptedJsonFile) {
				// parsing error! Let's get rid of the offending bugger
				jsonFile.delete();
			}
			// if this object is the Root object, let's create it and get outta
			// here
			if (getLast(path).equals(separator)) {
				return createRootDirectory();
			}

			VirtualFileSystemDirectory parentInode;
			{
				VirtualFileSystemInode inode = getInodeByPath(stripLast(path));
				if (inode.isDirectory()) {
					parentInode = (VirtualFileSystemDirectory) inode;
				} else {
					// the parent is a Directory on the REAL filesystem and
					// a something else on our virtual one...
					throw new FileNotFoundException("You're filesystem is really messed up");
				}
			}
			if (realDirectory != null && realDirectory.exists()) {
				// let's create the .dirProperties file from what we know since
				// it should be there
				parentInode.createDirectoryRaw(getLast(path), "drftpd", "drftpd");
				return parentInode.getInodeByName(getLast(path));
			}
			if (corruptedJsonFile) {
				// we already deleted the file, but we need to tell the parent
				// directory that it doesn't exist anymore
                logger.debug("Error loading {}, deleting file", fullPath, e);
				parentInode.removeMissingChild(getLast(path));
			}
			throw new FileNotFoundException();
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
            for (File file1 : files) {
                recursiveDelete(file1);
            }
		}
		if (file.exists() && !file.delete()) {
            logger.error("Could not delete local entry {}, check permissions", file.getAbsolutePath());
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
		if (inode instanceof VirtualFileSystemRoot) {
			new File(fileSystemPath).mkdirs();
			fullPath = fullPath + separator + dirName;
		} else if (inode.isDirectory()) {
			new File(fullPath).mkdirs();
			fullPath = fullPath + separator + dirName;
		} else {
			new File(getRealPath(inode.getParent().getPath())).mkdirs();
		}
		Map<String,Object> params = new HashMap<>();
		params.put(JsonWriter.PRETTY_PRINT, true);
		try (OutputStream out = new SafeFileOutputStream(fullPath);
			 JsonWriter writer = new JsonWriter(out, params)) {
			writer.write(inode);
            logger.debug("Wrote fullPath {}", fullPath);
		} catch (IOException | JsonIoException e) {
            logger.error("Unable to write {} to disk", fullPath, e);
		}
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

	protected void notifyOwnershipChanged(VirtualFileSystemInode inode, String owner, String group) {
        logger.debug("Notifying that ownership of {} has changed to: {}/{}", inode.getPath(), owner, group);

		publishAsyncEvent(new VirtualFileSystemOwnershipEvent(inode, inode.getPath(), owner, group));
	}
	
	protected void notifySlavesChanged(VirtualFileSystemFile inode, Set<String> slaves) {
        logger.debug("Notifying the list of slaves of {} has changed to: {}", inode.getPath(), slaves);
		
		publishAsyncEvent(new VirtualFileSystemSlaveEvent(inode, inode.getPath(), slaves));
	}
	
	protected void notifyInodeRenamed(String sourcePath, VirtualFileSystemInode destination) {
        logger.debug("Notifying that {} has been renamed to {}", sourcePath, destination.getPath());

		publishAsyncEvent(new VirtualFileSystemRenameEvent(sourcePath, destination, destination.getPath()));
	}
	
	protected void notifyInodeCreated(VirtualFileSystemInode inode) {
        logger.debug("Notifying that {} has been created", inode.getPath());

		publishAsyncEvent(new VirtualFileSystemInodeCreatedEvent(inode, inode.getPath()));
	}
	
	protected void notifyInodeDeleted(VirtualFileSystemInode inode, String path) {
        logger.debug("Notifying that {} has been deleted", path);

		publishAsyncEvent(new VirtualFileSystemInodeDeletedEvent(inode, path));
	}

	protected void notifySizeChanged(VirtualFileSystemInode inode, long size) {
        logger.debug("Notifying that the size of {} has changed to: {}", inode.getPath(), size);

		publishAsyncEvent(new VirtualFileSystemSizeEvent(inode, inode.getPath(), size));
	}

	protected void notifyLastModifiedChanged(VirtualFileSystemInode inode, long lastmodified) {
        logger.debug("Notifying that the last modified timestamp of {} has changed to: {}", inode.getPath(), lastmodified);

		publishAsyncEvent(new VirtualFileSystemLastModifiedEvent(inode, inode.getPath(), lastmodified));
	}
	
	protected void notifyInodeRefresh(VirtualFileSystemInode inode, boolean sync) {
        logger.debug("Notifying that a refresh has been requested for {}", inode.getPath());

		if (sync) {
			publishSyncEvent(new VirtualFileSystemInodeRefreshEvent(inode, inode.getPath()));
		} else {
			publishAsyncEvent(new VirtualFileSystemInodeRefreshEvent(inode, inode.getPath()));
		}
	}
	
	private void publishAsyncEvent(VirtualFileSystemEvent event) {
		GlobalContext.getEventService().publishAsync(event);
	}
	
	private void publishSyncEvent(VirtualFileSystemEvent event) {
		GlobalContext.getEventService().publish(event);
	}
}
