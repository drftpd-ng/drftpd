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

import java.io.IOException;
import java.net.InetAddress;
import java.rmi.RemoteException;
import java.util.Hashtable;

import net.sf.drftpd.slave.Transfer;
import net.sf.drftpd.slave.TransferStatus;

import org.apache.log4j.Logger;

/**
 * @author mog
 * @version $Id: AsyncTransfer.java,v 1.2 2004/05/21 18:42:01 zombiewoof64 Exp $
 */
public class AsyncTransfer implements Transfer {
    
    private static final Logger logger = Logger.getLogger(AsyncTransfer.class);
    
    private long        _conn;
    private String      _addr;
    private int         _port;
    
    private boolean     _abort = false;
    
    private char        _direction;
    private long        _started = 0;
    private long        _finished = 0;
    private long        _transfered = 0;
    private long        _checksum = 0;
    private long        _error = 0;
    private String      _status = "";
    
    private AsyncSlave      _slave;
    private AsyncCommand    _cmd;
    
    /**
     * Start undefined passive transfer.
     */
    public AsyncTransfer(AsyncSlave slave, AsyncCommand cmd)
    {
        Hashtable data = cmd._data;
        _slave = slave;
        _direction = Transfer.TRANSFER_UNKNOWN;
        _conn = Long.parseLong((String)data.get("conn"));
        _port = 0;
        _addr = "";
        if (data.containsKey("addr")) {
            String tmp = (String)data.get("addr");
            String[] items = tmp.split(":");
            _addr = items[0];
            _port = Integer.parseInt(items[1]);
        }
    }
    
    public long getChecksum() {
        return _checksum;
    }
    
    public char getDirection() {
        return _direction;
    }
    
    public int getLocalPort() throws RemoteException {
        return _port;
    }
    
    public long getID() {
        return _conn;
    }
    
    public long getTransfered() {
        return _transfered;
    }
    
    public long getElapsed() {
        if (_finished == 0) {
            return System.currentTimeMillis() - _started;
        } else {
            return _finished - _started;
        }
    }
    
    public int getXferSpeed() {
        long elapsed = getElapsed();
        
        if (_transfered == 0) {
            return 0;
        }
        
        if (elapsed == 0) {
            return 0;
        }
        return (int) (_transfered / ((float) elapsed / (float) 1000));
    }
    
    public TransferStatus getStatus() {
        try {
            return new TransferStatus(getElapsed(), getTransfered(), getChecksum(), InetAddress.getLocalHost());
        } catch (Exception e) {
            return null;
        }
    }
    
    public boolean isReceivingUploading() {
        return _direction == TRANSFER_RECEIVING_UPLOAD;
    }
    
    public boolean isSendingUploading() {
        return _direction == Transfer.TRANSFER_SENDING_DOWNLOAD;
    }
    
    public TransferStatus sendFile(
    String path,
    char type,
    long resumePosition
    ) throws IOException {
        logger.info("Send: path=" + path);
      
        _direction = TRANSFER_SENDING_DOWNLOAD;
        _cmd = _slave.sendCommand(
        "send", "\"" + path + "\" " + resumePosition + " " + _conn 
        );
        _started = System.currentTimeMillis();
        _slave.addTransfer(this);
        _cmd.waitForComplete();
        _slave.removeTransfer(this);
        TransferStatus tmp = getStatus();
        return tmp;
    }
    
    public synchronized TransferStatus receiveFile(
    String dirname,
    char mode,
    String filename,
    long offset
    ) throws IOException {
        logger.info("Recv: path=" + dirname + "/" + filename);
        
        _direction = TRANSFER_RECEIVING_UPLOAD;
        String args = "\"" + dirname + "/" + filename + "\" " + offset + " " + _conn;
        _cmd = _slave.sendCommand("recv", args);
        _started = System.currentTimeMillis();
        _slave.addTransfer(this);
        _cmd.waitForComplete();
        _slave.removeTransfer(this);
        TransferStatus tmp = getStatus();
        return tmp;
    }
    
    public void startSend(
    String path,
    char type,
    long resumePosition
    ) throws IOException {
        logger.info("Send: path=" + path);
      
        _direction = TRANSFER_SENDING_DOWNLOAD;
        _cmd = _slave.sendCommand(
        "send", "\"" + path + "\" " + resumePosition + " " + _conn 
        );
        _started = System.currentTimeMillis();
        _slave.addTransfer(this);
    }
    public TransferStatus finishSend()
    {
        _slave.removeTransfer(this);
        TransferStatus tmp = getStatus();
        return tmp;
    }
    
    public void startRecv(
    String dirname,
    char mode,
    String filename,
    long offset
    ) throws IOException {
        logger.info("Recv: path=" + dirname + "/" + filename);
        
        _direction = TRANSFER_RECEIVING_UPLOAD;
        String args = "\"" + dirname + "/" + filename + "\" " + offset + " " + _conn;
        AsyncCommand cmd = _slave.sendCommand("recv", args);
        _started = System.currentTimeMillis();
        _slave.addTransfer(this);
    }
    
    public TransferStatus finishRecv()
    {
        _slave.removeTransfer(this);
        TransferStatus tmp = getStatus();
        return tmp;
    }
    
    public void abort() throws RemoteException {
        _abort = true;
        _cmd.abort();
    }
    
    
    public void updateStats(String sta, long byt, long crc, long err, String addr) {
        _status = sta;
        _transfered = byt;
        _checksum = crc;
        _error = err;
        _addr = addr;
    }
}
