package net.sf.drftpd.slave;

import java.io.BufferedReader;
import java.io.EOFException;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.rmi.ConnectIOException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.server.RMISocketFactory;
import java.rmi.server.UnicastRemoteObject;
import java.rmi.server.Unreferenced;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Properties;
import java.util.Vector;
import java.util.zip.CRC32;
import java.util.zip.CheckedInputStream;

import javax.net.ssl.SSLContext;

import net.sf.drftpd.Bytes;
import net.sf.drftpd.FatalException;
import net.sf.drftpd.ObjectExistsException;
import net.sf.drftpd.PermissionDeniedException;
import net.sf.drftpd.SFVFile;
import net.sf.drftpd.master.SlaveManager;
import net.sf.drftpd.master.command.plugins.DataConnectionHandler;
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

	private SSLContext _ctx;
	private static final boolean isWin32 =
		System.getProperty("os.name").startsWith("Windows");
	private static final Logger logger =
		Logger.getLogger(SlaveImpl.class.getName());

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

	public static RootBasket getDefaultRootBasket(Properties cfg) {
		RootBasket roots;
		// START: RootBasket
		long defaultMinSpaceFree =
			Bytes.parseBytes(cfg.getProperty("slave.minspacefree", "50mb"));
		ArrayList rootStrings = new ArrayList();
		for (int i = 1; true; i++) {
			String rootString = cfg.getProperty("slave.root." + i);
			if (rootString == null)
				break;
			System.out.println("slave.root." + i + ": " + rootString);

			long minSpaceFree;
			try {
				minSpaceFree =
					Long.parseLong(
						cfg.getProperty("slave.root." + i + ".minspacefree"));
			} catch (NumberFormatException ex) {
				minSpaceFree = defaultMinSpaceFree;
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
		} catch (IOException e) {
			throw new FatalException(e);
		}
		// END: RootBasket
		return roots;
	}

	public static void main(String args[]) {
		BasicConfigurator.configure();
		System.out.println(SlaveImpl.VERSION + " slave server starting");
		String slaveconf;
		if (args.length >= 1) {
			slaveconf = args[0];
		} else {
			slaveconf = "slave.conf";
		}
		try {

			Properties cfg = new Properties();
			try {
				cfg.load(new FileInputStream(slaveconf));
			} catch (Throwable ex) {
				ex.printStackTrace();
				System.err.println(
					"Could not open " + slaveconf + ", exiting.");
				System.exit(0);
				return;
			}
			if (cfg.getProperty("slave.portfrom") != null) {
				RMISocketFactory.setSocketFactory(
					new PortRangeServerSocketFactory(
						Integer.parseInt(cfg.getProperty("slave.portfrom")),
						Integer.parseInt(cfg.getProperty("slave.portto"))));
			}

			new SlaveImpl(cfg);

		} catch (Throwable e) {
			logger.warn("Error registering", e);
			System.exit(0);
			return;
		}
	}
	private String _name;
	//private String root;
	private RootBasket _roots;

	private Vector _transfers = new Vector();
	private long _sentBytes = 0;
	private long _receivedBytes = 0;

	public static final String VERSION = "DrFTPD 0.9.0-CVS";

	public SlaveImpl(Properties cfg) throws RemoteException {
		super(0);
		try {
			_ctx = DataConnectionHandler.getSSLContext();
		} catch (Exception e) {
			logger.warn("Error loading SSLContext", e);
		}
		String slavemanagerurl;
		slavemanagerurl =
			"//"
				+ cfg.getProperty("master.host")
				+ ":"
				+ cfg.getProperty("master.bindport", "1099")
				+ "/"
				+ cfg.getProperty("master.bindname");
		_name = cfg.getProperty("slave.name");

		_roots = getDefaultRootBasket(cfg);
		try {
			SlaveManager manager;
			logger.log(Level.INFO, "Getting master reference");
			manager = (SlaveManager) Naming.lookup(slavemanagerurl);

			logger.log(
				Level.INFO,
				"Registering with master and sending filelist");

			LinkedRemoteFile slaveroot = SlaveImpl.getDefaultRoot(_roots);
			manager.addSlave(_name, this, slaveroot);

			logger.log(
				Level.INFO,
				"Finished registered with master, awaiting commands.");
		} catch (RuntimeException t) {
			logger.warn("Error registering with slave", t);
			System.exit(0);
		} catch (IOException e) {
			if (e instanceof ConnectIOException
				&& e.getCause() instanceof EOFException) {
				logger.info(
					"Check slaves.xml on the master that you are allowed to connect.");
			}
			logger.info("", e);
			System.exit(0);
		} catch (NotBoundException e) {
			logger.warn("", e);
		}
		System.gc();
	}

	/**
	 * @see net.sf.drftpd.slave.Slave#checkSum(String)
	 */
	public long checkSum(String path) throws IOException {
		logger.debug("Checksumming: " + path);
		CRC32 crc32 = new CRC32();
		InputStream in =
			new CheckedInputStream(
				new FileInputStream(_roots.getFile(path)),
				crc32);
		byte buf[] = new byte[4096];
		while (in.read(buf) != -1);
		return crc32.getValue();
	}

	public Transfer connect(InetSocketAddress addr, boolean encrypted)
		throws RemoteException {
		return new TransferImpl(new ActiveConnection(encrypted ? _ctx : null, addr), this);
	}

	public void delete(String path) throws IOException {
		Collection files = _roots.getMultipleRootsForFile(path);
		for (Iterator iter = files.iterator(); iter.hasNext();) {
			Root root = (Root) iter.next();
			File file = root.getFile(path);
			if (!file.exists()) {
				throw new FileNotFoundException(
					file.getAbsolutePath() + " does not exist.");
			}
			if (!file.delete())
				throw new PermissionDeniedException("delete failed on " + path);
			File dir = new File(file.getParentFile());

			//TODO don't go above empty root
			while (dir.list().length == 0) {
				file.delete();
				logger.debug(dir.getPath() + " - " + dir.getParent());
				java.io.File tmpFile = dir.getParentFile();
				if (tmpFile == null)
					break;
				logger.debug(
					tmpFile.getPath()
						+ ".length() <= "
						+ root.getPath()
						+ ".length()");
				if (tmpFile.getPath().length() <= root.getPath().length()) {
					break;
				}
				dir = new File(tmpFile);
			}
		}
	}

	public SFVFile getSFVFile(String path) throws IOException {
		return new SFVFile(
			new BufferedReader(new FileReader(_roots.getFile(path))));
	}

	public SlaveStatus getSlaveStatus() {
		int throughputUp = 0, throughputDown = 0;
		int transfersUp = 0, transfersDown = 0;
		long bytesReceived, bytesSent;
		synchronized (_transfers) {
			bytesReceived = _receivedBytes;
			bytesSent = _sentBytes;
			for (Iterator i = _transfers.iterator(); i.hasNext();) {
				TransferImpl transfer = (TransferImpl) i.next();
				switch (transfer.getDirection()) {
					case Transfer.TRANSFER_RECEIVING_UPLOAD :
						throughputUp += transfer.getXferSpeed();
						transfersUp += 1;
						bytesReceived += transfer.getTransfered();
						break;
					case Transfer.TRANSFER_SENDING_DOWNLOAD :
						throughputDown += transfer.getXferSpeed();
						transfersDown += 1;
						bytesSent += transfer.getTransfered();
						break;
					default :
						throw new FatalException("unrecognized direction");
				}
			}
		}
		try {
			return new SlaveStatus(
				_roots.getTotalDiskSpaceAvailable(),
				_roots.getTotalDiskSpaceCapacity(),
				bytesSent,
				bytesReceived,
				throughputUp,
				transfersUp,
				throughputDown,
				transfersDown);
		} catch (Exception ex) {
			ex.printStackTrace();
			throw new RuntimeException(ex.toString());
		}
	}

	public Transfer listen(boolean encrypted)
		throws RemoteException, IOException {
		return new TransferImpl(
			new PassiveConnection(encrypted ? _ctx : null, new InetSocketAddress(0)),
			this);
	}

	public void ping() {
	}

	public void rename(String from, String toDirPath, String toName)
		throws IOException {
		for (Iterator iter = _roots.iterator(); iter.hasNext();) {
			Root root = (Root) iter.next();

			File fromfile = root.getFile(from);
			if (!fromfile.exists())
				continue;

			File toDir = root.getFile(toDirPath);
			toDir.mkdirs();
			File tofile = new File(toDir.getPath() + File.separator + toName);
			//!win32 == true on linux
			//!win32 && equalsignore == true on win32 
			if (tofile.exists()
				&& !(isWin32 && fromfile.getName().equalsIgnoreCase(toName))) {
				throw new ObjectExistsException(
					"cannot rename from "
						+ fromfile
						+ " to "
						+ tofile
						+ ", destination exists");
			}

			if (!fromfile.renameTo(tofile)) {
				throw new IOException(
					"Rename " + fromfile + " to " + tofile + " failed");
			}
		}
	}

	public void unreferenced() {
		logger.info("unreferenced");
		System.exit(0);
	}

	public RootBasket getRoots() {
		return _roots;
	}

	public void addTransfer(TransferImpl transfer) {
		synchronized (_transfers) {
			_transfers.add(transfer);
		}
	}

	public void removeTransfer(TransferImpl transfer) {
		synchronized (_transfers) {
			switch (transfer.getDirection()) {
				case Transfer.TRANSFER_RECEIVING_UPLOAD :
					_receivedBytes += transfer.getTransfered();
					break;
				case Transfer.TRANSFER_SENDING_DOWNLOAD :
					_sentBytes += transfer.getTransfered();
					break;
				default :
					throw new IllegalArgumentException();
			}
			if (!_transfers.remove(transfer))
				throw new IllegalStateException();
		}
	}

}
