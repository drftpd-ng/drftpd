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

package org.drftpd.slave.async;

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
import java.util.Stack;

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
 * @version $Id: AsyncSlave.java,v 1.4 2004/07/14 12:52:01 teflon114 Exp $
 */
public class AsyncSlave extends Thread implements Slave, Unreferenced {
    private static final Logger logger =
    Logger.getLogger(AsyncSlave.class);
    
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
    
    protected Hashtable commands = new Hashtable();
    protected Stack availcmd = new Stack();
    
    private LinkedRemoteFile _root;
    
    public AsyncSlave(ConnectionManager mgr, Hashtable cfg)
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
    
    public AsyncSlave(ConnectionManager mgr, Hashtable cfg, Socket sock)
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
        for (int i=0; i<256; i++) {
            String key = Integer.toHexString(i);
            if (key.length()<2) key = "0" + key;
            availcmd.push(key);
            commands.put(key,null);
        }
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
                AsyncSlaveListener.invalidSlave("INITFAIL BadKey", _sock);
            }
            // generate slave hash
            pass = _spsw + sseed + _mpsw;
            md5 = MessageDigest.getInstance("MD5");
            md5.reset();
            md5.update(pass.getBytes());
            hash = hash2hex(md5.digest()).toLowerCase();
            
            // authenticate
            if (!sname.equals(_name)) {
                AsyncSlaveListener.invalidSlave("INITFAIL Unknown", _sock);
            }
            if (!spass.toLowerCase().equals(hash.toLowerCase())) {
                AsyncSlaveListener.invalidSlave("INITFAIL BadKey", _sock);
            }
            _cman.getSlaveManager().addSlave(_name, this, getSlaveStatus(), -1);
            start();
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
    // Control Socket Processing Thread
    //*********************************
    
    public void run() {
        int tick = 0;
        while (true) {
            try { sleep(100); } catch (Exception e) {}
            String msg = readLine(0);
            if (msg == null) {
                // do something to indicate the connection died
                shutdown();
                return;
            }
            while (msg != "") {
                String[] items = msg.split(",");
                AsyncCommand cmd = (AsyncCommand)commands.get(items[0]);
                msg = msg.substring(3); // strip off channel ID
                if (cmd._name.equals("ping")) processPing(cmd,msg);
                else if (cmd._name.equals("xfer")) xferMessage(msg);
                else if (cmd._name.equals("disk")) processDisk(cmd,msg);
                else if (cmd._name.equals("conn")) processConnect(cmd,msg);
                else if (cmd._name.equals("send")) processSend(cmd,msg);
                else if (cmd._name.equals("recv")) processRecv(cmd,msg);
                else if (cmd._name.equals("renm")) processRename(cmd,msg);
                else if (cmd._name.equals("dele")) processDelete(cmd,msg);
                else if (cmd._name.equals("list")) processList(cmd,msg);
                else if (cmd._name.equals("csum")) processCRC32(cmd,msg);
                else if (cmd._name.equals("dump")) processDump(cmd,msg);
                msg = readLine(0);
                if (msg == null) {
                    // do something to indicate the connection died
                    shutdown();
                    return;
                }
            }
        }
    }
    
    private void shutdown() {
        // fatal error occured, close it all down
        // notify SlaveManager that we are dead
        try {
            _cman.getSlaveManager().delSlave(_name, "Connection lost");
        } catch (Exception e) {
        }
    }
    
    //*********************************
    // Control Socket I/O Methods
    //*********************************
    
    public void sendLine(String line) {
        synchronized (_sout) {
            _sout.println(line);
            logger.info(_name + "< " + line);
        }
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
                        return "";
                    sleep(100);
                    cnt--;
                    if (cnt == 0)
                        return "";
                }
                String txt = _sinp.readLine();
                if (txt == null) return null;
                logger.info(_name + "> " + txt);
                return txt;
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
                AsyncTransfer tmp = (AsyncTransfer) _transfers.get(i);
                if (tmp.getID() == tid) {
                    // update transfer status
                    tmp.updateStats(sta, byt, crc, err, adr);
                    break;
                }
            }
        }
    }
    
    public void addTransfer(AsyncTransfer transfer) {
        synchronized (_transfers) {
            _transfers.add(transfer);
        }
    }
    
    public void removeTransfer(AsyncTransfer transfer) {
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
    // Command Response Processing Methods
    //*********************************
    
    private void processPing(AsyncCommand cmd, String res) {
        cmd.setStatus(0);
    }
    
    private void processCRC32(AsyncCommand cmd, String res) {
        if (res.startsWith("CRCFAIL ")) {
            cmd.setStatus(1);
            return;
        }
        cmd._data.put("crc32", res.substring(6));
        cmd.setStatus(0);
    }
    
    private void processDump(AsyncCommand cmd, String res) {
        if (res.startsWith("DUMPFAIL ")) {
            cmd.setStatus(1);
            return;
        }
        if (!cmd._data.containsKey("data")) {
            cmd._data.put("data", new StringBuffer(65536));
        }
        if (res.equals("DUMPEND")) {
            cmd.setStatus(0);
        }
        StringBuffer buf = (StringBuffer)cmd._data.get("data");
        buf.append(res);
    }
    
    private void processDelete(AsyncCommand cmd, String res) {
        if (res.startsWith("DELFAIL "))
            cmd.setStatus(1);
        else
            cmd.setStatus(0);
    }
    
    private void processRename(AsyncCommand cmd, String res) {
        if (res.startsWith("RENFAIL "))
            cmd.setStatus(1);
        else
            cmd.setStatus(0);
    }
    
    private void processSend(AsyncCommand cmd, String res) {
        if (res.startsWith("SENDFAIL ")) {
            cmd.setStatus(-1);
        } else {
            // parse response
            cmd.setStatus(0);
        }
    }
    
    private void processRecv(AsyncCommand cmd, String res) {
        if (res.startsWith("RECVFAIL ")) {
            cmd.setStatus(-1);
        } else {
            // parse response
            cmd.setStatus(0);
        }
    }
    
    private void processConnect(AsyncCommand cmd, String res) {
        if (res.startsWith("CONNFAIL ")) {
            cmd.setStatus(-1);
        } else {
            // parse response
            String[] items = res.split(" ");
            cmd.getData().put("conn", items[1]);
            if (items.length > 2)
                cmd.getData().put("addr", items[2]);
            cmd.setStatus(0);
        }
    }
    
    private void processDisk(AsyncCommand cmd, String res) {
        if (res.startsWith("DISKFAIL ")) {
            cmd.setStatus(-1);
        } else {
            // parse response
            String[] items = res.split(" ");
            cmd.getData().put("total", items[1]);
            cmd.getData().put("free", items[2]);
        }
    }
    
    private void processList(AsyncCommand cmd, String res) {
        if (!cmd._data.containsKey("data")) {
            cmd._data.put("data", new StringBuffer(65536));
        }
        if (res.equals("DUMPEND")) {
            cmd.setStatus(0);
        }
        StringBuffer buf = (StringBuffer)cmd._data.get("data");
        if (res.startsWith("LISTFAIL")) {
            cmd.setStatus(-1);
        } else {
            if (res.equals("LISTEND")) {
                try {
                    LinkedRemoteFile root =
                    MLSTSerialize.unserialize(
                    _cman.getConfig(),
                    new StringReader(buf.toString()),
                    _cman.getSlaveManager().getSlaveList());
                    _root = root;
                } catch (Exception e) {
                    logger.info("LIST Exception from " + getName(), e);
                }
                return;
            }
            if (res.equals("LISTBEGIN")) {
                return;
            }
            if (!res.startsWith("/")
            && !res.equals("")
            && res.indexOf("type=dir;") == -1)
                buf.append("x.slaves=" + _name + ";");
            buf.append(res);
            buf.append((String) "\n");
        }
    }
    
    
    //*********************************
    // Synchronous action methods
    //*********************************
    
    public AsyncCommand sendCommand(String cmd, String args) {
        while (availcmd.size() == 0) {
            try { sleep(100); } catch (Exception e) {}
        }
        String chan = (String)availcmd.pop();
        AsyncCommand tmp = new AsyncCommand(chan, cmd, args, this);
        commands.put(chan, tmp);
        String msg = chan + " " + cmd + " " + args;
        sendLine(msg);
        return tmp;
    }
    
    public void releaseChan(String chan)
    {
        availcmd.push(chan);
        commands.put(chan, null);
    }
    
    public void waitForCommand(AsyncCommand cmd) {
        cmd.waitForComplete();
    }
    
    public void ping() {
        logger.debug("Trying PING");
        AsyncCommand cmd = sendCommand("ping", "");
        waitForCommand(cmd);
        return;
    }
    
    public long checkSum(String path) throws IOException {
        AsyncCommand cmd = sendCommand("csum", "\"" + path + "\"");
        waitForCommand(cmd);
        return Integer.parseInt((String) cmd._data.get("crc32"));
    }
    
    public String dumpfile(String path) throws IOException {
        AsyncCommand cmd = sendCommand("dump", "\"" + path + "\"");
        waitForCommand(cmd);
        return (String) cmd._data.get("data");
    }
    
    public void delete(String path) throws IOException {
        AsyncCommand cmd = sendCommand("dele", "\"" + path + "\"");
        waitForCommand(cmd);
    }
    
    public void rename(String from, String toDirPath, String toName)
    throws IOException {
        AsyncCommand cmd = sendCommand("renm", "\"" + from + "\" "+ toDirPath + "/" + toName + "\"");
        waitForCommand(cmd);
    }
    
    public SFVFile getSFVFile(String path) throws IOException {
        String sfv = dumpfile(path);
        return new SFVFile(new BufferedReader(new StringReader(sfv)));
    }
    
	public ID3v1Tag getID3v1Tag(String path) throws IOException {
		return null;
	}

    public Transfer connect(InetSocketAddress addr, boolean encrypted) {
        AsyncCommand cmd = sendCommand("conn", addr.getAddress().getHostAddress());
        waitForCommand(cmd);
        Transfer tmp = (Transfer) new AsyncTransfer(this, cmd);
        return tmp;
    }
    
    public Transfer listen(boolean encrypted)
    throws RemoteException, IOException {
        AsyncCommand cmd = sendCommand("conn", "");
        waitForCommand(cmd);
        Transfer tmp = (Transfer) new AsyncTransfer(this, cmd);
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
                AsyncTransfer transfer = (AsyncTransfer) i.next();
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
        AsyncCommand cmd = sendCommand("disk", "");
        waitForCommand(cmd);
        Hashtable result = cmd._data;
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
        AsyncCommand cmd = sendCommand("list", "");
        waitForCommand(cmd);
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
