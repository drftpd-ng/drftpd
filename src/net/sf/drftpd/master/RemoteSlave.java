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
package net.sf.drftpd.master;

import java.io.IOException;
import java.net.InetAddress;
import java.rmi.ConnectException;
import java.rmi.ConnectIOException;
import java.rmi.RemoteException;
import java.util.Collection;
import java.util.Hashtable;
import java.util.Iterator;

import net.sf.drftpd.SlaveUnavailableException;
import net.sf.drftpd.event.SlaveEvent;
import net.sf.drftpd.remotefile.LinkedRemoteFile;
import net.sf.drftpd.slave.Slave;
import net.sf.drftpd.slave.SlaveStatus;
import net.sf.drftpd.slave.Transfer;

import org.apache.log4j.Logger;

import org.jdom.Element;
import java.util.Hashtable;

/**
 * @author mog
 * @version $Id: RemoteSlave.java,v 1.34 2004/04/28 13:00:55 zombiewoof64 Exp $
 */
public class RemoteSlave implements Comparable {
    
    private static final Logger logger =
    Logger.getLogger(RemoteSlave.class.getName());
    
    public static boolean isFatalRemoteException(RemoteException ex) {
        return (
        ex instanceof ConnectException || ex instanceof ConnectIOException);
    }
    
    private InetAddress _inetAddress;
    private long _lastDownloadSending = 0;
    private long _lastPing;
    private long _lastUploadReceiving = 0;
    private SlaveManagerImpl _manager;
    private Collection _masks;
    private String _name;
    private Slave _slave;
    private SlaveStatus _status;
    private Hashtable _config = new Hashtable();
    
    public void oldRemoteSlave(String name, Collection masks) {
        if (name.equalsIgnoreCase("all"))
            throw new IllegalArgumentException(
            name
            + " is a reserved keyword, it can't be used as a slave name");
        _name = name;
        _masks = masks;
    }
    
    public RemoteSlave(String name, Collection masks, Element config) {
        if (name.equalsIgnoreCase("all"))
            throw new IllegalArgumentException(
            name
            + " is a reserved keyword, it can't be used as a slave name");
        _name = name;
        _masks = masks;
        for (Iterator i = config.getChildren().iterator(); i.hasNext();) {
            Element e = (Element)i.next();
            try {
                _config.put(e.getName(), e.getText());
            } catch (Exception e1) {}
        }
    }
    
    public int compareTo(Object o) {
        if (!(o instanceof RemoteSlave))
            throw new IllegalArgumentException();
        return getName().compareTo(((RemoteSlave) o).getName());
    }
    
    public boolean equals(Object obj) {
        if (obj instanceof RemoteSlave) {
            RemoteSlave rslave = (RemoteSlave) obj;
            if (rslave.getName().equals(getName())) {
                return true;
            }
        }
        return false;
    }
    
    public InetAddress getInetAddress() {
        return _inetAddress;
    }
    
    public long getLastDownloadSending() {
        return _lastDownloadSending;
    }
    
    public long getLastTransfer() {
        return Math.max(getLastDownloadSending(), getLastUploadReceiving());
    }
    
    public long getLastUploadReceiving() {
        return _lastUploadReceiving;
    }
    
    public SlaveManagerImpl getManager() {
        return _manager;
    }
    
    public Collection getMasks() {
        return _masks;
    }
    
    public Hashtable getConfig() {
        return _config;
    }
    
    /**
     * Returns the name.
     */
    public String getName() {
        return _name;
    }
    
    /**
     * Throws NoAvailableSlaveException only if slave is offline
     */
    public Slave getSlave() throws SlaveUnavailableException {
        if (_slave == null)
            throw new SlaveUnavailableException("slave is offline");
        return _slave;
    }
    
    /**
     * Returns the RemoteSlave's stored SlaveStatus
     */
    public SlaveStatus getStatus()
    throws SlaveUnavailableException {
        if (_status == null) {
            throw new SlaveUnavailableException();
        }
        return _status;
    }
    
    public synchronized void updateStatus() throws SlaveUnavailableException {
        try {
            _status = getSlave().getSlaveStatus();
        } catch (RemoteException e) {
            _status = null;
        }
    }
    
    /**
     * @param ex RemoteException
     * @return true If exception was fatal and the slave was removed
     */
    public void handleRemoteException(RemoteException ex) {
        //		if (!isFatalRemoteException(ex)) {
        //			logger.log(
        //				Level.WARN,
        //				"Caught non-fatal exception from "
        //					+ getName()
        //					+ ", not removing",
        //				ex);
        //			return false;
        //		}
        logger.warn("Exception from " + getName() + ", removing", ex);
        setOffline(ex.getCause().getMessage());
    }
    
    public int hashCode() {
        return getName().hashCode();
    }
    
    public boolean isAvailable() {
        return _slave != null;
    }
    
    public boolean isAvailablePing() {
        try {
            getSlave().ping();
        } catch (RemoteException e) {
            handleRemoteException(e);
            return false;
        } catch (SlaveUnavailableException e) {
            return false;
        }
        return isAvailable();
    }
    
    /**
     * @deprecated use isAvailablePing() instead
     * @throws RemoteException
     * @throws SlaveUnavailableException
     */
    public void ping() throws RemoteException, SlaveUnavailableException {
        if (_slave == null)
            throw new SlaveUnavailableException(getName() + " is offline");
        if (System.currentTimeMillis() > _lastPing + 1000) {
            getSlave().ping();
        }
    }
    public void setLastDownloadSending(long lastDownloadSending) {
        _lastDownloadSending = lastDownloadSending;
    }
    public void setLastUploadReceiving(long lastUploadReceiving) {
        _lastUploadReceiving = lastUploadReceiving;
    }
    
    public void setManager(SlaveManagerImpl manager) {
        if (_manager != null)
            throw new IllegalStateException("Can't overwrite manager");
        _manager = manager;
    }
    
    public void setMasks(Collection masks) {
        _masks = masks;
    }
    
    public void setOffline(String reason) {
        assert _manager != null;
        if (_slave != null) {
            _manager.getConnectionManager().dispatchFtpEvent(
            new SlaveEvent("DELSLAVE", reason, this));
        }
        _slave = null;
        _status = null;
        _inetAddress = null;
    }
    
    public void setSlave(Slave slave, InetAddress inetAddress, SlaveStatus status) {
        if (slave == null)
            throw new IllegalArgumentException();
        _slave = slave;
        _inetAddress = inetAddress;
        _status = status;
    }
    
    public String toString() {
        try {
            return getName() + "[slave=" + getSlave().toString() + "]";
        } catch (SlaveUnavailableException e) {
            return getName() + "[slave=offline]";
        }
    }
    
    public static Hashtable rslavesToHashtable(Collection rslaves) {
        Hashtable map = new Hashtable(rslaves.size());
        for (Iterator iter = rslaves.iterator(); iter.hasNext();) {
            RemoteSlave rslave = (RemoteSlave) iter.next();
            map.put(rslave.getName(), rslave);
        }
        return map;
    }
    
    public long getLastTransferForDirection(char dir) {
        if (dir == Transfer.TRANSFER_RECEIVING_UPLOAD) {
            return getLastUploadReceiving();
        } else if (dir == Transfer.TRANSFER_SENDING_DOWNLOAD) {
            return getLastDownloadSending();
        } else if (dir == Transfer.TRANSFER_THROUGHPUT) {
            return getLastTransfer();
        } else {
            throw new IllegalArgumentException();
        }
    }
    
    /**
     * Returns an updated slaveRoot
     */
    public LinkedRemoteFile getSlaveRoot() throws IOException, SlaveUnavailableException {
        return getSlave().getSlaveRoot();
    }
    
    public void setLastDirection(char direction, long l) {
        switch (direction) {
            case Transfer.TRANSFER_RECEIVING_UPLOAD :
                setLastUploadReceiving(l);
                return;
            case Transfer.TRANSFER_SENDING_DOWNLOAD :
                setLastDownloadSending(l);
                return;
            default :
                throw new IllegalArgumentException();
        }
    }
}
