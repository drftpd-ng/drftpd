package net.sf.drftpd.remotefile;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Serializable;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.Stack;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.drftpd.FatalException;
import net.sf.drftpd.IllegalTargetException;
import net.sf.drftpd.NoAvailableSlaveException;
import net.sf.drftpd.ObjectExistsException;
import net.sf.drftpd.ObjectNotFoundException;
import net.sf.drftpd.SFVFile;
import net.sf.drftpd.master.ConnectionManager;
import net.sf.drftpd.master.RemoteSlave;
import net.sf.drftpd.master.SlaveManagerImpl;
import net.sf.drftpd.slave.Slave;
import net.sf.drftpd.slave.Transfer;

/**
 * Represents the file attributes of a remote file.
 * 
 * @author Morgan Christiansson <mog@linux.nu>
 */
//TODO make lightweight LinkedRemoteFile for the slave without RemoteSlave & ConnectionManager

public class LinkedRemoteFile extends RemoteFile implements Serializable {
	static final long serialVersionUID = 3585958839961835107L;
	private static Logger logger =
		Logger.getLogger(LinkedRemoteFile.class.getName());
	static {
		logger.setLevel(Level.FINE);
	}

	private Map files;
	private String name;

	private LinkedRemoteFile parent;
	private long xfertime;
	//private Random rand = new Random();

	//private String path;
	private boolean isDeleted = false;

	private long length;
	private long lastModified;
	/////////////////////// SLAVES
	protected Collection slaves;
	private transient ConnectionManager connectionmanager;

	/**
	 * Creates an empty RemoteFile directory, usually used as an empty root directory that
	 * <link>{merge()}</link> can be called on.
	 * 
	 * Used if no file database exists to start a tree from scratch.
	 */
	public LinkedRemoteFile(ConnectionManager cm) {
		//		canRead = true;
		//		canWrite = false;
		this.connectionmanager = cm;

		this.lastModified = System.currentTimeMillis();
		this.length = 0;
		this.isDirectory = true;
		this.isFile = false;
		this.parent = null;
		this.name = "";
		this.files = Collections.synchronizedMap(new Hashtable());
		this.slaves = Collections.synchronizedCollection(new ArrayList());
	}

	/**
	 * Creates a root directory (parent == null) that FileRemoteFile or JDOMRemoteFile is merged on.
	 * 
	 * Also called with null ConnectionManager from slave
	 */
	public LinkedRemoteFile(RemoteFile file, ConnectionManager cm)
		throws IOException {
		this(null, file, cm);
	}
	/**
	 * Creates a RemoteFile from file or creates a directory tree representation.
	 * 
	 * Used by DirectoryRemoteFile.
	 * Called by other constructor, ConnectionManager is null if called from SlaveImpl.
	 * 
	 * @param parent the parent of this file
	 * @param file file that this RemoteFile object should represent.
	 */
	private LinkedRemoteFile(
		LinkedRemoteFile parent,
		RemoteFile file,
		ConnectionManager cm) {
		this.connectionmanager = cm;
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
		this.slaves =
			Collections.synchronizedCollection(new ArrayList(file.getSlaves()));
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
			this.files = Collections.synchronizedMap(new Hashtable(dir.length));
			Stack dirstack = new Stack();
			for (int i = 0; i < dir.length; i++) {
				RemoteFile file2 = dir[i];
				if (file2.isDirectory()) {
					dirstack.push(file2);
					continue;
				}
				files.put(
					file2.getName(),
					new LinkedRemoteFile(this, file2, this.connectionmanager));
			}

			Iterator i = dirstack.iterator();
			while (i.hasNext()) {
				RemoteFile file2 = (RemoteFile) i.next();
				String filename = file2.getName();
				files.put(
					filename,
					new LinkedRemoteFile(this, file2, this.connectionmanager));
			}
		}
	}

	public LinkedRemoteFile addFile(RemoteFile file) {
		LinkedRemoteFile linkedfile =
			new LinkedRemoteFile(this, file, this.connectionmanager);
		files.put(linkedfile.getName(), linkedfile);
		return linkedfile;
	}
	public void addSlave(RemoteSlave slave) {
		if (!slaves.contains(slave))
			slaves.add(slave);
	}
	private void addSlaves(Collection addslaves) {
		if (addslaves == null)
			throw new IllegalArgumentException("addslaves cannot be null");
		for (Iterator iter = addslaves.iterator(); iter.hasNext();) {
			RemoteSlave rslave = (RemoteSlave) iter.next();
			addSlave(rslave);
		}
	}

	public LinkedRemoteFile createDirectory(
		String owner,
		String group,
		String fileName)
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
				new DirectoryRemoteFile(this, owner, group, fileName),
				connectionmanager);
		//file.addSlaves(getSlaves());
		files.put(file.getName(), file);
		logger.fine("Created directory " + file);
		return file;
	}

	public void delete() {
		if (isDirectory()) {
			for (Iterator iter = getFiles().iterator(); iter.hasNext();) {
				LinkedRemoteFile myFile = (LinkedRemoteFile) iter.next();
				myFile.delete();
			}
		}

		synchronized (slaves) {
			for (Iterator iter = slaves.iterator(); iter.hasNext();) {
				RemoteSlave rslave = (RemoteSlave) iter.next();
				Slave slave;
				try {
					slave = rslave.getSlave();
				} catch (NoAvailableSlaveException ex) {
					//TODO queued deletion
					logger.info("slave not available for deletion");
					continue;
				}
				try {
					slave.delete(getPath());
					// throws RemoteException, IOException
					iter.remove();
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
		return SlaveManagerImpl.getASlave(
			getAvailableSlaves(),
			direction,
			connectionmanager.getConfig());
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
		if (checkSum != 0 && length != 0)
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
		return ret;
		//return Collections.unmodifiableMap(ret);
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

	public boolean hasFile(String filename) {
		return files.containsKey(filename);
	}

	protected SFVFile sfvFile;
	public SFVFile lookupSFVFile()
		throws IOException, ObjectNotFoundException, NoAvailableSlaveException {
		if (!isDirectory())
			throw new IllegalStateException("lookupSFVFile must be called on a directory");

		for (Iterator iter = getFiles().iterator(); iter.hasNext();) {
			LinkedRemoteFile myFile = (LinkedRemoteFile) iter.next();
			if (myFile.getName().endsWith(".sfv")) {
				return myFile.getSFVFile();
				// throws IOException, NoAvailableSlaveException
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
					sfvFile.setCompanion(this);
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

	//TODO: remerge, delete files not in the merging slaves tree
	/**
	 * Merges two RemoteFile directories.
	 * If duplicates exist, the slaves are added to this object and the file-attributes of the oldest file (lastModified) are kept.
	 */
	public void remerge(LinkedRemoteFile mergedir, RemoteSlave rslave) {
		System.out.println("merge(): " + this +" and " + mergedir);
		if (!isDirectory()) {
			throw new IllegalArgumentException(
				"merge() called on a non-directory: "
					+ this
					+ " argument: "
					+ mergedir);
		}
		if (!mergedir.isDirectory()) {
			throw new IllegalArgumentException(
				"argument is not a directory: " + mergedir + " this: " + this);
		}
		Map map = getMap();
		//Collection mergefiles = mergedir.getFiles();
		if (mergedir.getFiles() == null) {
			throw new FatalException(
				"dir.getFiles() returned null: " + this +", " + mergedir);
		}

		// add all files from mergedir
		for (Iterator i = mergedir.getFiles().iterator(); i.hasNext();) {
			LinkedRemoteFile mergefile = (LinkedRemoteFile) i.next();

			LinkedRemoteFile file =
				(LinkedRemoteFile) files.get(mergefile.getName());
			// two scenarios:, local file [does not] exists
			if (file == null) {
				// local file does not exist, just put it in the hashtable
				map.put(mergefile.getName(), mergefile);
			} else {
				if (file.isDeleted()) {
					mergefile.delete();
					continue;
				}
				if (mergefile.isFile()
					&& file.length() != mergefile.length()) {
					throw new RuntimeException(
						"file sizes differ: "
							+ file
							+ " and "
							+ mergefile
							+ "don't know what to do! :(");
				}

				// 4 scenarios: new/existing file/directory
				if (mergefile.isDirectory()) {
					if (!file.isDirectory())
						throw new RuntimeException("!!! ERROR: Directory/File conflict!!");
					// is a directory -- dive into directory and start merging
					file.remerge(mergefile, rslave);
				} else {
					file.addSlave(rslave);
					if (file.isDirectory())
						throw new RuntimeException("!!! ERROR: File/Directory conflict!!");
				}
			}
		}

		//TODO remove all slaves not in mergedir.getFiles()
		for (Iterator i = getFiles().iterator(); i.hasNext();) {
			LinkedRemoteFile file = (LinkedRemoteFile) i.next();
			if (!mergedir.hasFile(file.getName())) {
				file.unmerge(rslave);
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
					"FileNotFoundException from slave on a file in LinkedRemoteFile",
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

	public long dirSize() {
		if (files == null)
			throw new IllegalStateException("Cannot be called on a non-directory");
		return files.size();
	}

	/**
	 * @see java.lang.Object#toString()
	 */
	public String toString() {
		StringBuffer ret = new StringBuffer();
		ret.append("LinkedRemoteFile[\"" + this.getName() + "\",");
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
				RemoteSlave rslave = (RemoteSlave) i.next();
				if (rslave == null)
					throw new FatalException("There's a null in rslaves");
				ret.append(rslave.getName());
				if (i.hasNext())
					ret.append(",");
			}
			ret.append("]");
		}
		if (isDirectory())
			ret.append("[directory(" + files.size() + ")]");
		ret.append("]");
		return ret.toString();
	}

	public void unmerge(RemoteSlave rslave) { //LinkedRemoteFile files[] = listFiles();
		removeSlave(rslave);
		if (files == null)
			return;
		for (Iterator i = files.entrySet().iterator(); i.hasNext();) {
			Map.Entry entry = (Map.Entry) i.next();
			LinkedRemoteFile file = (LinkedRemoteFile) entry.getValue();
			String filename = (String) entry.getKey();
			if (file.isDirectory()) {
				file.unmerge(rslave);
				if (file.isDeleted() && file.getFilesMap().size() == 0)
					i.remove();
			} else {
				file.removeSlave(rslave);
			}
		}
		if (isFile() && getSlaves().size() == 0) {
			delete();
		}
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#finalize()
	 * 
	 * ouch, we only want this to happen on the master root. what to do?
	 * And we don't want the extra dependency on the slave.
	 * It's so nice to have it save before it exits.
	 */
	//	protected void finalize() throws Throwable {
	//		super.finalize();
	//		if (this.parent == null) {
	//			SlaveManagerImpl.saveFilesXML(XMLSerialize.serialize(this));
	//		}
	//	}
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
	/**
	 * @return
	 */
	public long getXfertime() {
		return xfertime;
	}

	/**
	 * @param l
	 */
	public void setXfertime(long l) {
		xfertime = l;
	}

}
