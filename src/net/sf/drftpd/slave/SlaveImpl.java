package net.sf.drftpd.slave;

import net.sf.drftpd.master.SlaveManager;
import net.sf.drftpd.PermissionDeniedException;
import net.sf.drftpd.RemoteFile;
import net.sf.drftpd.LinkedRemoteFile;
import net.sf.drftpd.DrftpdFileFilter;
import net.sf.drftpd.RemoteSlave;

import java.rmi.server.UnicastRemoteObject;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.NotBoundException;

import java.util.Properties;
import java.util.Hashtable;
import java.util.Map;
import java.util.Stack;
import java.util.EmptyStackException;
import java.util.zip.CRC32;
import java.util.zip.CheckedOutputStream;

import java.io.InputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedInputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import java.net.ConnectException;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.InetAddress;

public final class SlaveImpl extends UnicastRemoteObject implements Slave {

	Properties cfg;
	SlaveManager manager;

	public SlaveImpl(Properties cfg) throws RemoteException {
		super();
		this.cfg = cfg;
	}

	public static void main(String args[]) {
		RemoteSlave slave;
		Properties cfg = new Properties();
		try {
		cfg.load(new FileInputStream("drftpd.conf"));
		} catch(IOException ex) {
			ex.printStackTrace();
		}

		try {
			slave = new RemoteSlave(new SlaveImpl(cfg));
		} catch (RemoteException ex) {
			ex.printStackTrace();
			System.exit(0);
			return;
			//the compiler doesn't know that execution stops at System.exit() stops execution
		}
		
		LinkedRemoteFile root = getDefaultRoot(cfg, slave);
		
		SlaveManager manager;
		try {
			manager =
				(SlaveManager) Naming.lookup(
					cfg.getProperty("slavemanager.url"));
		} catch(Exception ex) {
			ex.printStackTrace();
			return;
		}
		try {
			System.out.println("manager.addSlave() root: "+root);
			manager.addSlave(slave, root);
		} catch(RemoteException ex) {
			ex.printStackTrace();
		}
	}

	public static LinkedRemoteFile getDefaultRoot(Properties cfg, RemoteSlave slave) {
			File root = new File(cfg.getProperty("slave.root"));
			if (!root.isDirectory()) {
				System.out.println(
					"slave.root = " + root.getPath() + " is not a directory!");
				System.exit(-1);
				return null;
			}
			LinkedRemoteFile rroot = new LinkedRemoteFile(slave, root);
			return rroot;
	}
	//public void doPassiveTransfer(RemoteFile file) {}

	public void doConnectSend(
		RemoteFile rfile,
		long offset,
		InetAddress address,
		int port)
		throws FileNotFoundException, ConnectException {
//		doConnectSend(file.getPath(), offset, address, port);

		File file = new File(cfg.getProperty("slave.root") + rfile.getPath());
		if(!file.exists()) throw new FileNotFoundException("File "+file+" not found, Remotefile: "+rfile);
		try {
			Socket sock;
			sock = new Socket(address, port);
			FileInputStream is = new FileInputStream(file);
			is.skip(offset);
			OutputStream os = sock.getOutputStream();

			transfer(is, os);
			sock.close();
		} catch (ConnectException ex) {
			System.err.println(
				"Error connecting to "
					+ address
					+ ":"
					+ port
					+ " to send "
					+ file.getPath());
		} catch (Throwable ex) {
			ex.printStackTrace();
		}
	}

	public void doConnectSend(
		String path,
		long offset,
		InetAddress address,
		int port)
		throws FileNotFoundException, ConnectException {

	}

	private long transfer(InputStream is, OutputStream os) throws IOException {
		long transfered = 0;
		try {
			byte[] buff = new byte[1024];
			int count;
			while ((count = is.read(buff)) != -1) {
				transfered += count;
				os.write(buff, 0, count);
			}
			os.close();
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		return transfered;
	}

	/**
	 * @see net.sf.drftpd.slave.Slave#doConnectReceive(String, InetAddress, int)
	 * @author mog
	 */
	public void doConnectReceive(
		RemoteFile remotefile,
		InetAddress addr,
		int port)
		throws RemoteException, PermissionDeniedException {

		File file = new File(remotefile.getPath());
		FileOutputStream os;
		try {
			os = new FileOutputStream(file);
		} catch (FileNotFoundException ex) {
			throw new PermissionDeniedException(ex.toString());
		}

		CRC32 checksum = new CRC32();
		CheckedOutputStream cos = new CheckedOutputStream(os, checksum);

		try {
			Socket socket = new Socket(addr, port);
			InputStream is = socket.getInputStream();
			transfer(is, cos);
		} catch (IOException ex) {
			ex.printStackTrace();
		}
	}

}
