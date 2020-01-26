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
/**
 * 
 */
package org.drftpd.master;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.drftpd.ActiveConnection;
import org.drftpd.GlobalContext;
import org.drftpd.PassiveConnection;
import org.drftpd.dynamicdata.Key;
import org.drftpd.exceptions.SlaveUnavailableException;
import org.drftpd.slave.Transfer;
import org.drftpd.slave.TransferFailedException;
import org.drftpd.slave.TransferStatus;
import org.drftpd.util.FtpRequest;
import org.drftpd.vfs.FileHandle;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * @author zubov
 * @version $Id$
 */
public class TransferState {
	
	public static final Key<TransferState> TRANSFERSTATE = new Key<>(TransferState.class, "transferstate");
	private static final Logger logger = LogManager.getLogger(TransferState.class);
	
	/**
	 * The file to be transfered
	 */
	private FileHandle _transferFile = null;
    /**
     * ServerSocket for PASV mode.
     */
    private PassiveConnection _localPassiveConnection;
    
    /**
     * The handle to the transfer on the slave
     */
    private RemoteTransfer _transfer;
    
    /**
     * Binary or Ascii transfers
     */
    private char _type = 'A';
    
    /**
     * Where do we connect in Active mode?
     */
    private InetSocketAddress _portAddress;
    
    /**
     * Is this a PRET transfer?
     * If a PRET command has been done, this will be set
     */
    private FtpRequest _pretRequest = null;
    
    /**
     * Where in the file should we start sending from?
     * Used for resume of transfers
     */
    private long _resumePosition = 0;
    
    /**
     * What slave should we use?
     * This will only be set after the SlaveSelection process
     */
    private RemoteSlave _rslave;
    
    /**
     * This flag defines if we are the client or server in the SSL handshake
     * This only means something when _encryptedDataChannel below is set
     */
    private boolean _SSLHandshakeClientMode = false;
    
    /**
     * Should we send files encrypted?
     */
    private boolean _encryptedDataChannel;
    
    /**
     * Defines if the transfer is of type Passive
     */
	private boolean _isPasv = false;

	/**
	 * Defines if this transfer successfully created the VFS file
	 */
	private boolean _transferFileCreated = false;

	/**
	 * This class to be used as a state holder for DataConnectionHandler
	 */
	public TransferState() {

	}
	
	public synchronized char getDirection() {
		RemoteTransfer transfer = getTransfer();
		if (transfer == null) {
			return Transfer.TRANSFER_UNKNOWN;
		}
		return transfer.getTransferDirection();
	}
	
	public synchronized void reset() {
		_rslave = null;
		resetTransfer();
		
		_transfer = null;
		_transferFile = null;
		_pretRequest = null;
		_isPasv = false;
		_portAddress = null;

		if (_localPassiveConnection != null) {
			_localPassiveConnection.abort();
			_localPassiveConnection = null;
		}
		_resumePosition = 0;
		_transferFileCreated = false;
	}
	
	public synchronized void resetTransfer() {
		if (_transfer != null) {
			_transfer.abort("reset");
		}
	}

	public boolean isPreTransfer() {
		return _pretRequest != null;
	}

	public boolean isPort() {
		return _portAddress != null;
	}
	
	public boolean isPasv() {
		return _isPasv;
	}

	public void setSSLHandshakeClientMode(boolean b) {
		_SSLHandshakeClientMode = b;
	}
	
	public Socket getDataSocketForLIST() throws IOException {
        Socket dataSocket;
        // get socket depending on the selection
        if (isPort()) {
            try {
				ActiveConnection ac = new ActiveConnection(_encryptedDataChannel ? GlobalContext.getGlobalContext().getSSLContext() : null, _portAddress, getSSLHandshakeClientMode(),ConnectionManager.getBindIP());

				dataSocket = ac.connect(GlobalContext.getConfig().getCipherSuites(), GlobalContext.getConfig().getSSLProtocols(), 0);
            } catch (IOException ex) {
                logger.warn("Error opening data socket", ex);
                dataSocket = null;
                throw ex;
            }
        } else if (isPasv()) {
            try {
				dataSocket = _localPassiveConnection.connect(GlobalContext.getConfig().getCipherSuites(), GlobalContext.getConfig().getSSLProtocols(), 0);
            } finally {
				if (_localPassiveConnection != null) {
					_localPassiveConnection.abort();
					_localPassiveConnection = null;
				}
            }
        } else {
            throw new IllegalStateException("Neither PASV nor PORT");
        }

        return dataSocket;
	}
	
	public boolean getSSLHandshakeClientMode() {
		return _SSLHandshakeClientMode;
	}

	public boolean getSendFilesEncrypted() {
		return _encryptedDataChannel;
	}
	
	public void setSendFilesEncrypted(boolean encrypted) {
		_encryptedDataChannel = encrypted;
	}

	public FtpRequest getPretRequest() {
		return _pretRequest;
	}

	public boolean isLocalPreTransfer() {
		if (!isPreTransfer()) {
			return false;
		}
		return (getPretRequest().getCommand().equalsIgnoreCase("LIST")
				|| getPretRequest().getCommand().equalsIgnoreCase("NLST") || getPretRequest()
				.getCommand().equalsIgnoreCase("MLSD"));
	}
	
	public boolean isPASVDownload() {
		return (isPasv() && getPretRequest().getCommand().equalsIgnoreCase("RETR"));
	}
	
	public boolean isPASVUpload() {
		return (isPasv() && getPretRequest().getCommand().equalsIgnoreCase("STOR"));
	}

	public void setLocalPassiveConnection(PassiveConnection pc) {
		_localPassiveConnection = pc;
	}

	public void setPreTransferRequest(FtpRequest ghostRequest) {
		_pretRequest = ghostRequest;
	}

	public RemoteSlave getTransferSlave() {
		return _rslave;
	}

	public void setTransferFile(FileHandle file) {
		_transferFile = file;
	}

	public void setTransferFileCreated(boolean transferFileCreated) {
		_transferFileCreated = transferFileCreated;
	}

	public void setTransferSlave(RemoteSlave slave) {
		_rslave = slave;
	}

	public void setTransfer(RemoteTransfer transfer) {
		_transfer = transfer;
	}
	
	public boolean isTransfering() {
		return getDirection() != Transfer.TRANSFER_UNKNOWN;
	}
	
	private RemoteTransfer getTransfer() {
		return _transfer;
	}

	public FileHandle getTransferFile() {
		return _transferFile;
	}

	public boolean getTransferFileCreated() {
		return _transferFileCreated;
	}
	
	public InetSocketAddress getPortAddress() {
		return _portAddress;
	}
	
	public void setPortAddress(InetSocketAddress addr) {
		_portAddress = addr;
	}
	
	public void setPasv(boolean value) {
		_isPasv = value;
	}

	public void setResumePosition(long resumePosition) {
		_resumePosition = resumePosition;
	}
	
    /**
     * Set the data type. Supported types are A (ascii) and I (binary).
     *
     * @return true if success
     */
    public boolean setType(char type) {
        type = Character.toUpperCase(type);

        if ((type != 'A') && (type != 'I')) {
            return false;
        }

        _type = type;

        return true;
    }
    
    public char getType() {
    	return _type;
    }

	public long getResumePosition() {
		return _resumePosition;
	}

	/**
	 * Returns true if the transfer was aborted
	 * 
	 * @param reason
	 * @return
	 */
	public synchronized boolean abort(String reason) {
		RemoteTransfer rt = getTransfer();
		if (rt != null) {
			rt.abort(reason);
			if (_transferFileCreated && rt.getTransferDirection() == Transfer.TRANSFER_RECEIVING_UPLOAD &&
					Boolean.parseBoolean(GlobalContext.getConfig().getMainProperties()
							.getProperty("delete.upload.on.abort", "false"))) {
				try {
					_transferFile.deleteUnchecked();
				} catch (FileNotFoundException e) {
					// This is fine as we wanted to delete it anyway
				}
			}
			return true;
		}
		return false;
	}

	public static char getDirectionFromRequest(FtpRequest request) {
		String cmd = request.getCommand();

		if ("RETR".equals(cmd)) {
			return Transfer.TRANSFER_SENDING_DOWNLOAD;
		}

		if ("STOR".equals(cmd) || "APPE".equals(cmd)) {
			return Transfer.TRANSFER_RECEIVING_UPLOAD;
		}
		throw new IllegalStateException("Not transfering");
	}

	public synchronized InetSocketAddress getAddress() {
		return getTransfer().getAddress();
	}

	public synchronized TransferStatus getTransferStatus() throws TransferFailedException {
		return getTransfer().getTransferStatus();
	}

	public synchronized void sendFile(String path, char type, long resumePosition, String address, long minSpeed, long maxSpeed)
	throws IOException, SlaveUnavailableException {
		getTransfer().sendFile(path, type, resumePosition, address, minSpeed, maxSpeed);
	}

	public synchronized void receiveFile(String path, char type, long resumePosition, String address, long minSpeed, long maxSpeed)
	throws IOException, SlaveUnavailableException {
		getTransfer().receiveFile(path, type, resumePosition, address, minSpeed, maxSpeed);
	}

	public synchronized long getElapsed() {
		return getTransfer().getElapsed();
	}

	public synchronized long getXferSpeed() {
		return getTransfer().getXferSpeed();
	}

	public synchronized long getTransfered() {
		return getTransfer().getTransfered();
	}
}
