package net.sf.drftpd.slave;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.server.RMISocketFactory;
import java.rmi.server.UnicastRemoteObject;
import java.rmi.server.Unreferenced;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Properties;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.CRC32;
import java.util.zip.CheckedInputStream;

import net.sf.drftpd.FatalException;
import net.sf.drftpd.ObjectExistsException;
import net.sf.drftpd.PermissionDeniedException;
import net.sf.drftpd.SFVFile;
import net.sf.drftpd.master.SlaveManager;
import net.sf.drftpd.permission.InetAddrRMIServerSocketFactory;
import net.sf.drftpd.remotefile.FileRemoteFile;
import net.sf.drftpd.remotefile.LinkedRemoteFile;
import se.mog.io.File;

/**
 * @author <a href="mailto:drftpd@mog.se">Morgan Christiansson</a>
 */
public class SlaveImpl
	extends UnicastRemoteObject
	implements Slave, Unreferenced {
	private Vector transfers = new Vector();
	private static Logger logger = Logger.getLogger(SlaveImpl.class.getName());
	static {
		logger.setLevel(Level.FINEST);
	}
	//Properties cfg;

	SlaveManager slavemanager;
	//private String root;
	private RootBasket roots;
	private String slavemanagerurl;
	private String name;

	public SlaveImpl(Properties cfg, InetAddress inetAddress) throws RemoteException {
		super(0, RMISocketFactory.getDefaultSocketFactory(), new InetAddrRMIServerSocketFactory(inetAddress));
		this.slavemanagerurl = "//"+cfg.getProperty("master.host")+"/"+cfg.getProperty("master.bindname");
		this.name = cfg.getProperty("slave.name");

		// START: RootBasket
		ArrayList rootStrings = new ArrayList();
		for (int i = 0; ; i++) {
			String rootString = System.getProperty("slave.root."+i);
			if(rootString == null) break;
			
			long minSpaceFree;
			try {
				minSpaceFree = Long.parseLong(cfg.getProperty("slave.root."+i+".minspacefree"));
			} catch(NumberFormatException ex) {
				minSpaceFree = 0;
			}

			int priority;
			try {
				priority = Integer.parseInt(cfg.getProperty("slave.root."+i+".priority"));
			} catch(NumberFormatException ex) {
				priority = 0;
			}

			System.out.println("root."+i+": "+rootString);
			rootStrings.add(new Root(rootString, minSpaceFree, priority));
		}
		
		try {
			roots = new RootBasket(rootStrings);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			System.exit(0);
			return;
		}
		// END: RootBasket

		register();
		System.gc();
	}

	public void register() {
		while (true) {
			try {
				logger.log(Level.INFO, "Getting master reference");
				SlaveManager manager;
				manager = (SlaveManager) Naming.lookup(slavemanagerurl);
				
				logger.log(Level.INFO, "Registering with master");

				LinkedRemoteFile slaveroot =
					SlaveImpl.getDefaultRoot(this.roots);

				manager.addSlave(this.name, this, slaveroot);
				logger.log(Level.INFO, "Registered with master.");
				break;
			} catch (Throwable t) {
				long retry = Long.parseLong(System.getProperty("java.rmi.dgc.leaseValue", "600000"));
				logger.log(Level.SEVERE, "Failed to register slave, will retry in "+retry/1000+" seconds", t);
				try {
					Thread.sleep(retry);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
		System.gc();
		return;
	}
	public static void main(String args[]) {
		String drftpdconf;
		if(args.length >= 1) {
			drftpdconf = args[0];
		} else {
			drftpdconf = "drftpd.conf";
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

			InetAddress masterAddr = InetAddress.getByName(cfg.getProperty("slavemanager.host"));
			Slave slave;
			slave = new SlaveImpl(cfg, masterAddr);

		} catch (Throwable e) {
			e.printStackTrace();
			System.exit(0);
			return;
		}
	}

	public static LinkedRemoteFile getDefaultRoot(String rootString)
		throws IOException {
		return getDefaultRoot(new RootBasket(rootString));
		//RootBasket  throws FileNotFoundException
	}

	/**
	 * returns the {LinkedRemoteFile} directory that will be serialized and registered at the master.
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

	/////////////////// TRANSFER METHODS ///////////////////////////
	// SEND

	/**
	 * Starts sending 'remotefile' starting at 'offset' bytes to a outputstream from the already connected socket 'sock'.
	 */
	private Transfer doSend(
		String path,
		char type,
		long offset,
		Connection conn)
		throws IOException {

		File file = roots.getFile(path); // throws FileNotFoundException

		FileInputStream in = new FileInputStream(file); // throws FileNotFoundException
		in.skip(offset);

		TransferImpl transfer = new TransferImpl(transfers, in, conn, type);
		return transfer;
	}

	/**
	 * @see net.sf.drftpd.slave.Slave#doConnectSend(REmoteFile, long, InetADdress, int)
	 */
	public Transfer doConnectSend(
		String path,
		char type,
		long offset,
		InetAddress addr,
		int port)
		throws IOException {

		return doSend(path, type, offset, new ActiveConnection(addr, port));
	}

	/**
	 * @see net.sf.drftpd.slave.Slave#doListenSend(RemoteFile, long, int)
	 */
	public Transfer doListenSend(
		String path,
		char type,
		long offset)
		throws IOException {

		return doSend(path, type, offset, new PassiveConnection());
	}

	//RECEIVE
	/**
	 * Generic receive method.
	 */
	private Transfer doReceive(
		String dirname,
		String filename,
		char type,
		long offset,
		Connection conn)
		throws IOException {

		String root = roots.getARoot().getPath();
		new File(root + dirname).mkdirs();
		File file =
			new File(
				root + File.separator + dirname + File.separator + filename);
		FileOutputStream out = new FileOutputStream(file);

		//		Socket sock = conn.connect();
		//		InputStream in = sock.getInputStream();
		/*
				CRC32 checksum = new CRC32();
				CheckedOutputStream cos = new CheckedOutputStream(os, checksum);
		*/
		TransferImpl transfer = new TransferImpl(transfers, conn, out);
		return transfer;
	}

	/**
	 * @see net.sf.drftpd.slave.Slave#doConnectReceive(RemoteFile, long, InetAddress, int)
	 */
	public Transfer doConnectReceive(
		String dirname,
		String filename,
		char type,
		long offset,
		InetAddress addr, int port)
		throws IOException {

		//Socket sock = new Socket(addr, port);
		return doReceive(
			dirname,
			filename,
			type,
			offset,
			new ActiveConnection(addr, port));
	}

	/**
	 * @see net.sf.drftpd.slave.Slave#doListenReceive(RemoteFile, long, int)
	 */
	public Transfer doListenReceive(
		String dirname,
		String filename,
		char type, long offset)
		throws IOException {
		//		ServerSocket server = new ServerSocket(0, 1);
		//server.setSoTimeout(xxx);
		//		Socket sock = server.accept();
		//		server.close();
		return doReceive(dirname, filename, type, offset, new PassiveConnection());
	}

	/**
	 * @see net.sf.drftpd.slave.Slave#checkSum(String)
	 */
	public long checkSum(String path) throws IOException {
		logger.fine("Checksumming: " + path);
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
		File fromfile = roots.getFile(from);
		// throws FileNotFoundException
		if (!fromfile.exists())
			throw new FileNotFoundException(
				"cannot rename from " + from + ", file does not exist");
		File tofile = new File(roots.getARoot() + to);
		if (tofile.exists())
			throw new ObjectExistsException(
				"cannot rename from "
					+ from
					+ " to "
					+ to
					+ ", destination exists");
		fromfile.renameTo(tofile);
	}

	public void delete(String path) throws IOException {
		//File file = new File(root + path);
		//File file = roots.getFile(path);
		Collection files = roots.getMultipleFiles(path);
		// throws FileNotFoundException
		for (Iterator iter = files.iterator(); iter.hasNext();) {
			File file = (File) iter.next();
			if (!file.delete())
				throw new PermissionDeniedException("Cannot delete " + path);
		}
	}

	/* (non-Javadoc)
	 * @see java.rmi.server.Unreferenced#unreferenced()
	 */
	public void unreferenced() {
		logger.log(Level.WARNING, "Lost master, trying to re-register with master.");
		register();
		System.gc();
	}
}
