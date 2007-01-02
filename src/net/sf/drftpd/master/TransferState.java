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
package net.sf.drftpd.master;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

import javax.net.ssl.SSLSocket;

import net.sf.drftpd.master.config.FtpConfig;

import org.apache.log4j.Logger;
import org.drftpd.ActiveConnection;
import org.drftpd.GlobalContext;
import org.drftpd.PassiveConnection;
import org.drftpd.dynamicdata.Key;
import org.drftpd.master.RemoteSlave;
import org.drftpd.master.RemoteTransfer;
import org.drftpd.slave.Connection;
import org.drftpd.vfs.FileHandle;

/**
 * @author zubov
 * @version $Id$
 */
public class TransferState {
	
	public static final Key TRANSFERSTATE = new Key(TransferState.class,
			"transferstate", TransferState.class);
	private static final Logger logger = Logger.getLogger(TransferState.class);
	
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
     * Is this a PRET/PASV transfer?
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
    private boolean _SSLHandshakeClientMode=true;
    
    /**
     * Should we send files encrypted?
     */
    private boolean _encryptedDataChannel;

	/**
	 * This class to be used as a state holder for DataConnectionHandler
	 */
	public TransferState() {
		
	}
	
	public void reset() {
		_rslave = null;
		if (_transfer != null) {
			_transfer.abort("reset");
		}
		
		_transfer = null;
		_transferFile = null;
		_pretRequest = null;

		if (_localPassiveConnection != null) { //isPasv() && _preTransferRSlave == null
			_localPassiveConnection.abort();
			_localPassiveConnection = null;
		}
		_resumePosition = 0;
	}

	public boolean isPreTransfer() {
		return _pretRequest != null;
	}

	public boolean isPort() {
		return _portAddress != null;
	}

	/**
	 * A synonym for isPreTransfer()
	 * @see isPreTransfer()
	 */
	public boolean isPasv() {
		return isPreTransfer();
	}

	public void setSSLHandshakeClientMode(boolean b) {
		_SSLHandshakeClientMode = b;
	}
	
	public Socket getDataSocketForLIST() throws IOException {
        Socket dataSocket;
        // get socket depending on the selection
        if (isPort()) {
            try {
				ActiveConnection ac = new ActiveConnection(_encryptedDataChannel ? GlobalContext.getGlobalContext().getSSLContext() : null, _portAddress, _SSLHandshakeClientMode);
                dataSocket = ac.connect(FtpConfig.getFtpConfig().getCipherSuites());
            } catch (IOException ex) {
                logger.warn("Error opening data socket", ex);
                dataSocket = null;
                throw ex;
            }
        } else if (isPasv()) {
            try {
                dataSocket = _localPassiveConnection.connect(FtpConfig.getFtpConfig().getCipherSuites());
            } finally {
				if (_localPassiveConnection != null) {
					_localPassiveConnection.abort();
					_localPassiveConnection = null;
				}
            }
        } else {
            throw new IllegalStateException("Neither PASV nor PORT");
        }
		// Already done since we are using ActiveConnection and PasvConnection
        dataSocket.setSoTimeout(Connection.TIMEOUT); // 15 seconds timeout

        if (dataSocket instanceof SSLSocket) {
            SSLSocket ssldatasocket = (SSLSocket) dataSocket;
            ssldatasocket.setUseClientMode(false);
            ssldatasocket.startHandshake();
        }

        return dataSocket;
	}
	
	public boolean getSSLHandshakeClientMode() {
		return _SSLHandshakeClientMode;
	}

	public boolean getSendFilesEncrypted() {
		return _encryptedDataChannel;
	}

	public FtpRequest getPretRequest() {
		return _pretRequest;
	}

	public boolean isLocalPreTransfer() {
		return (getPretRequest().getCommand().equalsIgnoreCase("LIST")
				|| getPretRequest().getCommand().equalsIgnoreCase("NLST") || getPretRequest()
				.getCommand().equalsIgnoreCase("MLSD"));
	}
	
	public boolean isPASVDownload() {
		return (getPretRequest() != null && getPretRequest().getCommand().equalsIgnoreCase("RETR"));
	}
	
	public boolean isPASVUpload() {
		return (getPretRequest() != null && getPretRequest().getCommand().equalsIgnoreCase("STOR"));
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

	public void setTransferSlave(RemoteSlave slave) {
		_rslave = slave;
	}

	public void setTransfer(RemoteTransfer transfer) {
		_transfer = transfer;
	}
	
	public RemoteTransfer getTransfer() {
		return _transfer;
	}

	public FileHandle getTransferFile() {
		return _transferFile;
	}
	
	public InetSocketAddress getPortAddress() {
		return _portAddress;
	}
	
	public void setPortAddress(InetSocketAddress addr) {
		_portAddress = addr;
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
}
