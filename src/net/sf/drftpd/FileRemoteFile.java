package net.sf.drftpd;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;

/**
 * @author mog
 *
 * To change this generated comment edit the template variable "typecomment":
 * Window>Preferences>Java>Templates.
 * To enable and disable the creation of type comments go to
 * Window>Preferences>Java>Code Generation.
 */
public class FileRemoteFile extends RemoteFile implements RemoteFileTree {
	private File file;
	private String root;

	public FileRemoteFile(String root, File file) throws IOException {
		this.root = root;
		this.file = file;

		if (!file.getCanonicalPath().equals(file.getAbsolutePath())) {
			isDirectory = false;
			System.out.println(
				"NOT following possible symlink: " + file.getAbsolutePath());
			throw new InvalidDirectoryException("Not following symlink");
		}
	}

	/**
	 * @see net.sf.drftpd.RemoteFile#getName()
	 */
	public String getName() {
		return file.getName();
	}

	/**
	 * @see net.sf.drftpd.RemoteFile#getParent()
	 */
	public String getParent() {
		return file.getParent();
	}

	/**
	 * @see net.sf.drftpd.RemoteFile#getPath()
	 */
	public String getPath() {
		return file.getPath();
	}

	/**
	 * @see net.sf.drftpd.RemoteFile#getGroup()
	 */
	public String getGroup() {
		return "drftpd";
	}

	/**
	 * @see net.sf.drftpd.RemoteFile#getUser()
	 */
	public String getUser() {
		return "drftpd";
	}

	/**
	 * @see net.sf.drftpd.RemoteFile#isDirectory()
	 */
	public boolean isDirectory() {
		return file.isDirectory();
	}

	/**
	 * @see net.sf.drftpd.RemoteFile#isFile()
	 */
	public boolean isFile() {
		return file.isFile();
	}

	/**
	 * @see net.sf.drftpd.RemoteFile#lastModified()
	 */
	public long lastModified() {
		return file.lastModified();
	}

	/**
	 * @see net.sf.drftpd.RemoteFile#length()
	 */
	public long length() {
		return super.length();
	}

	/**
	 * @see net.sf.drftpd.RemoteFileTree#listFiles()
	 */
	public RemoteFile[] listFiles() {
		File filefiles[] = file.listFiles();
		FileRemoteFile files[] = new FileRemoteFile[filefiles.length];
		System.arraycopy(filefiles,0, files, 0, filefiles.length);
		return files;
	}
}
