package net.sf.drftpd.remotefile;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Hashtable;
import java.util.Iterator;

import org.apache.log4j.Logger;

import net.sf.drftpd.FatalException;
import net.sf.drftpd.InvalidDirectoryException;
import net.sf.drftpd.slave.Root;
import net.sf.drftpd.slave.RootBasket;

/**
 * A wrapper for java.io.File to the net.sf.drftpd.RemoteFile structure.
 * 
 * @author <a href="mailto:drftpd@mog.se">Morgan Christiansson</a>
 */
public class FileRemoteFile extends RemoteFile {
	private static Logger logger = Logger.getLogger(FileRemoteFile.class.getName());
//	private File file;
//	private String root;

	RootBasket rootBasket;
	String path;

	public FileRemoteFile(RootBasket rootBasket) throws IOException {
		this(rootBasket, "");
	}
	private boolean isFile;
	private boolean isDirectory;
	private long length;

	public FileRemoteFile(RootBasket rootBasket, String path) throws IOException {
		//if(path.length() != 0) {
		//	if(path.charAt(path.length()-1) == File.separatorChar) path = path.substring(0, path.length()-1);
		//}
		this.path = path;
		this.rootBasket = rootBasket;
		//this.slaves = slaves;

		//check that the roots in the rootBasket are in sync
		boolean first = true;
		for (Iterator iter = rootBasket.iterator(); iter.hasNext();) {
			Root root = (Root)iter.next();
			//File rootFile = root.getFile();
			File file = new File(root.getPath()+"/"+path);
			//System.out.println("File: "+file);
			
			if (!file.exists()) continue;

//			if(file.isDirectory() && file.list().length == 0) {
//				while(file.list().length == 0) {
//					File file2 = file.getParentFile();
//					file.delete();
//					file = file2;
//				}
//				continue;
//			}
			
			if(first) {
				first=false;
				isDirectory = file.isDirectory();
				isFile = file.isFile();
				if((!isFile() && !isDirectory()) || (isFile() && isDirectory)) {
					throw new IOException("(!isFile() && !isDirectory()) || (isFile() && isDirectory): "+isFile()+isDirectory()+" "+path);
				}
				if(isDirectory()) {
					length = 0; 
				} else {
					length = file.length();
				} 
			} else {
				if(file.isDirectory() != isDirectory) throw new IOException("roots are out of sync, dir&file mix: "+path);
				if(file.isFile() != isFile) throw new IOException("roots are out of sync, file&dir mix: "+path);
				if(isFile() && file.length() != length) throw new IOException("roots are out of sync, different sizes: "+path);
			}
					
			if (!file.getCanonicalPath().equalsIgnoreCase(file.getAbsolutePath())) {
				System.out.println(
					"FileRemoteFile: warning: not following possible symlink: "
						+ file.getAbsolutePath());
				throw new InvalidDirectoryException(
					"Not following symlink: " + file.getAbsolutePath());
			}
		}
	}
	private File getFile() {
		try {
			return rootBasket.getFile(getPath());
		} catch(FileNotFoundException ex) {
			throw new RuntimeException(ex); 
		}
	}
	/**
	 * @see net.sf.drftpd.RemoteFile#getName()
	 */
	public String getName() {
		return path.substring(path.lastIndexOf(File.separatorChar)+1);
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
		return path;
		//throw new NoSuchMethodError();
		//return file.getPath();
	}

	/**
	 * @see net.sf.drftpd.RemoteFile#getGroup()
	 */
	public String getGroupname() {
		return "drftpd";
	}

	/**
	 * @see net.sf.drftpd.RemoteFile#getUser()
	 */
	public String getUsername() {
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
		return this.getFile().lastModified();
	}

	/**
	 * @see net.sf.drftpd.RemoteFile#length()
	 */
	public long length() {
		if(isDirectory()) {
			buildFileFiles();
			return filefiles.size();
		}
		return this.getFile().length();
	}

	/**
	 * Returns an array of FileRemoteFile:s representing the contents of the directory this FileRemoteFile represents.
	 * @see net.sf.drftpd.RemoteFileTree#listFiles()
	 */
	public RemoteFileInterface[] listFiles() {
		return (RemoteFileInterface[]) getFiles().toArray(new FileRemoteFile[0]);
	}
	
	Hashtable filefiles;
	private void buildFileFiles() {
		if(filefiles != null) return;
		filefiles = new Hashtable();
		
		if (!isDirectory()) {
			throw new IllegalArgumentException("listFiles() called on !isDirectory()");
		}
		for (Iterator iter = rootBasket.iterator(); iter.hasNext();) {
			Root root = (Root)iter.next();
			File file = new File(root.getPath()+"/"+path);
			if(!file.exists()) continue;
			if(!file.isDirectory()) throw new FatalException(file.getPath()+" is not a directory, attempt to getFiles() on it");
			if(!file.canRead()) throw new FatalException("Cannot read: "+file);
			File tmpFiles[] = file.listFiles(); //returns null if not a dir, blah!
			if(tmpFiles == null) throw new NullPointerException("list() on "+file+" returned null");
			for (int i = 0; i < tmpFiles.length; i++) {
				try {
					if(tmpFiles[i].isDirectory() && isEmptyDeleted(tmpFiles[i])) {
						continue;
					}
					FileRemoteFile listfile = new FileRemoteFile(rootBasket, path+File.separatorChar+tmpFiles[i].getName());
					filefiles.put(tmpFiles[i].getName(), listfile);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		assert !filefiles.isEmpty();
	}
	/**
	 * @see net.sf.drftpd.remotefile.RemoteFile#getFiles()
	 */
	public Collection getFiles() {
		buildFileFiles();
		return filefiles.values();
	}

	/**
	 * @return true if directory contained no files and is now deleted, false otherwise.
	 */
	private static boolean isEmptyDeleted(File dir) {
		File listfiles[] = dir.listFiles();
		if(listfiles == null) throw new FatalException("Not a directory or IO error: "+dir);
		for (int i = 0; i < listfiles.length; i++) {
			File file = listfiles[i];
			if(file.isFile()) return false;
		}
		
		for (int i = 0; i < listfiles.length; i++) {
			File file = listfiles[i];
			// parent directory not empty
			if(!isEmptyDeleted(file)) return false;
		}
		dir.delete();
		return true;
	}
	
	/* (non-Javadoc)
	 * @see net.sf.drftpd.remotefile.RemoteFile#getSlaves()
	 */
	public Collection getSlaves() {
		return new ArrayList();
	}


}
