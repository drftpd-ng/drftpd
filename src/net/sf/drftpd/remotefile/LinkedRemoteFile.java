package net.sf.drftpd.remotefile;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Serializable;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.Stack;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.drftpd.ObjectExistsException;
import net.sf.drftpd.IllegalTargetException;
import net.sf.drftpd.ObjectNotFoundException;
import net.sf.drftpd.SFVFile;
import net.sf.drftpd.master.NoAvailableSlaveException;
import net.sf.drftpd.master.SlaveManagerImpl;
import net.sf.drftpd.master.usermanager.User;
import net.sf.drftpd.slave.RemoteSlave;
import net.sf.drftpd.slave.Slave;
import net.sf.drftpd.slave.Transfer;

import org.jdom.Element;

/**
 * Represents the file attributes of a remote file.
 * 
 * @author Morgan Christiansson <mog@linux.nu>
 */
public class LinkedRemoteFile extends RemoteFile implements Serializable {
	private static Logger logger =
		Logger.getLogger(LinkedRemoteFile.class.getName());
	static {
		logger.setLevel(Level.FINE);
	}

	private Hashtable files;
	private String name;

	private LinkedRemoteFile parent;
	//private Random rand = new Random();

	//private String path;
	private boolean isDeleted = false;

	/////////////////////// SLAVES
	protected Collection slaves;

	/**
	 * Creates an empty RemoteFile directory, usually used as an empty root directory that
	 * <link>{merge()}</link> can be called on.
	 */
	public LinkedRemoteFile() {
		//		canRead = true;
		//		canWrite = false;
		this.lastModified = System.currentTimeMillis();
		this.length = 0;
		this.isDirectory = true;
		this.isFile = false;
		this.parent = null;
		this.name = "";
		this.files = new Hashtable();
		this.slaves = new ArrayList();
	}

	public LinkedRemoteFile(RemoteFile file) throws IOException {
		this(null, file);
	}
	/**
	 * Creates a RemoteFile from file or creates a directory tree representation.
	 * @param parent the parent of this file
	 * @param file file that this RemoteFile object should represent.
	 */
	public LinkedRemoteFile(LinkedRemoteFile parent, RemoteFile file) {

		this.lastModified = file.lastModified();
		this.length = file.length();
		if (this.length == -1)
			throw new IllegalArgumentException("length() == -1 for " + file);
		//isHidden = file.isHidden();
		this.isDirectory = file.isDirectory();
		this.isFile = file.isFile();

		this.owner = file.getOwner();
		this.group = file.getGroup();
		this.checkSum = file.getCheckSum();
		this.slaves = new ArrayList(file.getSlaves());
		if (this.slaves == null) {
			throw new IllegalArgumentException("slaves == null for " + file);
		}

		if (parent == null) {
			name = "";
		} else {
			name = file.getName();
		}
		//path = file.getPath();
		/* serialize directory*/
		this.parent = parent;

		//		if (remoteSlave != null) {
		//			slaves.add(remoteSlave);
		//		}

		if (file.isDirectory()) {
			RemoteFile dir[] = file.listFiles();
			files = new Hashtable(dir.length);
			Stack dirstack = new Stack();
			for (int i = 0; i < dir.length; i++) {
				RemoteFile file2 = dir[i];
				if (file2.isDirectory()) {
					dirstack.push(file2);
					continue;
				}
				files.put(file2.getName(), new LinkedRemoteFile(this, file2));
			}

			Iterator i = dirstack.iterator();
			while (i.hasNext()) {
				RemoteFile file2 = (RemoteFile) i.next();
				String filename = file2.getName();
				files.put(filename, new LinkedRemoteFile(this, file2));
			}
		}
	}

	public LinkedRemoteFile addFile(RemoteFile file) {
		LinkedRemoteFile linkedfile = new LinkedRemoteFile(this, file);
		files.put(linkedfile.getName(), linkedfile);
		return linkedfile;
	}
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

	public LinkedRemoteFile createDirectory(User owner, String fileName)
		throws ObjectExistsException {
		LinkedRemoteFile existingfile = (LinkedRemoteFile) files.get(fileName);
		if (existingfile != null) {
			throw new ObjectExistsException(
				fileName + " already exists in this directory");
		}
		//		for (Iterator i = slaves.iterator(); i.hasNext();) {
		//			RemoteSlave slave = (RemoteSlave) i.next();
		//			try {
		//				slave.getSlave().mkdir(owner, getPath() + "/" + fileName);
		//			} catch (RemoteException ex) {
		//				slave.handleRemoteException(ex);
		//			}
		//		}
		LinkedRemoteFile file =
			new LinkedRemoteFile(
				this,
				new DirectoryRemoteFile(this, owner, fileName));
		//file.addSlaves(getSlaves());
		files.put(file.getName(), file);
		logger.fine("Created directory " + file);
		return file;
	}

	public void delete() {
		if(isDirectory()) {
			for (Iterator iter = getFiles().iterator(); iter.hasNext();) {
				LinkedRemoteFile myFile = (LinkedRemoteFile) iter.next();
				myFile.delete();
			}
		}
		for (Iterator iter = slaves.iterator(); iter.hasNext();) {
			RemoteSlave rslave = (RemoteSlave) iter.next();
			Slave slave;
			try {
				slave = rslave.getSlave();
			} catch (NoAvailableSlaveException ex) {
				logger.info("slave not available for deletion");
				continue;
			}
			try {
				slave.delete(getPath()); // throws RemoteException, IOException
				slaves.remove(rslave);
			} catch (RemoteException ex) {
				rslave.handleRemoteException(ex);
				continue;
			} catch (IOException ex) {
				logger.log(
					Level.SEVERE,
					"IOException deleting file on slave.",
					ex);
				continue;
			}
		}
		isDeleted = true;
		if (slaves.size() == 0) {
			try {
				getParentFile().getMap().remove(getName());
			} catch (FileNotFoundException ex) {
				logger.log(
					Level.SEVERE,
					"FileNotFoundException on getParentFile()",
					ex);
			}
		} else {
			//TODO queued deletion
			logger.log(
				Level.INFO,
				"TODO: "
					+ this.getPath()
					+ " should be queued for deletion, slaves:"
					+ slaves);
		}
	}

	public boolean equals(Object obj) {
		if (obj instanceof LinkedRemoteFile
			&& ((LinkedRemoteFile) obj).getPath().equals(getPath())) {
			return true;
		}
		return false;
	}

	public RemoteSlave getASlave(char direction)
		throws NoAvailableSlaveException {
		return SlaveManagerImpl.getASlave(getAvailableSlaves(), direction);
	}

	public RemoteSlave getASlave() throws NoAvailableSlaveException {
		return getASlave(Transfer.TRANSFER_THROUGHPUT);
	}

	/**
	 * @see net.sf.drftpd.remotefile.RemoteFile#getCheckSum()
	 * @throws net.sf.drftpd.master.NoAvailableSlaveException
	 */
	public long getCheckSum() {
		return getCheckSum(true);
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
			} catch (NoAvailableSlaveException ex) {
				logger.log(Level.WARNING, "NoAvailableSlaveException", ex);
				return 0L;
			}
			try {
				checkSum = slave.getSlave().checkSum(getPath());
			} catch (RemoteException ex) {
				slave.handleRemoteException(ex);
				continue;
			} catch (IOException ex) {
				logger.log(Level.WARNING, "IOException", ex);
				continue;
			}
			return checkSum;
		}
	}

	/**
	 * Returns fileName contained in this directory.
	 * 
	 * @param fileName
	 * @throws FileNotFoundException if fileName doesn't exist in the files Map
	 */
	public LinkedRemoteFile getFile(String fileName)
		throws FileNotFoundException {
		LinkedRemoteFile file = (LinkedRemoteFile) files.get(fileName);
		if (file == null)
			throw new FileNotFoundException("No such file or directory");
		if (file.isDeleted())
			throw new FileNotFoundException("File is queued for deletion");
		return file;
	}

	public Collection getFiles() {
		return getFilesMap().values();
	}

	public Map getFilesMap() {
		Hashtable ret = new Hashtable(files);

		for (Iterator iter = ret.values().iterator(); iter.hasNext();) {
			LinkedRemoteFile file = (LinkedRemoteFile) iter.next();
			if (file.isDeleted())
				iter.remove();
		}

		return Collections.unmodifiableMap(ret);
	}

	/** return files;
	 */
	public Map getMap() {
		return files;
	}

	/** return name;
	 */
	public String getName() {
		return name;
	}

	/**
	 * @see java.io.File#getParent()
	 * @see net.sf.drftpd.remotefile.RemoteFile#getParent()
	 */
	public String getParent() throws FileNotFoundException {
		return getParentFile().getPath();
	}

	/**
	 * @see java.io.File#getParentFile()
	 */
	public LinkedRemoteFile getParentFile() throws FileNotFoundException {
		if (parent == null)
			throw new FileNotFoundException("root directory has no parent");
		return parent;
	}

	public String getPath() {
		StringBuffer path = new StringBuffer();
		LinkedRemoteFile parent = this;

		while (true) {
			if (parent.getName().length() == 0)
				break;
			//			if(parent == null) break;
			path.insert(0, "/" + parent.getName());
			try {
				parent = parent.getParentFile();
				// throws FileNotFoundException
			} catch (FileNotFoundException ex) {
				break;
			}
		}
		if (isDirectory())
			path.append("/");

		return path.toString();
	}

	public LinkedRemoteFile getRoot() {
		LinkedRemoteFile root = this;
		try {
			while (true)
				root = root.getParentFile();
		} catch (FileNotFoundException ex) {
		}
		return root;
	}

	protected SFVFile sfvFile;
	public SFVFile lookupSFVFile()
		throws IOException, ObjectNotFoundException, NoAvailableSlaveException {
		if (!isDirectory())
			throw new IllegalStateException("lookupSFVFile must be called on a directory");
			
		for (Iterator iter = getFiles().iterator(); iter.hasNext();) {
			LinkedRemoteFile myFile = (LinkedRemoteFile) iter.next();
			if (myFile.getName().endsWith(".sfv")) {
				return myFile.getSFVFile(); // throws IOException, NoAvailableSlaveException
			}
		}
		throw new ObjectNotFoundException("no sfv file in directory");

	}
	public SFVFile getSFVFile() throws IOException, NoAvailableSlaveException {
		if (sfvFile == null) {
			while (true) {
				RemoteSlave slave = getASlave();
				try {
					sfvFile = slave.getSlave().getSFVFile(getPath());
					break;
				} catch (RemoteException ex) {
					slave.handleRemoteException(ex);
				}
			}
			throw new NoAvailableSlaveException("No available slaves");
		}
		return sfvFile;
	}

	public Collection getAvailableSlaves() throws NoAvailableSlaveException {
		ArrayList availableSlaves = new ArrayList();
		for (Iterator iter = getSlaves().iterator(); iter.hasNext();) {
			RemoteSlave rslave = (RemoteSlave) iter.next();
			if (!rslave.isAvailable())
				continue;
			availableSlaves.add(rslave);
		}
		if (availableSlaves.isEmpty()) {
			throw new NoAvailableSlaveException(
				getPath() + " has 0 slaves online");
		}
		return availableSlaves;
	}

	/** return slaves;
	 */
	public Collection getSlaves() {
		return slaves;
	}

	/** return isDeleted;
	 */
	public boolean isDeleted() {
		return isDeleted;
	}

	/**
	 * @see net.sf.drftpd.remotefile.RemoteFile#isDirectory()
	 */
	public boolean isDirectory() {
		return files != null;
	}

	public RemoteFile[] listFiles() {
		if (!isDirectory())
			throw new RuntimeException(getPath() + " is not a directory");
		return (LinkedRemoteFile[]) getFilesMap().values().toArray(
			new LinkedRemoteFile[0]);
	}

	/**
	 * 
	 * @param path
	 * @return new Object[] {LinkedRemoteFile file, String path};
	 * path is null if path exists
	 */
	public Object[] lookupNonExistingFile(String path) {
		if (path == null)
			throw new IllegalArgumentException("null path not allowed");
		LinkedRemoteFile currFile = this;

		if (path.charAt(0) == '/')
			currFile = getRoot();

		//check for leading ~
		if (path.length() == 1 && path.equals("~")) {
			currFile = getRoot();
			path = "";
		} else if (path.length() >= 2 && path.substring(0, 2).equals("~/")) {
			currFile = getRoot();
			path = path.substring(2);
		}

		StringTokenizer st = new StringTokenizer(path, "/");
		while (st.hasMoreTokens()) {
			String currFileName = st.nextToken();
			if (currFileName.equals("."))
				continue;
			if (currFileName.equals("..")) {
				try {
					currFile = currFile.getParentFile();
				} catch (FileNotFoundException ex) {
				}
				continue;
			}
			LinkedRemoteFile nextFile;
			try {
				nextFile = (LinkedRemoteFile) currFile.getFile(currFileName);
			} catch (FileNotFoundException ex) {
				StringBuffer remaining = new StringBuffer(currFileName);
				while (st.hasMoreElements()) {
					remaining.append('/').append((String) st.nextElement());
				}
				return new Object[] { currFile, remaining.toString()};
			}
			currFile = nextFile;
		}
		return new Object[] { currFile, null };
	}

	public LinkedRemoteFile lookupFile(String path)
		throws FileNotFoundException {

		Object[] ret = lookupNonExistingFile(path);

		//logger.info("ret[0] = " + ret[0] + " ret[1] = " + ret[1]);
		if (ret[1] != null)
			throw new FileNotFoundException(path + " not found");
		return (LinkedRemoteFile) ret[0];
	}

	public String lookupPath(String path) {
		Object[] ret = lookupNonExistingFile(path);
		return ((LinkedRemoteFile) ret[0]).getPath() + ((String) ret[1]);
	}

	/**
	 * Merges two RemoteFile directories.
	 * If duplicates exist, the slaves are added to this object and the file-attributes of the oldest file (lastModified) are kept.
	 */
	public void merge(RemoteFile dir, RemoteSlave rslave) {
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
		Map map = this.getMap();
		Collection mergefiles = dir.getFiles();
		if (mergefiles == null) {
			return;
			// remote directory wasn't added, it might have been a symlink.
		}
		System.out.println(
			"Adding "
				+ dir.getPath()
				+ " with "
				+ mergefiles.size()
				+ " files");
		Iterator i = mergefiles.iterator();
		while (i.hasNext()) {
			LinkedRemoteFile mergefile = (LinkedRemoteFile) i.next();
			String filename = mergefile.getName();

			//RemoteFile file = (RemoteFile) entry.getValue();
			LinkedRemoteFile file = (LinkedRemoteFile) files.get(filename);
			//RemoteFile mergefile = (RemoteFile) mergemap.get(getName());
			// two scenarios:, local file [does not] exists
			if (file == null) {
				// local file does not exist, just put it in the hashtable
				System.out.println("Just put it: " + mergefile.toString());
				map.put(mergefile.getName(), mergefile);
			} else {
				if (file.length() != mergefile.length()) {
					throw new RuntimeException("file sizes differ");
				}

				if (lastModified() > mergefile.lastModified()) {
					logger.log(
						Level.WARNING,
						getPath()
							+ ": last modified changed from "
							+ new Date(lastModified())
							+ " to "
							+ new Date(mergefile.lastModified()));
					lastModified = mergefile.lastModified();
				} else {
					System.out.println(
						"Last modified NOT changed from "
							+ new Date(lastModified())
							+ " to "
							+ new Date(mergefile.lastModified()));
				} // 4 scenarios: new/existing file/directory
				if (mergefile.isDirectory()) {
					if (!file.isDirectory())
						throw new RuntimeException("!!! ERROR: Directory/File conflict!!");
					// is a directory -- dive into directory and start merging
					file.merge(mergefile, rslave);
				} else {
					if (file.isDirectory())
						throw new RuntimeException("!!! ERROR: File/Directory conflict!!");
				} // in all cases we add the slaves of the remote file to 'this' file
				//Collection slaves2 = mergefile.getSlaves();
				//file.addSlaves(slaves2);
				file.addSlave(rslave);
			}
		} // directory backdating, do other attrbiutes need "backdating"? if so fix it! :)
		if (lastModified() > dir.lastModified()) {
			logger.log(
				Level.WARNING,
				getPath()
					+ ": last modified changed from "
					+ new Date(lastModified())
					+ " to "
					+ new Date(dir.lastModified()));
			lastModified = dir.lastModified();
		}
	}

	private void recursiveUnmerge(RemoteSlave slave) {
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

	public void removeSlave(RemoteSlave slave) {
		slaves.remove(slave);
	}

	// cannot rename to existing file
	//	public void renameTo(RemoteFile to) throws IOException {
	//		renameTo(to.getPath());
	//	}

	/**
	 * Renames this file
	 * 
	 * Sanity checks performed:
	 *   VirtualDirectory.isLegalFileName(to)
	 *   destination file does not exist
	 * @throws IllegalFileNameException, FileExistsException, FileNotFoundException
	 */
	public void renameTo(String to)
		throws ObjectExistsException, IllegalTargetException {
		if (to.charAt(0) != '/')
			throw new IllegalArgumentException("renameTo() must be given an absolute path as argument");

		// throws FileNotFoundException
		/*if (getParentFile().getMap().get(to) != null) {
			throw new FileExistsException("Target file exists");
		}*/

		String fromName = getName();

		for (Iterator iter = slaves.iterator(); iter.hasNext();) {
			RemoteSlave rslave = (RemoteSlave) iter.next();
			Slave slave;
			try {
				slave = rslave.getSlave();
			} catch (NoAvailableSlaveException ex) {
				//TODO slave is offline, queue rename
				continue;
			}
			try {
				slave.rename(getPath(), to);
				// throws RemoteException, IOException
			} catch (RemoteException ex) {
				rslave.handleRemoteException(ex);
			} catch (FileNotFoundException ex) {
				logger.log(
					Level.SEVERE,
					"FileNotFoundException from slave on a file in LinkedRemoteFile, TODO: handle it",
					ex);
			}
		}

		try {
			getParentFile().files.remove(fromName);
		} catch (FileNotFoundException ex) {
			logger.log(
				Level.SEVERE,
				"FileNotFoundException on getParentFile() on this in rename",
				ex);
		}

		Object[] ret = lookupNonExistingFile(to);

		LinkedRemoteFile toDir = (LinkedRemoteFile) ret[0];
		String toName = (String) ret[1];
		if (toName == null)
			throw new ObjectExistsException("Target already exists");
		if (toName.indexOf('/') != -1)
			throw new IllegalTargetException("Cannot rename to non-existing directory");
		//toDir.mkdir()

		toDir.getMap().put(toName, this);
		name = toName;
	}

	public void setLastModified(long lastModified) {
		this.lastModified = lastModified;
	}

	public long size() {
		if (isDirectory()) {
			return files.size();
		}
		return length();
	}

	/**
	 * @see java.lang.Object#toString()
	 */
	public String toString() {
		StringBuffer ret = new StringBuffer();
		ret.append(this.getClass().getName() + "[\"" + this.getName() + "\",");
		if (this.isDeleted())
			ret.append("deleted,");
		//ret.append(slaves);
		if (slaves != null) {
			Iterator i = slaves.iterator();
			//			Enumeration e = slaves.elements();
			ret.append("slaves:[");
			// HACK: How can one find out the endpoint without using regexps?
			//Pattern p = Pattern.compile("endpoint:\\[(.*?):.*?\\]");
			while (i.hasNext()) {
				//SUN J2SDK 1.4: [endpoint:[213.114.146.44:2012](remote),objID:[2b6651:ef0b3c7162:-8000, 0]]]]]
				//IBM J2SDK 1.4: net.sf.drftpd.slave.SlaveImpl[RemoteStub [ref: [endpoint:[127.0.0.1:32907](local),objID:[1]]]]
				RemoteSlave slave = (RemoteSlave) i.next();
				//TODO find RMI stub class and a better way of extracting the endpoint

				//				Matcher m;
				//				try {
				//					m = p.matcher(slave.getSlave().toString());
				//				} catch (NoAvailableSlaveException e) {
				//					throw new RuntimeException(e);
				//				}
				//				if (!m.find()) {
				//					new RuntimeException("RMI regexp didn't match");
				//				}
				//				ret.append(m.group(1));
				ret.append(slave.toString());
				if (i.hasNext())
					ret.append(",");
			}
			ret.append("]");
		}
		if (isDirectory())
			ret.append("[directory(" + files.size() + ")]");
		//ret.append("isFile(): " + isFile() + " ");
		//ret.append(getName());
		//ret.append("[owner:" + getOwner() + "]");
		//ret.append("[group:" + getGroup() + "]");
		ret.append("]");
		return ret.toString();
	}

	public Element toXML() {
		Element element =
			new Element(this.isDirectory() ? "directory" : "file");
		//		if (this.isDirectory()) {
		//			element = new Element("directory");
		//		} else {
		//			element = new Element("file");
		//		}
		element.setAttribute("name", this.getName());

		element.addContent(new Element("user").setText(this.getOwner()));
		element.addContent(new Element("group").setText(this.getGroup()));

		element.addContent(
			new Element("size").setText(Long.toString(this.length())));

		element.addContent(
			new Element("lastModified").setText(
				Long.toString(this.lastModified())));

		if (this.isDirectory()) {
			Element contents = new Element("contents");
			for (Iterator i = this.getFiles().iterator(); i.hasNext();) {
				contents.addContent(((LinkedRemoteFile) i.next()).toXML());
			}
			element.addContent(contents);
		} else {
			String checksum = "";
			checksum = Long.toHexString(this.getCheckSum(false));

			element.addContent(new Element("checksum").setText(checksum));
		}

		Element slaves = new Element("slaves");
		for (Iterator i = this.getSlaves().iterator(); i.hasNext();) {
			RemoteSlave rslave = (RemoteSlave) i.next();
			slaves.addContent(new Element("slave").setText(rslave.getName()));
		}
		element.addContent(slaves);

		return element;
	}

	public void unmerge(RemoteSlave slave) { //LinkedRemoteFile files[] = listFiles();
		if (!getSlaves().remove(slave)) {
			System.out.println("Slave already removed from " + this);
		}
		recursiveUnmerge(slave);
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#finalize()
	 */
	protected void finalize() throws Throwable {
		super.finalize();
		if (this.parent == null) {
			SlaveManagerImpl.saveFilesXML(this.toXML());
		}
	}
	private long length;
	private long lastModified;
	/**
	 * @see net.sf.drftpd.remotefile.RemoteFile#length()
	 */
	public long length() {
		return this.length;
	}
	
	public void setLength(long length) {
		this.length = length;
	}
	
	/* (non-Javadoc)
	 * @see net.sf.drftpd.remotefile.RemoteFile#lastModified()
	 */
	public long lastModified() {
		return this.lastModified;
	}
}
