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
import java.util.Random;
import java.util.Stack;
import java.util.StringTokenizer;
import java.util.Vector;
import java.util.Collection;
/**
 * Represents the file attributes of a remote file.
 * 
 * @author Morgan Christiansson <mog@linux.nu>
 */
public class RemoteFile implements Serializable {

	/**
	 * Creates an empty RemoteFile directory, usually used as an empty root directory that
	 * <link>{merge()}</link> can be called on.
	 */
	public RemoteFile() {
		canRead = true;
		canWrite = false;
		lastModified = System.currentTimeMillis();
		length = 0;
		isDirectory = true;
		isFile = false;
		path = "/";
		files = new Hashtable();
	}

	/**
	 * Creates a RemoteFile from file.
	 * @param file file that this RemoteFile object should represent.
	 */
	public RemoteFile(RemoteSlave slave, File file) {
		canRead = file.canRead();
		canWrite = file.canWrite();
		lastModified = file.lastModified();
		length = file.length();
		//isHidden = file.isHidden();
		isDirectory = file.isDirectory();
		isFile = file.isFile();
		path = file.getPath();
		/* serialize directory*/

		slaves = new Vector(1);
		slaves.add(slave);

		if (isDirectory()) {
			/* get existing file entries */
			File cache = new File(file.getPath() + "/.drftpd");
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

			File dir[] = file.listFiles(new DrftpdFileFilter());
			files = new Hashtable(dir.length);
			Stack dirstack = new Stack();
			for (int i = 0; i < dir.length; i++) {
				File file2 = dir[i];
				System.out.println("III " + file2);
				if (file2.isDirectory()) {
					dirstack.push(file2);
					continue;
				}
				RemoteFile oldfile = null;
				if (oldtable != null)
					oldfile = (RemoteFile) oldtable.get(file.getName());
				if (oldfile != null) {
					files.put(file2.getName(), oldfile);
				} else {
					files.put(file2.getName(), new RemoteFile(slave, file2));
				}
			}

			/*
					//don't need to serialize/cache old files... we won't save any additional data about them anyway..
						try {
							new ObjectOutputStream(
								new FileOutputStream(cache)).writeObject(
								files);
						} catch (FileNotFoundException ex) {
							System.out.println("Could not open file: " + ex.getMessage());
						} catch (Exception ex) {
							ex.printStackTrace();
						}
			*/
			// OK, now the Object is saved, continue with serializing the dir's
			Enumeration e = dirstack.elements();
			while (e.hasMoreElements()) {
				File file2 = (File) e.nextElement();
				String filename = file2.getName();
				System.out.println(">>> " + file2.getName());
				if (oldtable != null) {
					RemoteFile oldfile = (RemoteFile) oldtable.get(filename);
					if (oldfile != null) {
						files.put(filename, oldfile);
					} else {
						files.put(filename, new RemoteFile(slave, file2));
					}
				} else {
					files.put(filename, new RemoteFile(slave, file2));
				}
			}
			System.out.println(
				"<<< " + getPath() + " " + files.size() + " entries");
		} /* serialize directory */
	}

	public RemoteFile[] listFiles() {
		return (RemoteFile[]) files.values().toArray(new RemoteFile[0]);
	}

	public RemoteFile lookupFile(String path) throws FileNotFoundException {
		StringTokenizer st = new StringTokenizer(path, "/");
		RemoteFile currfile = this;
		while (st.hasMoreTokens()) {
			String nextToken = st.nextToken();
			currfile = (RemoteFile) currfile.getHashtable().get(nextToken);
			if (currfile == null)
				throw new FileNotFoundException();
		}
		return currfile;
	}

	/**
	 * For compatibility with java.io.File, always returns true.
	 */
	public boolean exists() {
		return true;
	}

	private Vector slaves;
	public void addSlave(RemoteSlave slave) {
		slaves.add(slave);
	}
	public void addSlaves(Collection slaves) {
		this.slaves.addAll(slaves);
	}
	public Collection getSlaves() {
		return slaves;
	}
	private Random rand = new Random();
	public RemoteSlave getAnySlave() {
		/*if(slaves.size() == 1) {
			return (RemoteSlave)slaves.get(0);
		}
		*/
		return (RemoteSlave)slaves.get(rand.nextInt(slaves.size()));
		
	}
	public void removeSlave(RemoteSlave slave) {
		slaves.remove(slave);
	}

	private String user;
	public String getUser() {
		if (user == null) {
			return "drftpd";
		}
		return user;
	}

	private String group;
	public String getGroup() {
		if (group == null) {
			return "drftpd";
		} else {
			return group;
		}
	}

	private static final char separatorChar = '/';

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

	protected boolean isDirectory;
	public boolean isDirectory() {
		return isDirectory;
	}

	protected boolean isFile;
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

	public void merge(RemoteFile dir) {
		if (!isDirectory())
			throw new IllegalArgumentException("merge() called on a non-directory");
		if (!dir.isDirectory())
			throw new IllegalArgumentException(
				"argument is not a directory (" + dir + ")");

		//Hashtable map = getHashtable();
		Hashtable mergemap = dir.getHashtable();
		System.out.println(
			"Adding " + dir.getPath() + " with " + mergemap.size() + " files");

		Iterator i = mergemap.entrySet().iterator();
		while (i.hasNext()) {
			Map.Entry entry = (Map.Entry) i.next();
			String filename = (String) entry.getKey();
			//RemoteFile file = (RemoteFile) entry.getValue();
			RemoteFile file = (RemoteFile) files.get(filename);
			RemoteFile mergefile = (RemoteFile) entry.getValue();
			//RemoteFile mergefile = (RemoteFile) mergemap.get(getName());

			System.out.println("Adding " + mergefile.getPath());

			// two scenarios:, local file [does not] exists
			if (file == null) {
				// local file does not exist, just put it in the hashtable
				files.put(mergefile.getName(), mergefile);
			} else {
				
				if (lastModified() > mergefile.lastModified()) {
					lastModified = mergefile.lastModified();
				}

				// 4 scenarios: new/existing file/directory
				if (mergefile.isDirectory()) {
					if (!file.isDirectory())
						System.out.println(
							"!!! WARNING: File/Directory conflict!!");
					file.merge(mergefile);
				} else {
					if (file.isDirectory())
						System.out.println(
							"!!! WARNING: File/Directory conflict!!");
					addSlaves(mergefile.getSlaves());
				}
			}
		}

		// directory backdating, do other attrbiutes need "backdating"? if so fix it! :)
		if (lastModified() > dir.lastModified()) {
			lastModified = dir.lastModified();
		}
	}

	/**
	 * @see java.lang.Object#toString()
	 */
	public String toString() {
		StringBuffer ret = new StringBuffer();
		ret.append("net.sf.drftpd.RemoteFile[");
		ret.append("isDirectory(): " + isDirectory() + " ");
		if (isDirectory())
			ret.append("[directory contains " + files.size() + " files] ");
		ret.append("isFile(): " + isFile() + " ");
		ret.append(getPath());
		ret.append("]");
		return ret.toString();
	}
}
