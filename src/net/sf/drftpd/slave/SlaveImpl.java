package net.sf.drftpd.slave;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.rmi.server.Unreferenced;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Properties;
import java.util.Vector;
import java.util.zip.CRC32;
import java.util.zip.CheckedInputStream;

import net.sf.drftpd.FatalException;
import net.sf.drftpd.ObjectExistsException;
import net.sf.drftpd.PermissionDeniedException;
import net.sf.drftpd.SFVFile;
import net.sf.drftpd.master.ConnectionManager;
import net.sf.drftpd.master.SlaveManager;
import net.sf.drftpd.remotefile.FileRemoteFile;
import net.sf.drftpd.remotefile.LinkedRemoteFile;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import se.mog.io.File;

/**
 * @author <a href="mailto:drftpd@mog.se">Morgan Christiansson</a>
 */
public class SlaveImpl
	extends UnicastRemoteObject
	implements Slave, Unreferenced {
	/**
	 * @param cfg
	 * @param inetAddress
	 * @param b
	 */
	public SlaveImpl(Properties cfg)
		throws RemoteException {
		super(0); // starts RMI accept thread which will keep us from dying

		String slavemanagerurl;
		slavemanagerurl =
			"//"
				+ cfg.getProperty("master.host")
				+ ":"
				+ cfg.getProperty("master.bindport", "1099")
				+ "/"
				+ cfg.getProperty("master.bindname");
		this.name = cfg.getProperty("slave.name");

		this.roots = getDefaultRootBasket(cfg);
		try {
			SlaveManager manager;
			logger.log(Level.INFO, "Getting master reference");
			manager = (SlaveManager) Naming.lookup(slavemanagerurl);

			logger.log(
				Level.INFO,
				"Registering with master and sending filelist");

			LinkedRemoteFile slaveroot = SlaveImpl.getDefaultRoot(this.roots);
			manager.addSlave(this.name, this, slaveroot);

			logger.log(
				Level.INFO,
				"Finished registered with master, awaiting commands.");
		} catch (Throwable t) {
			logger.warn("Error registering with slave", t);
			System.exit(0);
		}
		System.gc();
	}

	private Vector transfers = new Vector();
	private static Logger logger = Logger.getLogger(SlaveImpl.class.getName());
	static {
		logger.setLevel(Level.ALL);
	}
	//private String root;
	private RootBasket roots;
	private String name;

	public static void main(String args[]) {
		BasicConfigurator.configure();
		System.out.println(
			ConnectionManager.VERSION + " slave server starting");
		String drftpdconf;
		if (args.length >= 1) {
			drftpdconf = args[0];
		} else {
			drftpdconf = "drftpd-0.7.conf";
		}
		try {

			Properties cfg = new Properties();
			try {
				cfg.load(new FileInputStream(drftpdconf));
			} catch (Throwable ex) {
				ex.printStackTrace();
				System.err.println("Could not open drftpd.conf, exiting.");
				System.exit(0);
				return;
			}

			new SlaveImpl(cfg);

		} catch (Throwable e) {
			logger.warn("Error registering", e);
			System.exit(0);
			return;
		}
	}

	public static RootBasket getDefaultRootBasket(Properties cfg) {
		RootBasket roots;
		// START: RootBasket
		ArrayList rootStrings = new ArrayList();
		for (int i = 1; true; i++) {
			String rootString = cfg.getProperty("slave.root." + i);
			System.out.println("slave.root." + i + ": " + rootString);
			if (rootString == null)
				break;

			long minSpaceFree;
			try {
				minSpaceFree =
					Long.parseLong(
						cfg.getProperty("slave.root." + i + ".minspacefree"));
			} catch (NumberFormatException ex) {
				minSpaceFree = 0;
			}

			int priority;
			try {
				priority =
					Integer.parseInt(
						cfg.getProperty("slave.root." + i + ".priority"));
			} catch (NumberFormatException ex) {
				priority = 0;
			}

			rootStrings.add(new Root(rootString, minSpaceFree, priority));
		}

		try {
			roots = new RootBasket(rootStrings);
		} catch (FileNotFoundException e) {
			throw new FatalException(e);
		}
		// END: RootBasket
		return roots;
	}

	/**
	 * returns the {LinkedRemoteFile} directory that will be serialized and registered at the master.
	 * they all end up here
	 */
	public static LinkedRemoteFile getDefaultRoot(RootBasket rootBasket)
		throws IOException {

		LinkedRemoteFile linkedroot =
			new LinkedRemoteFile(new FileRemoteFile(rootBasket), null);

		return linkedroot;
	}

	public SlaveStatus getSlaveStatus() {
		int throughputUp = 0, throughputDown = 0;
		int transfersUp = 0, transfersDown = 0;

		for (Iterator i = transfers.iterator(); i.hasNext();) {
			TransferImpl transfer = (TransferImpl) i.next();
			if (transfer.getDirection()
				== Transfer.TRANSFER_RECEIVING_UPLOAD) {
				throughputUp += transfer.getTransferSpeed();
				transfersUp += 1;
			} else if (
				transfer.getDirection()
					== Transfer.TRANSFER_SENDING_DOWNLOAD) {
				throughputDown += transfer.getTransferSpeed();
				transfersDown += 1;
			} else {
				throw new FatalException("unrecognized direction");
			}
		}
		try {
			return new SlaveStatus(
				roots.getTotalDiskSpaceAvailable(),
				roots.getTotalDiskSpaceCapacity(),
				throughputUp,
				transfersUp,
				throughputDown,
				transfersDown);
		} catch (Exception ex) {
			ex.printStackTrace();
			throw new RuntimeException(ex.toString());
		}
	}

	/**
	 * @see net.sf.drftpd.slave.Slave#ping()
	 */
	public void ping() {
	}

	/**
	 * @see net.sf.drftpd.slave.Slave#checkSum(String)
	 */
	public long checkSum(String path) throws IOException {
		logger.debug("Checksumming: " + path);
		CRC32 crc32 = new CRC32();
		InputStream in =
			//			new CheckedInputStream(new FileInputStream(root + path), crc32);
	new CheckedInputStream(new FileInputStream(roots.getFile(path)), crc32);
		byte buf[] = new byte[1024];
		while (in.read(buf) != -1);
		return crc32.getValue();
	}

	public SFVFile getSFVFile(String path) throws IOException {
		return new SFVFile(
			new BufferedReader(new FileReader(roots.getFile(path))));
	}
	/**
	 * @see net.sf.drftpd.slave.Slave#rename(String, String)
	 */
	public void rename(String from, String to)
		throws FileNotFoundException, ObjectExistsException {
		Root root = roots.getRootForFile(from);
		File fromfile = new File(root.getPath()+File.separatorChar+from);
		// throws FileNotFoundException
		if (!fromfile.exists())
			throw new FileNotFoundException(
				"cannot rename from " + from + ", file does not exist");
		File tofile;
		try {
			roots.getFile(to);
			throw new ObjectExistsException(
				"cannot rename from "
					+ from
					+ " to "
					+ to
					+ ", destination exists");
		} catch(FileNotFoundException ex) {} // good
		tofile = new File(root.getPath() + to);
		fromfile.renameTo(tofile);
	}

	public void delete(String path) throws IOException {
		Collection files = roots.getMultipleFiles(path);
		for (Iterator iter = files.iterator(); iter.hasNext();) {
			File file = (File) iter.next();
			assert file.exists();
			if (!file.delete())
				throw new PermissionDeniedException("delete failed on " + path);
			File dir = new File(file.getParentFile());
			assert dir != null;
			while (dir.list().length == 0) {
				file.delete();
				dir = new File(dir.getParentFile());
			}
		}
	}

	/* (non-Javadoc)
	 * @see java.rmi.server.Unreferenced#unreferenced()
	 */
	public void unreferenced() {
		logger.info("unreferenced");
		System.exit(0);
		//		logger.log(
		//			Level.WARN,
		//			"Lost master, trying to re-register with master.");
		//		register();
		//		System.gc();
	}

	/* (non-Javadoc)
	 * @see net.sf.drftpd.slave.Slave#listen()
	 */
	public Transfer listen() throws RemoteException, IOException {

		return new TransferImpl(
			this.transfers,
			new PassiveConnection(null),
			this.roots);
	}

	/* (non-Javadoc)
	 * @see net.sf.drftpd.slave.Slave#connect(java.net.InetAddress, int)
	 */
	public Transfer connect(InetAddress addr, int port)
		throws RemoteException {
		return new TransferImpl(
			this.transfers,
			new ActiveConnection(addr, port),
			this.roots);
	}
}
