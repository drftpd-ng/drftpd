/**
 * 
 */
package org.drftpd.remotefile;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.apache.log4j.Logger;
import org.drftpd.io.SafeFileOutputStream;

/**
 * @author zubov
 *
 */
public class FileSystemFileLoader implements FileLoader {
	private static String _inodeRoot = "files";
    private static final Logger logger = Logger.getLogger(FileSystemFileLoader.class);
	private static final String directoryExtension = ".dir";
	/**
	 * Stores the filesystem in the master's filesystem
	 */
	public FileSystemFileLoader() {
		super();
		new java.io.File(_inodeRoot).mkdirs();
	}

	public Inode getInode(String path) throws FileNotFoundException,
			IOException {
		java.io.File realFile = setupFile(path);
		ObjectInputStream ois = null;
		try {
			ois = new ObjectInputStream(new FileInputStream(realFile));
			return (Inode) ois.readObject();
		} catch (ClassNotFoundException e) {
			realFile.delete();
			logger.error("Inode is corrupt, deleting -- " + path);
			throw new FileNotFoundException("File is corrupt");
		} finally {
			if (ois != null) {
				ois.close();
			}
		}
	}
	
	private File setupFile(String path) {
		path = fixPathSeparator(path);
		java.io.File realFile = new java.io.File(_inodeRoot + path);
		if (realFile.isDirectory()) {
			realFile = new java.io.File(_inodeRoot + path + directoryExtension);
		}
		return realFile;
	}

	private String fixPathSeparator(String path) {
		return path.replaceAll(FileManager.separator, java.io.File.pathSeparator);
	}

	public void writeInode(Inode inode) {
		ObjectOutputStream oos = null;
		java.io.File realFile = setupFile(inode.getPath());
		try {
			oos = new ObjectOutputStream(new SafeFileOutputStream(realFile));
			oos.writeObject(inode);
		} catch (IOException e) {
			logger.fatal("Error writing " + inode.getPath() + " to disk", e);
		} finally {
			if (oos != null) {
				try {
					oos.close();
				} catch (IOException e) {
				}
			}
		}
 	}
}
