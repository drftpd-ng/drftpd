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
import java.rmi.server.UnicastRemoteObject;
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
import net.sf.drftpd.remotefile.FileRemoteFile;
import net.sf.drftpd.remotefile.LinkedRemoteFile;
import net.sf.drftpd.remotefile.StaticRemoteFile;

import se.mog.io.File;

/**
 * @author <a href="mailto:drftpd@mog.se">Morgan Christiansson</a>
 */
public class SlaveImpl extends UnicastRemoteObject implements Slave {
	private Vector transfers = new Vector();
	private static Logger logger = Logger.getLogger(SlaveImpl.class.getName());
	static {
		logger.setLevel(Level.FINEST);
	}
	//Properties cfg;

	SlaveManager slavemanager;
	//private String root;
	private RootBasket roots;

	public SlaveImpl(Properties cfg) throws RemoteException {
		super();
		/*
		StringTokenizer st =
			new StringTokenizer(cfg.getProperty("slave.roots"), ",;");
		Vector rootCollection = new Vector();
		while (st.hasMoreTokens()) {
			rootCollection.add(st.nextToken());
		}
		*/

		try {
			roots = new RootBasket(cfg.getProperty("slave.roots"));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			System.exit(0);
			return;
		}
	}

	public static void main(String args[]) {
		try {

			Properties cfg = new Properties();
			try {
				cfg.load(new FileInputStream("drftpd.conf"));
			} catch (Throwable ex) {
				ex.printStackTrace();
				System.err.println("Could not open drftpd.conf, exiting.");
				System.exit(0);
				return;
			}

			SlaveManager manager;
			try {
				manager =
					(SlaveManager) Naming.lookup(
						cfg.getProperty("slavemanager.url"));
			} catch (Throwable ex) {
				ex.printStackTrace();
				System.out.println(
					"Could not lookup slavemanager.url, this is a critical task, exiting.");
				System.exit(0);
				return;
			}

			Slave slave;
			try {
				slave = new SlaveImpl(cfg);
			} catch (Throwable ex) {
				ex.printStackTrace();
				System.exit(0);
				return;
			}

			try {
				LinkedRemoteFile slaveroot =
					SlaveImpl.getDefaultRoot(cfg.getProperty("slave.roots"));

				System.out.println("manager.addSlave() root: " + slaveroot);
				manager.addSlave(
					cfg.getProperty("slave.name"),
					slave,
					slaveroot);
			} catch (RemoteException ex) {
				ex.printStackTrace();
				System.exit(0);
				return;
			} catch (IOException ex) {
				ex.printStackTrace();
				return;
			}
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
			new LinkedRemoteFile(new FileRemoteFile(rootBasket));

		return linkedroot;
	}

	public SlaveStatus getSlaveStatus() {
		int throughputUp = 0, throughputDown = 0;
		int transfersUp = 0, transfersDown = 0;

		for (Iterator i = transfers.iterator(); i.hasNext();) {
			TransferImpl transfer = (TransferImpl) i.next();
			System.out.println("transfer: "+transfer);
			if (transfer.getDirection() == Transfer.TRANSFER_RECEIVING_UPLOAD) {
				throughputUp += transfer.getTransferSpeed();
				transfersUp += 1;
			} else if(transfer.getDirection() == Transfer.TRANSFER_SENDING_DOWNLOAD) {
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
		StaticRemoteFile remotefile,
		char mode,
		long offset,
		Connection conn)
		throws IOException {
		//File file = new File(root + remotefile.getPath());
		File file = roots.getFile(remotefile.getPath());
		//throws FileNotFoundException
		//		if (!file.exists())
		//			throw new FileNotFoundException(
		//				"File " + file + " not found, Remotefile: " + remotefile);

		FileInputStream in = new FileInputStream(file);
		in.skip(offset);

		TransferImpl transfer = new TransferImpl(transfers, in, conn, mode);
		return transfer;
	}

	/**
	 * @see net.sf.drftpd.slave.Slave#doConnectSend(REmoteFile, long, InetADdress, int)
	 */
	public Transfer doConnectSend(
		StaticRemoteFile rfile,
		char mode,
		long offset,
		InetAddress addr,
		int port)
		throws IOException {

		return doSend(rfile, mode, offset, new ActiveConnection(addr, port));
	}

	/**
	 * @see net.sf.drftpd.slave.Slave#doListenSend(RemoteFile, long, int)
	 */
	public Transfer doListenSend(
		StaticRemoteFile remotefile,
		char mode,
		long offset)
		throws IOException {

		return doSend(remotefile, mode, offset, new PassiveConnection());
	}

	//RECEIVE
	/**
	 * Generic receive method.
	 */
	private Transfer doReceive(
		String dirname,
		String filename,
		long offset,
		Connection conn)
		throws IOException {

		String root = roots.getARoot().getPath();
		new File(root + dirname).mkdirs();
		File file =
			new File(
				root + File.separator + dirname + File.separator + filename);
		System.out.println("Will write " + file);
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
		long offset,
		InetAddress addr,
		int port)
		throws IOException {

		//Socket sock = new Socket(addr, port);
		return doReceive(
			dirname,
			filename,
			offset,
			new ActiveConnection(addr, port));
	}

	/**
	 * @see net.sf.drftpd.slave.Slave#doListenReceive(RemoteFile, long, int)
	 */
	public Transfer doListenReceive(
		String dirname,
		String filename,
		long offset)
		throws IOException {
		//		ServerSocket server = new ServerSocket(0, 1);
		//server.setSoTimeout(xxx);
		//		Socket sock = server.accept();
		//		server.close();
		return doReceive(dirname, filename, offset, new PassiveConnection());
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
		System.out.println("rename from " + from + " to " + to);
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
}
