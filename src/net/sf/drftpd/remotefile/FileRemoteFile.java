package net.sf.drftpd.remotefile;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.Vector;

import net.sf.drftpd.InvalidDirectoryException;
import net.sf.drftpd.slave.RootBasket;

/**
 * A wrapper for java.io.File to the net.sf.drftpd.RemoteFile structure.
 * 
 * @author <a href="mailto:mog@linux.nu">Morgan Christiansson</a>
 */
public class FileRemoteFile extends RemoteFile {
//	private File file;
//	private String root;

	/**
	 * Creates 
	 * @param root
	 * @param file
	 * @throws IOException
	 */

//	public FileRemoteFile(String root, File file) throws IOException {
//		this.root = root;
//		this.file = file;
//
//		if (!file.getCanonicalPath().equals(file.getAbsolutePath())) {
//			System.out.println(
//				"FileRemoteFile: warning: not serializing possible symlink: "
//					+ file.getAbsolutePath());
//			throw new InvalidDirectoryException(
//				"Not following symlink: " + file.getAbsolutePath());
//		}
//	}
	
	RootBasket rootBasket;
	String path;
	public FileRemoteFile(RootBasket rootBasket) throws IOException {
		this(rootBasket, "");
	}
	
	public FileRemoteFile(RootBasket rootBasket, String path) throws IOException {
		this.path = path;
		this.rootBasket = rootBasket;

		//check that the roots in the rootBasket are in sync
		boolean first = true;
		for (Iterator iter = rootBasket.iterator(); iter.hasNext();) {
			File root = (File) iter.next();
			File file = new File(root.getPath()+"/"+path);
			System.out.println("File: "+file);
			
			if (!file.exists()) continue;

			if(first) {
				isDirectory = file.isDirectory();
			} else {
				if(file.isDirectory() != isDirectory) throw new IOException("rootBasket out of sync");
			}
					
			if (!file.getCanonicalPath().equals(file.getAbsolutePath())) {
				System.out.println(
					"FileRemoteFile: warning: not serializing possible symlink: "
						+ file.getAbsolutePath());
				throw new InvalidDirectoryException(
					"Not following symlink: " + file.getAbsolutePath());
			}
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
		throw new NoSuchMethodError();
		//return file.getName();
	}

	/**
	 * @see net.sf.drftpd.RemoteFile#getParent()
	 */
	public String getParent() {
		throw new NoSuchMethodError();
		//return file.getParent();
	}

	/**
	 * @see net.sf.drftpd.RemoteFile#getPath()
	 */
	public String getPath() {
		throw new NoSuchMethodError();
		//return file.getPath();
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
		return file.length();
	}

	/**
	 * Returns an array of FileRemoteFile:s representing the contents of the directory this FileRemoteFile represents.
	 * @see net.sf.drftpd.RemoteFileTree#listFiles()
	 */
	public RemoteFile[] listFiles() {
		if (!isDirectory()) {
			throw new IllegalArgumentException("listFiles() called on !isDirectory()");
		}
		Vector filefiles = new Vector();
		for (Iterator iter = rootBasket.iterator(); iter.hasNext();) {
			File root = (File) iter.next();
			File file = new File(root+"/"+path);
			File tmpFiles[] = file.listFiles();
			System.out.println("Capacity 1: size: "+filefiles.size()+" capacity: "+filefiles.capacity()+" add: "+tmpFiles.length);
			filefiles.ensureCapacity(filefiles.size()+tmpFiles.length);
			System.out.println("Capacity 2: size: "+filefiles.size()+" capacity: "+filefiles.capacity());
			for (int i = 0; i < tmpFiles.length; i++) {
				try {
					filefiles.add(new FileRemoteFile(rootBasket, path+tmpFiles[i].getName()));
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		
//		File filefiles[] = file.listFiles();
//		RemoteFile files[] = new RemoteFile[filefiles.length];
//		for (int i = 0; i < filefiles.length; i++) {
//			try {
//				files[i] = new FileRemoteFile(root, filefiles[i]);
//			} catch (IOException ex) {
//				ex.printStackTrace();
//			}
//		}
		return (RemoteFile[]) filefiles.toArray(new FileRemoteFile[filefiles.size()]);
	}
}
