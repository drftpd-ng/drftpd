/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * DrFTPD is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with DrFTPD; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package org.drftpd.slave.socket;

import java.io.BufferedReader;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.rmi.ConnectIOException;
import java.rmi.RemoteException;
import java.rmi.server.Unreferenced;
import java.security.MessageDigest;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Random;
import java.util.Vector;

import net.sf.drftpd.FatalException;
import net.sf.drftpd.SFVFile;
import net.sf.drftpd.master.ConnectionManager;
import net.sf.drftpd.remotefile.LinkedRemoteFile;
import net.sf.drftpd.remotefile.MLSTSerialize;
import net.sf.drftpd.slave.RootBasket;
import net.sf.drftpd.slave.Slave;
import net.sf.drftpd.slave.SlaveStatus;
import net.sf.drftpd.slave.Transfer;
import net.sf.drftpd.util.PortRange;

import org.apache.log4j.Logger;
import de.hampelratte.id3.ID3v1Tag;

/**
 * @author mog
 * @version $Id: SocketSlaveImpl.java,v 1.3 2004/07/14 12:52:01 teflon114 Exp $
 */
public class SocketSlaveImpl extends Thread implements Slave, Unreferenced {
	private static final Logger logger =
		Logger.getLogger(SocketSlaveImpl.class);

	private long disktotal = 0;
	private long diskfree = 0;

	private ConnectionManager _cman;

	private boolean _uploadChecksums;
	private boolean _downloadChecksums;

	private String _name;
	private String _spsw;
	private String _mpsw;
	private String _host;
	private int _port;

	private PortRange _portRange = new PortRange();

	private long _receivedBytes = 0;
	private long _sentBytes = 0;

	private Vector _transfers = new Vector();

	private Socket _sock = null;
	private BufferedReader _sinp = null;
	private PrintWriter _sout = null;

	private Vector _que = new Vector();

	private LinkedRemoteFile _root;
        
        public SocketSlaveImpl(ConnectionManager mgr, Hashtable cfg)
		throws RemoteException {
		Socket sock = null;
		_name = (String) cfg.get("name");
		_spsw = (String) cfg.get("slavepass");
		_mpsw = (String) cfg.get("masterpass");
		_host = (String) cfg.get("addr");
		_port = Integer.parseInt((String) cfg.get("port"));
		logger.info("Starting connect to " + _name + "@" + _host + ":" + _port);
		try {
			sock = new java.net.Socket(_host, _port);
		} catch (IOException e) {
			if (e instanceof ConnectIOException
				&& e.getCause() instanceof EOFException) {
				logger.info(
					"Check slaves.xml on the master that you are allowed to connect.");
			}
			logger.info("IOException: " + e.toString(), e);
			try {
				sock.close();
			} catch (Exception e1) {
			}
			//System.exit(0);
		} catch (Exception e) {
			logger.warn("Exception: " + e.toString());
			try {
				if (sock != null)
					sock.close();
			} catch (Exception e2) {
			}
		}
		init(mgr, cfg, sock);
	}

	public SocketSlaveImpl(ConnectionManager mgr, Hashtable cfg, Socket sock)
		throws RemoteException {
		_name = (String) cfg.get("name");
		_spsw = (String) cfg.get("slavepass");
		_mpsw = (String) cfg.get("masterpass");
		_host = (String) cfg.get("addr");
		_port = Integer.parseInt((String) cfg.get("port"));
		init(mgr, cfg, sock);
	}

	public void init(ConnectionManager mgr, Hashtable cfg, Socket sock)
		throws RemoteException {
		_cman = mgr;
		_sock = sock;
		try {
			_sout = new PrintWriter(_sock.getOutputStream(), true);
			_sinp =
				new BufferedReader(
					new InputStreamReader(_sock.getInputStream()));

			// generate master hash
			String seed = "";
			Random rand = new Random();
			for (int i = 0; i < 16; i++) {
				String hex = Integer.toHexString(rand.nextInt(256));
				if (hex.length() < 2)
					hex = "0" + hex;
				seed += hex.substring(hex.length() - 2);
			}
			String pass = _mpsw + seed + _spsw;
			MessageDigest md5 = MessageDigest.getInstance("MD5");
			md5.reset();
			md5.update(pass.getBytes());
			String hash = hash2hex(md5.digest()).toLowerCase();

			String banner = "INIT " + "servername" + " " + hash + " " + seed;

			sendLine(banner);

			// get slave banner
			String txt = readLine(5);
			if (txt == null) {
				throw new IOException("Slave did not send banner !!");
			}

			String sname = "";
			String spass = "";
			String sseed = "";
			try {
				String[] items = txt.split(" ");
				sname = items[1].trim();
				spass = items[2].trim();
				sseed = items[3].trim();
			} catch (Exception e) {
				SocketSlaveListener.invalidSlave("INITFAIL BadKey", _sock);
			}
			// generate slave hash
			pass = _spsw + sseed + _mpsw;
			md5 = MessageDigest.getInstance("MD5");
			md5.reset();
			md5.update(pass.getBytes());
			hash = hash2hex(md5.digest()).toLowerCase();

			// authenticate
			if (!sname.equals(_name)) {
				SocketSlaveListener.invalidSlave("INITFAIL Unknown", _sock);
			}
			if (!spass.toLowerCase().equals(hash.toLowerCase())) {
				SocketSlaveListener.invalidSlave("INITFAIL BadKey", _sock);
			}
			start();
			_cman.getSlaveManager().addSlave(_name, this, getSlaveStatus(), -1);
		} catch (IOException e) {
			if (e instanceof ConnectIOException
				&& e.getCause() instanceof EOFException) {
				logger.info(
					"Check slaves.xml on the master that you are allowed to connect.");
			}
			logger.info("IOException: " + e.toString());
			try {
				sock.close();
			} catch (Exception e1) {
			}
			//System.exit(0);
		} catch (Exception e) {
			logger.warn("Exception: " + e.toString());
			try {
				sock.close();
			} catch (Exception e2) {
			}
		}
		System.gc();
	}

	private String hash2hex(byte[] bytes) {
		String res = "";
		for (int i = 0; i < 16; i++) {
			String hex = Integer.toHexString((int) bytes[i]);
			if (hex.length() < 2)
				hex = "0" + hex;
			res += hex.substring(hex.length() - 2);
		}
		return res;
	}

	//*********************************
	// General property accessors
	//*********************************

	public InetAddress getAddress() {
		return _sock.getInetAddress();
	}

	public boolean getDownloadChecksums() {
		return _downloadChecksums;
	}

	public boolean getUploadChecksums() {
		return _uploadChecksums;
	}

	public RootBasket getRoots() {
		return null;
	}

	//*********************************
	// Command Queue Element
	//*********************************

	private class Command {
		String _name;
		Hashtable _args;
		int _stat;
		Hashtable _data = new Hashtable();
		boolean _done = false;

		public Command(String name, Hashtable args) {
			_name = name;
			_args = args;
			_stat = 0;
		}

		public String getName() {
			return _name;
		}
		public Hashtable getArgs() {
			return _args;
		}
		public Hashtable getData() {
			return _data;
		}
		public int getStatus() {
			return _stat;
		}
		public void setStatus(int stat) {
			_stat = stat;
		}
		public void finished() {
			_done = true;
		}
	}

	//*********************************
	// Control Socket Processing Thread
	//*********************************

	public void run() {
		int tick = 0;
		while (true) {
			while (true) {
				try {
					synchronized (_que) {
						sleep(100);
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
				if (_que.size() != 0) {
					runque();
					tick = 0;
					break;
				}
				readLine(0);
				tick++;
				if (tick < 300)
					continue;
				tick = 0;
				_sout.println("ping");
				logger.info(_name + "< ping");
				if (readLine(5) == null) {
					// do something to indicate the connection died
					shutdown(null);
					return;
				}
			}
		}
	}

	private void runque() {
		// process command queue
		while (_que.size() > 0) {
			Command cmd = (Command) _que.get(0);
			_que.remove(0);
			try {
				process(cmd);
				cmd.finished();
			} catch (Exception e) {
				logger.info(_name + "$ exception=" + e.toString());
				// do something to indicate the connection died
				shutdown(cmd);
				return;
			}
		}
	}

	private void shutdown(Command cmd) {
		// fatal error occured, close it all down
		if (cmd != null) {
			cmd.setStatus(-1);
			cmd.finished();
		}
		// clear the queue
		while (_que.size() > 0) {
			cmd = (Command) _que.get(0);
			_que.remove(0);
			cmd.setStatus(-1);
			cmd.finished();
		}
		// notify SlaveManager that we are dead
		try {
			_cman.getSlaveManager().delSlave(_name, "Connection lost");
		} catch (Exception e) {
		}
	}

	private void process(Command cmd) {
		if (cmd.getName().equals("ping")) {
			processPing(cmd);
			return;
		}
		if (cmd.getName().equals("send")) {
			processSend(cmd);
			return;
		}
		if (cmd.getName().equals("recv")) {
			processRecv(cmd);
			return;
		}
		if (cmd.getName().equals("dump")) {
			processDump(cmd);
			return;
		}
		if (cmd.getName().equals("csum")) {
			processCRC32(cmd);
			return;
		}
		if (cmd.getName().equals("renm")) {
			processRename(cmd);
			return;
		}
		if (cmd.getName().equals("dele")) {
			processDelete(cmd);
			return;
		}
		if (cmd.getName().equals("conn")) {
			processConnect(cmd);
			return;
		}
		if (cmd.getName().equals("disk")) {
			processDisk(cmd);
			return;
		}
		if (cmd.getName().equals("list")) {
			processList(cmd);
			return;
		}
	}

	//*********************************
	// Control Socket I/O Methods
	//*********************************

	private void sendLine(String line) {
		_sout.println(line);
		logger.info(_name + "< " + line);
	}

	private String readLine() {
		return readLine(-1);
	}

	private String readLine(int secs) {
		int cnt = secs * 10;
		try {
			while (true) {
				while (!_sinp.ready()) {
					if (cnt < 1)
						return null;
					sleep(100);
					cnt--;
					if (cnt == 0)
						return null;
				}
				String txt = _sinp.readLine();
				logger.info(_name + "> " + txt);
				if (txt.startsWith("XFER")) {
					xferMessage(txt);
				} else {
					return txt;
				}
			}
		} catch (Exception e) {
			return null;
		}
	}

	//*********************************
	// Active Transfer Managment Methods
	//*********************************

	private void xferMessage(String msg) {
		String[] items = msg.split(" ");
		String xid = items[1];
		String sta = items[2];
		String cnt = items[3];
		String sum = items[4];
		String eid = items[5];
		String adr = items[6];

		long tid = Long.parseLong(xid);
		long byt = Long.parseLong(cnt);
		long err = Long.parseLong(eid);
		long crc = Long.parseLong(sum, 16);

		synchronized (_transfers) {
			for (int i = 0; i < _transfers.size(); i++) {
				SocketTransferImpl tmp = (SocketTransferImpl) _transfers.get(i);
				if (tmp.getID() == tid) {
					// update transfer status
					tmp.updateStats(sta, byt, crc, err, adr);
					break;
				}
			}
		}
	}

	public void addTransfer(SocketTransferImpl transfer) {
		synchronized (_transfers) {
			_transfers.add(transfer);
		}
	}

	public void removeTransfer(SocketTransferImpl transfer) {
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

	//*********************************
	// Command Queue Processing Methods
	//*********************************

	private void processPing(Command cmd) {
		sendLine("ping");
		if (readLine(5) == null)
			cmd.setStatus(-1);
	}

	private void processCRC32(Command cmd) {
		String msg = "crc32 \"" + cmd.getArgs().get("path") + "\"";
		sendLine(msg);
		String res;
		if ((res = readLine(5)) == null) {
			cmd.setStatus(-1);
		} else {
			// parse response
			if (res.startsWith("CRCFAIL ")) {
				cmd.setStatus(1);
				return;
			}
			cmd._data.put("crc32", res.substring(6));
		}
	}

	private void processDump(Command cmd) {
		String msg = "dump \"" + cmd.getArgs().get("path") + "\"";
		sendLine(msg);
		String res;
		if ((res = readLine(5)) == null) {
			cmd.setStatus(-1);
		} else {
			// parse response
			if (res.startsWith("DUMPFAIL ")) {
				cmd.setStatus(1);
				return;
			}
			StringBuffer buf = new StringBuffer(65536);
			while ((res = readLine(5)) != null) {
				if (res.equals("DUMPEND"))
					break;
				buf.append(res);
			}
			if (res == null)
				cmd.setStatus(1);
			cmd._data.put("data", new String(Base64.decode(buf.toString())));
		}
	}

	private void processDelete(Command cmd) {
		String msg = "del \"" + cmd.getArgs().get("path") + "\"";
		sendLine(msg);
		String res;
		if ((res = readLine(5)) == null) {
			cmd.setStatus(-1);
		} else {
			// parse response
			if (res.startsWith("DELFAIL "))
				cmd.setStatus(1);
		}
	}

	private void processRename(Command cmd) {
		String msg =
			"ren "
				+ "\""
				+ cmd.getArgs().get("from")
				+ "\" "
				+ "\""
				+ cmd.getArgs().get("path")
				+ "/"
				+ cmd.getArgs().get("name")
				+ "\"";
		sendLine(msg);
		String res;
		if ((res = readLine(5)) == null) {
			cmd.setStatus(-1);
		} else {
			// parse response
			if (res.startsWith("RENFAIL "))
				cmd.setStatus(1);
		}
	}

	private void processSend(Command cmd) {
		String msg =
			"send"
				+ " \""
				+ cmd.getArgs().get("path")
				+ "\""
				+ " "
				+ cmd.getArgs().get("conn")
				+ " "
				+ cmd.getArgs().get("offs");
		sendLine(msg);
		String res;
		if ((res = readLine(5)) == null || res.startsWith("SENDFAIL")) {
			cmd.setStatus(-1);
		} else {
			// parse response
		}
	}

	private void processRecv(Command cmd) {
		String msg =
			"recv"
				+ " \""
				+ cmd.getArgs().get("path")
				+ "\""
				+ " "
				+ cmd.getArgs().get("conn")
				+ " "
				+ cmd.getArgs().get("offs");
		sendLine(msg);
		String res;
		if ((res = readLine(5)) == null || res.startsWith("RECVFAIL")) {
			cmd.setStatus(-1);
		} else {
			// parse response
		}
	}

	private void processConnect(Command cmd) {
		String msg = "conn";
		if (cmd.getArgs().containsKey("addr")) {
			msg = msg + " " + cmd.getArgs().get("addr");
		}
		sendLine(msg);
		String res;
		if ((res = readLine(5)) == null || res.startsWith("CONNFAIL")) {
			cmd.setStatus(-1);
		} else {
			// parse response
			String[] items = res.split(" ");
			cmd.getData().put("conn", items[1]);
			if (items.length > 2)
				cmd.getData().put("addr", items[2]);
		}
	}

	private void processDisk(Command cmd) {
		String msg = "disk";
		sendLine(msg);
		String res;
		if ((res = readLine(5)) == null || res.startsWith("DISKFAIL")) {
			cmd.setStatus(-1);
		} else {
			// parse response
			String[] items = res.split(" ");
			cmd.getData().put("total", items[1]);
			cmd.getData().put("free", items[2]);
		}
	}

	private void processList(Command cmd) {
		String msg = "list";
		sendLine(msg);
		String txt;
		StringBuffer sbuf = new StringBuffer(65536);
		if ((txt = readLine(20)) == null || txt.startsWith("LISTFAIL")) {
			cmd.setStatus(-1);
		} else {
			while ((txt = readLine(20)) != null) {
				if (txt.equals("LISTEND"))
					break;
				if (txt.equals("LISTBEGIN"))
					continue;
				if (!txt.startsWith("/")
					&& !txt.equals("")
					&& txt.indexOf("type=dir;") == -1)
					sbuf.append("x.slaves=" + _name + ";");
				sbuf.append(txt);
				sbuf.append((String) "\n");
			}
			try {
				LinkedRemoteFile root =
					MLSTSerialize.unserialize(
						_cman.getConfig(),
						new StringReader(sbuf.toString()),
						_cman.getSlaveManager().getSlaveList());
				_root = root;
			} catch (Exception e) {
				logger.info("LIST Exception from " + getName(), e);
			}
		}
	}

	//*********************************
	// Thread-Safe action methods
	//*********************************

	public Hashtable doCommand(String name, Hashtable args) {
		Command cmd = new Command(name, args);
		_que.add(cmd);
		while (!cmd._done) {
			try {
				sleep(100);
			} catch (Exception e) {
			}
		}
		if (cmd.getStatus() == -1)
			return null;
		return cmd.getData();
	}

	public void ping() {
		Hashtable args = new Hashtable();

		logger.debug("Trying PING");
		Hashtable result = doCommand("ping", args);
		return;
	}

	public long checkSum(String path) throws IOException {
		Hashtable args = new Hashtable();

		logger.debug("Checksumming: " + path);
		args.put("path", path);
		Hashtable result = doCommand("csum", args);
		if (result == null)
			throw new IOException("Checksum command failed");
		return Integer.parseInt((String) result.get("crc32"));
	}

	public String dumpfile(String path) throws IOException {
		Hashtable args = new Hashtable();

		logger.debug("Retrieving: " + path);
		args.put("path", path);
		Hashtable result = doCommand("dump", args);
		if (result == null)
			throw new IOException("Dump command failed");
		return (String) result.get("data");
	}

	public void delete(String path) throws IOException {
		Hashtable args = new Hashtable();

		logger.debug("Deleting: " + path);
		args.put("path", path);
		Hashtable result = doCommand("dele", args);
		if (result == null)
			throw new IOException("Delete command failed");
		return;
	}

	public void rename(String from, String toDirPath, String toName)
		throws IOException {
		Hashtable args = new Hashtable();

		logger.debug("Renaming: " + from + " -> " + toDirPath + "/" + toName);
		args.put("from", from);
		args.put("path", toDirPath);
		args.put("name", toName);
		Hashtable result = doCommand("renm", args);
		if (result == null)
			throw new IOException("Rename command failed");
		return;
	}

	public SFVFile getSFVFile(String path) throws IOException {
		String sfv = dumpfile(path);
		return new SFVFile(new BufferedReader(new StringReader(sfv)));
	}

	public ID3v1Tag getID3v1Tag(String path) throws IOException {
		return null;
	}

	public Transfer connect(InetSocketAddress addr, boolean encrypted)
		throws RemoteException {
		Hashtable args = new Hashtable();

		logger.debug("Connect: " + addr.toString());
		args.put("addr", addr.toString().substring(1));
		Hashtable result = doCommand("conn", args);
		if (result == null)
			return null;
		Transfer tmp = (Transfer) new SocketTransferImpl(this, result);
		return tmp;
	}

	public Transfer listen(boolean encrypted)
		throws RemoteException, IOException {
		Hashtable args = new Hashtable();

		logger.debug("Listen: ");
		Hashtable result = doCommand("conn", args);
		if (result == null)
			return null;
		Transfer tmp = (Transfer) new SocketTransferImpl(this, result);
		return tmp;
	}

	public SlaveStatus getSlaveStatus() {
		int throughputUp = 0, throughputDown = 0;
		int transfersUp = 0, transfersDown = 0;
		long bytesReceived, bytesSent;
		synchronized (_transfers) {
			bytesReceived = _receivedBytes;
			bytesSent = _sentBytes;
			for (Iterator i = _transfers.iterator(); i.hasNext();) {
				SocketTransferImpl transfer = (SocketTransferImpl) i.next();
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
		Hashtable args = new Hashtable();
		logger.debug("Status: ");
		Hashtable result = doCommand("disk", args);
		long dtotal, dfree;
		if (result != null) {
			disktotal = Long.parseLong(result.get("total").toString());
			diskfree = Long.parseLong(result.get("free").toString());
		}
		try {
			return new SlaveStatus(
				diskfree,
			//_roots.getTotalDiskSpaceAvailable(),
			disktotal, //_roots.getTotalDiskSpaceCapacity(),
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

	public LinkedRemoteFile getSlaveRoot() throws IOException {
		Hashtable args = new Hashtable();
		logger.debug("List: ");
		Hashtable result = doCommand("list", args);
		return _root;
	}

	//*********************************
	// Leftovers from original SlaveImpl
	//*********************************

	public void unreferenced() {
		logger.info("unreferenced");
		System.exit(0);
	}

	public InetAddress getPeerAddress() {
		try {
			return InetAddress.getLocalHost();
		} catch (Exception e) {
			throw new RuntimeException();
		}
	}

}
