package net.sf.drftpd.remotefile;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;

import net.sf.drftpd.InvalidDirectoryException;

/**
 * @author <a href="mailto:mog@linux.nu">Morgan Christiansson</a>
 */
public class FileRemoteFile extends RemoteFileTree {
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
	public RemoteFileTree[] listFiles() {
		File filefiles[] = file.listFiles();
		RemoteFileTree files[] = new RemoteFileTree[filefiles.length];
		for (int i=0; i<filefiles.length; i++) {
			try {
			files[i] = new FileRemoteFile(root, filefiles[i]);
			} catch(IOException ex) {
				ex.printStackTrace();
			}
		}
		return files;
	}
}
