package net.sf.drftpd;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.Stack;
import java.util.StringTokenizer;
import java.util.Vector;

/**
 * Represents the file attributes of a remote file.
 * 
 * @author Morgan Christiansson <mog@linux.nu>
 */
public class RemoteFile implements Serializable {

	public RemoteFile[] listFiles() {
		return (RemoteFile[]) files.values().toArray(new RemoteFile[0]);
	}

	public RemoteFile lookupFile(String path) throws FileNotFoundException {
		StringTokenizer st = new StringTokenizer(path, "/");
		RemoteFile currfile;
		while (st.hasMoreTokens()) {
			String nextToken = st.nextToken();
			currfile = (RemoteFile) files.get(nextToken);
			if (currfile == null)
				throw new FileNotFoundException();
		}
		return null;
	}

	/**
	 * For compatibility with java.io.File, always returns true.
	 */
	public boolean exists() {
		return true;
	}

	private transient Vector slaves = new Vector();
	public void addSlave(RemoteSlave slave) {
		slaves.add(slave);
	}
	public void removeSlave(RemoteSlave slave) {
		slaves.remove(slave);
	}

	private String user;
	private String group;
	public String getUser() {
		if (user == null) {
			return "dftpd";
		}
		return user;
	}

	public String getGroup() {
		if (group == null) {
			return "dftpd";
		} else {
			return group;
		}
	}

	private static final char separatorChar = '/';

	/**
	 * Creates a RemoteFile from file.
	 * @param file file that this RemoteFile object should represent.
	 */
	public RemoteFile(File file) {
		canRead = file.canRead();
		canWrite = file.canWrite();
		lastModified = file.lastModified();
		length = file.length();
		//isHidden = file.isHidden();
		isDirectory = file.isDirectory();
		isFile = file.isFile();
		path = file.getPath();

		/* serialize directory*/
		if (isDirectory()) {

			/* get existing file entries */
			File cache = new File(file.getPath() + "/.dftpd");
			Hashtable oldtable = null;
			try {
				ObjectInputStream is =
					new ObjectInputStream(new FileInputStream(cache));
				oldtable = (Hashtable) is.readObject();
			} catch (FileNotFoundException ex) {
				//it's ok if it doesn't exist
			} catch (IOException ex) {
				ex.printStackTrace();
			} catch (ClassNotFoundException ex) {
				// this class must exist
				ex.printStackTrace();
				System.exit(-1);
			}
			/* END get existing file entries*/

			File dir[] = file.listFiles(new DftpdFileFilter());
			files = new Hashtable(dir.length);
			Stack dirstack = new Stack();
			for (int i = 0; i < dir.length; i++) {
				File file2 = dir[i];
				if (file2.isDirectory()) {
					dirstack.push(file2);
					continue;
				}
				RemoteFile oldfile = null;
				if (oldtable != null)
					oldfile = (RemoteFile) oldtable.get(file.getName());
				if (oldfile != null) {
					files.put(file.getName(), oldfile);
				} else {
					files.put(file.getName(), new RemoteFile(file2));
				}
			}

			try {
				new ObjectOutputStream(
					new FileOutputStream(cache)).writeObject(
					files);
			} catch (FileNotFoundException ex) {
				System.out.println("Could not open file: " + ex.getMessage());
			} catch (Exception ex) {
				ex.printStackTrace();
			}

			// OK, now the Object is saved, continue with serializing the dir's
			Enumeration e = dirstack.elements();
			while (e.hasMoreElements()) {
				RemoteFile file2 = (RemoteFile) e.nextElement();
				String filename = file2.getName();
				RemoteFile oldfile = (RemoteFile) oldtable.get(file2.getName());
				if (oldfile != null) {
					files.put(filename, oldfile);
				} else {
					files.put(filename, file2);
				}
			}
		} /* serialize directory */
	}

	private Hashtable files;
	public Hashtable getHashtable() {
		return files;
	}
	public void emptyHashtable() {
		files = null;
	}
	public void setHashtable(Hashtable map) {
		files = map;
	}

	boolean isDirectory;
	public boolean isDirectory() {
		return isDirectory;
	}

	boolean isFile;
	public boolean isFile() {
		return isFile;
	}

	//boolean isHidden;
	public boolean isHidden() {
		if (getPath().startsWith("."))
			return true;
		return false;
	}

	boolean canRead;
	public boolean canRead() {
		return canRead;
	}

	boolean canWrite;
	public boolean canWrite() {
		return canWrite;
	}

	long lastModified;
	public long lastModified() {
		return lastModified;
	}

	long length;
	public long length() {
		return length;
	}

	private String path;
	public String getName() {
		return path.substring(path.lastIndexOf(separatorChar) + 1);
	}

	public String getPath() {
		return path;
	}

	public boolean equals(Object obj) {
		if (obj instanceof RemoteFile
			&& ((RemoteFile) obj).getPath().equals(getPath())) {
			return true;
		}
		return false;
	}
	private void readObject(ObjectInputStream s)
		throws IOException, ClassNotFoundException {
		s.defaultReadObject();
		slaves = new Vector();
	}

	public void merge(RemoteFile dir) {
		if (!isDirectory())
			throw new IllegalArgumentException("merge() called on a non-directory");
		if (!dir.isDirectory())
			throw new IllegalArgumentException("argument is not a directory");

		Hashtable srcmap = getHashtable();
		Hashtable dstmap = dir.getHashtable();

		Iterator i = srcmap.entrySet().iterator();
		while (i.hasNext()) {
			//Map.Entry entry = (Map.Entry)i.next());
			//RemoteFile srcfile = (RemoteFile)entry.getValue();
			RemoteFile srcfile = (RemoteFile) ((Map.Entry) i.next()).getValue();
			RemoteFile dstfile = (RemoteFile) dstmap.get(getName());
			if (srcfile.isDirectory()) {
				//let mergeRoot() merge fromfile's hashtable to tofile's hashtable
				//and put the reference mergeRoot() returns back into the table
				dstmap.put(
					srcfile.getName(),
					null		//mergeRoot(slave, srcfile, dstfile));
				);
			}

			if (dstfile == null) {
				// dstfile has no entry for this file, addSlave() and add to dstmap
				//srcfile.addSlave(slave);
				dstmap.put(srcfile.getName(), srcfile);
			} else {
				// file backdating

				// dstfile exists, if we're adding an older srcfile,
				// replace the RemoteFile and put it in the Hashtable and keep looping
				if (srcfile.lastModified() > dstfile.lastModified()) {
					srcfile.getHashtable().putAll(dstfile.getHashtable());
					dstmap.put(srcfile.getName(), srcfile);
				} else {
					//just add the remoteslave to the target remotefile
					//dstfile.addSlave(slave);
				}
				//dst.addSlave(slave);
			}
		}

		// directory backdating
		if (lastModified() > dir.lastModified()) {
			lastModified = dir.lastModified();
		}
	}
}
