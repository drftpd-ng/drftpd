package net.sf.drftpd.remotefile;

import java.io.File;
import java.io.IOException;

import net.sf.drftpd.InvalidDirectoryException;

/**
 * @author <a href="mailto:mog@linux.nu">Morgan Christiansson</a>
 */
public class FileRemoteFile extends RemoteFile {
	private File file;
	private String root;
	
	/**
	 * Creates 
	 * @param root
	 * @param file
	 * @throws IOException
	 */
	public FileRemoteFile(String root, File file) throws InvalidDirectoryException, IOException {
		this.root = root;
		this.file = file;
		
		isDirectory = file.isDirectory();
		isFile = file.isFile();
		if (!file.getCanonicalPath().equals(file.getAbsolutePath())) {
//			isDirectory = false;
//			isFile = false;
			System.out.println(
				"FileRemoteFile: warning: not serializing possible symlink: " + file.getAbsolutePath());
			throw new InvalidDirectoryException("Not following symlink: "+file.getAbsolutePath());
		}
	}

	/**
	 * @see net.sf.drftpd.RemoteFile#getName()
	 */
	public String getName() {
/*
		String name = file.getName();
		if(name.equals("")) name = "/";
		return name;
*/
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
	public String getOwner() {
		return "drftpd";
	}

	/**
	 * @see net.sf.drftpd.RemoteFile#isDirectory()
	 */
	public boolean isDirectory() {
		return isDirectory;
	}

	/**
	 * @see net.sf.drftpd.RemoteFile#isFile()
	 */
	public boolean isFile() {
		return isFile;
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
		if(!isDirectory()) {
			throw new IllegalArgumentException("listFiles() called on !isDirectory()");
		}
		File filefiles[] = file.listFiles();
		RemoteFile files[] = new RemoteFile[filefiles.length];
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
