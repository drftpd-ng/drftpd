package net.sf.drftpd.remotefile;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Serializable;
import java.rmi.RemoteException;
import java.util.Collection;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Stack;
import java.util.StringTokenizer;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.sf.drftpd.FileExistsException;
import net.sf.drftpd.InvalidDirectoryException;
import net.sf.drftpd.SFVFile;
import net.sf.drftpd.master.NoAvailableSlaveException;
import net.sf.drftpd.master.usermanager.User;
import net.sf.drftpd.slave.RemoteSlave;

/**
 * Represents the file attributes of a remote file.
 * 
 * @author Morgan Christiansson <mog@linux.nu>
 */
public class LinkedRemoteFile extends RemoteFile implements Serializable {

	private static Logger logger = Logger.getLogger(LinkedRemoteFile.class.getName());
	static {
		logger.setLevel(Level.FINE);
	}
	//private static Category logger = Category.getInstance(LinkedRemoteFile.class);
	/**
	 * @author mog
	 *
	 * To change this generated comment edit the template variable "typecomment":
	 * Window>Preferences>Java>Templates.
	 * To enable and disable the creation of type comments go to
	 * Window>Preferences>Java>Code Generation.
	 */
	public class DirectoryRemoteFile extends RemoteFile {

		public DirectoryRemoteFile(User owner, String name) {
			this.name = name;
			isDirectory = true;
			isFile = false;
			lastModified = System.currentTimeMillis();
			//canWrite = true;
			//canRead = true;
			user = owner.getUsername();
			group = owner.getGroup();
		}
		/**
		 * @see net.sf.drftpd.remotefile.RemoteFileTree#listFiles()
		 */
		public RemoteFile[] listFiles() {
			return new RemoteFile[0];
		}

		protected String name;
		/**
		 * @see net.sf.drftpd.remotefile.RemoteFile#getName()
		 */
		public String getName() {
			return name;
		}

		/**
		 * @see net.sf.drftpd.remotefile.RemoteFile#getParent()
		 */
		public String getParent() {
			throw new UnsupportedOperationException("getParent() is not implemented on LinkedRemoteFile.DirectoryRemoteFile");
		}

		/**
		 * @see net.sf.drftpd.remotefile.RemoteFile#getPath()
		 */
		public String getPath() {
			throw new UnsupportedOperationException("getParent() is not implemented on LinkedRemoteFile.DirectoryRemoteFile");
		}

	}

	/**
	 * Creates an empty RemoteFile directory, usually used as an empty root directory that
	 * <link>{merge()}</link> can be called on.
	 */
	public LinkedRemoteFile() {
		//		canRead = true;
		//		canWrite = false;
		lastModified = System.currentTimeMillis();
		length = 0;
		isDirectory = true;
		isFile = false;
		parent = null;
		name = "";
		files = new Hashtable();
		slaves = new Vector();
		// there _should_ always be at least 1 RemoteSlave.
	}

	/**
	 * The slave argument may be null, if it is null, no slaves will be added.
	 */
	public LinkedRemoteFile(RemoteSlave slave, RemoteFile file)
		throws IOException {
		this(slave, null, file);
	}

	public LinkedRemoteFile(RemoteFile file) throws IOException {
		this(null, null, file);
	}

	/**
	 * Creates a RemoteFile from file or creates a directory tree representation.
	 * @param file file that this RemoteFile object should represent.
	 */
	public LinkedRemoteFile(
		RemoteSlave slave,
		LinkedRemoteFile parent,
		RemoteFile file)
		throws InvalidDirectoryException {
		logger.finest("creating linkedRemoteFile from " + file);
		lastModified = file.lastModified();
		length = file.length();
		//isHidden = file.isHidden();
		isDirectory = file.isDirectory();
		isFile = file.isFile();

		checkSum = file.getCheckSum();
		
		if (parent == null) {
			name = "";
		} else {
			name = file.getName();
		}
		//path = file.getPath();
		/* serialize directory*/
		this.parent = parent;

		slaves = new Vector();
		if (slave != null) {
			slaves.add(slave);
		}

		if (file.isDirectory()) {
			System.out.println("is a directory, serializing contents.");
			RemoteFile dir[] = file.listFiles();
			files = new Hashtable(dir.length);
			Stack dirstack = new Stack();
			for (int i = 0; i < dir.length; i++) {
				RemoteFile file2 = dir[i];
				if (file2.isDirectory()) {
					dirstack.push(file2);
					continue;
				}
				files.put(
					file2.getName(),
					new LinkedRemoteFile(slave, this, file2));
			}

			Iterator i = dirstack.iterator();
			while (i.hasNext()) {
				RemoteFile file2 = (RemoteFile) i.next();
				String filename = file2.getName();
				files.put(filename, new LinkedRemoteFile(slave, this, file2));
			}
		}
	}

	public void mkdir(User owner, String fileName) throws IOException {
		LinkedRemoteFile existingfile = (LinkedRemoteFile) files.get(fileName);
		if (existingfile != null) {
			throw new FileExistsException(
				fileName + " already exists in this directory");
		}
		for (Iterator i = slaves.iterator(); i.hasNext();) {
			RemoteSlave slave = (RemoteSlave) i.next();
			try {
				slave.getSlave().mkdir(owner, getPath() + "/" + fileName);
			} catch (RemoteException ex) {
				slave.getManager().handleRemoteException(ex, slave);
			}
		}

		LinkedRemoteFile file =
			new LinkedRemoteFile(
				null, // remoteslave
				this, // parent
				new DirectoryRemoteFile(owner, fileName)); //file
		file.addSlaves(getSlaves());
		files.put(file.getName(), file);
		logger.fine("Created directory " + file.getPath());
	}

	public void addFile(RemoteFile file) throws InvalidDirectoryException {
		LinkedRemoteFile linkedfile = new LinkedRemoteFile(null, this, file);
		files.put(linkedfile.getName(), linkedfile);
	}

	public Map getFiles() {
		return files;
	}

	public RemoteFile[] listFiles() {
		if (files == null) {
			System.out.println(
				"Warning: attempt to listFiles() on a null files map:");
			System.out.println(this);
			return new LinkedRemoteFile[0];
		}
		return (LinkedRemoteFile[]) files.values().toArray(
			new LinkedRemoteFile[0]);
	}

	/**
	 * Looks up the absolute path 'path', with this directory as directory root.
	 * 
	 * A leading / is ignored as this is the root, pathname cannot contain ".." or similair entries.
	 */
	public LinkedRemoteFile lookupFile(String path)
		throws FileNotFoundException {
		StringTokenizer st = new StringTokenizer(path, "/");
		LinkedRemoteFile currfile = this;
		while (st.hasMoreTokens()) {
			String nextToken = st.nextToken();
			currfile =
				(LinkedRemoteFile) currfile.getHashtable().get(nextToken);
			if (currfile == null)
				throw new FileNotFoundException();
		}
		return currfile;
	}

	/**
	 * A remote file never exists locally, therefore we return false.
	 * If the current RemoteSlave has the file this call could return true
	 * but as we don't know the root directory it's not possible right now.
	 */
	public boolean exists() {
		return false;
	}

	private Map files;
	public Map getHashtable() {
		return files;
	}

	private LinkedRemoteFile parent;
	public LinkedRemoteFile getParentFile() {
		return parent;
	}
	public String getParent() {
		if (getParentFile() == null)
			return null;
		return getParentFile().getPath();
	}

	//private String path;
	private String root;
	private String name;
	public String getName() {
		//return path.substring(path.lastIndexOf(separatorChar) + 1);
		return name;
	}

	public String getPath() {
		//return path;
		StringBuffer path = new StringBuffer("/" + getName());
		LinkedRemoteFile parent = getParentFile();
		while (parent != null && parent.getParentFile() != null) {
			path.insert(0, "/" + parent.getName());
			parent = parent.getParentFile();
		}
		return path.toString();
	}

	public boolean equals(Object obj) {
		if (obj instanceof LinkedRemoteFile
			&& ((LinkedRemoteFile) obj).getPath().equals(getPath())) {
			return true;
		}
		return false;
	}

	/**
	 * Merges two RemoteFile directories.
	 * If duplicates exist, the slaves are added to this object and the file-attributes of the oldest file (lastModified) are kept.
	 */
	public synchronized void merge(LinkedRemoteFile dir) {
		System.out.println("merge(): " + this +" and " + dir);
		if (!isDirectory())
			throw new IllegalArgumentException(
				"merge() called on a non-directory: "
					+ this
					+ " argument: "
					+ dir);
		if (!dir.isDirectory()) {
			System.out.println(dir.getPath());
			System.out.println(dir.getFiles());
			throw new IllegalArgumentException(
				"argument is not a directory: " + dir + " this: " + this);
		}
		Map map = getHashtable();
		Map mergemap = dir.getHashtable();
		if (mergemap == null)
			return;
		// remote directory wasn't added, it might have been a symlink.

		System.out.println(
			"Adding " + dir.getPath() + " with " + mergemap.size() + " files");

		Iterator i = mergemap.entrySet().iterator();
		while (i.hasNext()) {
			Map.Entry entry = (Map.Entry) i.next();
			String filename = (String) entry.getKey();
			//RemoteFile file = (RemoteFile) entry.getValue();
			LinkedRemoteFile file = (LinkedRemoteFile) files.get(filename);
			LinkedRemoteFile mergefile = (LinkedRemoteFile) entry.getValue();
			//RemoteFile mergefile = (RemoteFile) mergemap.get(getName());

			// two scenarios:, local file [does not] exists
			if (file == null) {
				// local file does not exist, just put it in the hashtable
				map.put(mergefile.getName(), mergefile);
			} else {

				if (lastModified() > mergefile.lastModified()) {
					System.out.println(
						"Last modified changed from "
							+ lastModified()
							+ " to "
							+ mergefile.lastModified());
					lastModified = mergefile.lastModified();
				} else {
					System.out.println(
						"Last modified NOT changed from "
							+ lastModified()
							+ " to "
							+ mergefile.lastModified());
				}

				// 4 scenarios: new/existing file/directory
				if (mergefile.isDirectory()) {
					if (!file.isDirectory())
						throw new RuntimeException("!!! WARNING: Directory/File conflict!!");
					// is a directory -- dive into directory and start merging
					file.merge(mergefile);
				} else {
					if (file.isDirectory())
						throw new RuntimeException("!!! WARNING: File/Directory conflict!!");
				}

				// in all cases we add the slaves of the remote file to 'this' file
				Collection slaves2 = mergefile.getSlaves();
				file.addSlaves(slaves2);
				System.out.println("Result file: " + file);
			}
		}

		// directory backdating, do other attrbiutes need "backdating"? if so fix it! :)
		if (lastModified() > dir.lastModified()) {
			lastModified = dir.lastModified();
		}
	}

	public void unmerge(RemoteSlave slave) {
		//LinkedRemoteFile files[] = listFiles();
		if (!getSlaves().remove(slave)) {
			System.out.println("Slave already removed from " + this);
		}
		recursiveUmerge(slave);
	}

	public void unmerge(RemoteSlave slave, Iterator i) {
		i.remove();
		recursiveUmerge(slave);
	}

	private void recursiveUmerge(RemoteSlave slave) {
		if (files == null)
			return;
		for (Iterator i = files.entrySet().iterator(); i.hasNext();) {
			Map.Entry entry = (Map.Entry) i.next();
			LinkedRemoteFile file = (LinkedRemoteFile) entry.getValue();
			String filename = (String) entry.getKey();
			if (file.isDirectory()) {
				file.unmerge(slave);
				if (file.listFiles().length == 0)
					i.remove();
			} else {
				if (file.getSlaves().remove(slave))
					System.out.println("Removed slave from " + file);
			}
		}
	}

	/////////////////////// SLAVES
	protected List slaves;
	public void addSlave(RemoteSlave slave) {
		slaves.add(slave);
	}
	private void addSlaves(Collection addslaves) {
		if (addslaves == null)
			throw new IllegalArgumentException("addslaves cannot be null");
		System.out.println("Adding " + addslaves + " to " + slaves);
		slaves.addAll(addslaves);
		System.out.println("slaves.size() is now " + slaves.size());
	}
	public List getSlaves() {
		return slaves;
	}
	private Random rand = new Random();
	public RemoteSlave getASlave() throws NoAvailableSlaveException {
		if (slaves.size() == 0)
			throw new NoAvailableSlaveException(
				getPath() + " has no slaves available");
		RemoteSlave myslaves[] =
			(RemoteSlave[]) slaves.toArray(new RemoteSlave[0]);
		int num = rand.nextInt(myslaves.length);
		return (RemoteSlave) myslaves[num];
	}

	public void removeSlave(RemoteSlave slave) {
		slaves.remove(slave);
	}
	/**
	 * @see java.lang.Object#toString()
	 */
	public String toString() {
		StringBuffer ret = new StringBuffer();
		ret.append("[net.sf.drftpd.RemoteFile[\"" + getPath() + "\"");
		//ret.append(slaves);
		if (slaves != null) {
			Iterator i = slaves.iterator();
			//			Enumeration e = slaves.elements();
			ret.append("slaves:[");

			// HACK: How can one find out the endpoint without using regexps?
			Pattern p = Pattern.compile("endpoint:\\[(.*?):.*?\\]");

			while (i.hasNext()) {
				//SUN J2SDK 1.4: [endpoint:[213.114.146.44:2012](remote),objID:[2b6651:ef0b3c7162:-8000, 0]]]]]
				//IBM J2SDK 1.4: net.sf.drftpd.slave.SlaveImpl[RemoteStub [ref: [endpoint:[127.0.0.1:32907](local),objID:[1]]]]
				RemoteSlave slave = (RemoteSlave)i.next();
				Matcher m = p.matcher(slave.toString());
				if(!m.find()) {
					new RuntimeException("RMI regexp didn't match");
				}
				ret.append(m.group(1));
				if (i.hasNext())
					ret.append(",");
			}
			ret.append("]");
		}
		if (isDirectory()) ret.append("[directory(" + files.size() + ")]");
		//ret.append("isFile(): " + isFile() + " ");
		//ret.append(getName());
		ret.append("]]");
		return ret.toString();
	}
	/**
	 * @see net.sf.drftpd.remotefile.RemoteFileTree#getSFVFile()
	 */
	public SFVFile getSFVFile() throws IOException {
		if (sfvFile == null) {
			while (true) {
				RemoteSlave slave = getASlave();
				try {
					sfvFile = slave.getSlave().getSFVFile(getPath());
					break;
				} catch (RemoteException ex) {
					slave.getManager().handleRemoteException(ex, slave);
				}
			}
		}
		return sfvFile;
	}

	/**
	 * @throws net.sf.drftpd.master.NoAvailableSlaveException
	 */
	public long getCheckSum(boolean scan) {
		if (scan == false)
			return checkSum;
		if (checkSum != 0)
			return checkSum;
		
		RemoteSlave slave;
		while (true) {
			try {
				slave = getASlave();
			} catch(NoAvailableSlaveException ex) {
				logger.log(Level.WARNING, "NoAvailableSlaveException", ex);
				return 0L;
			}
			try {
				checkSum = slave.getSlave().checkSum(getPath());
			} catch (RemoteException ex) {
				slave.getManager().handleRemoteException(ex, slave);
				continue;
			} catch (IOException ex) {
				logger.log(Level.WARNING, "IOException", ex);
				continue;
			}
			return checkSum;
		}
	}

	/**
	 * @see net.sf.drftpd.remotefile.RemoteFile#getCheckSum()
	 * @throws net.sf.drftpd.master.NoAvailableSlaveException
	 */
	public long getCheckSum() {
		return getCheckSum(false);
	}

	public void renameTo(String to) {
		throw new NoSuchMethodError("renameTo() not implemented");
	}
	public void delete() {
		throw new NoSuchMethodError("delete() not implemented");
	}
	/**
	 * @see net.sf.drftpd.remotefile.RemoteFile#isDirectory()
	 */
	public boolean isDirectory() {
		return files != null;
	}

}