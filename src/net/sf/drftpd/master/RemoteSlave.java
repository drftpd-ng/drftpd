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

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.DomDriver;

import net.sf.drftpd.SlaveUnavailableException;
import net.sf.drftpd.event.SlaveEvent;
import net.sf.drftpd.master.config.FtpConfig;
import net.sf.drftpd.remotefile.LinkedRemoteFile;
import net.sf.drftpd.slave.Slave;
import net.sf.drftpd.slave.SlaveStatus;
import net.sf.drftpd.slave.Transfer;
import net.sf.drftpd.util.SafeFileWriter;

import org.apache.log4j.Logger;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Serializable;

import java.net.InetAddress;
import java.net.SocketException;

import java.rmi.RemoteException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.StringTokenizer;


/**
 * @author mog
 * @version $Id: RemoteSlave.java,v 1.57 2004/10/29 02:45:17 mog Exp $
 */
public class RemoteSlave implements Comparable, Serializable {
    private static final Logger logger = Logger.getLogger(RemoteSlave.class);
    private transient int _errors;
    private transient long _lasterror;
    private HashMap renameQueue; // not _ because of XML Serialization
    private transient int _maxPath;
    private transient InetAddress _inetAddress;
    private transient long _lastDownloadSending = 0;
    private transient long _lastPing;
    private transient long _lastUploadReceiving = 0;
    private transient SlaveManagerImpl _manager;
    private Collection ipMasks; // not _ because of XML Serialization
    private transient String _name;
    private transient Slave _slave;
    private transient SlaveStatus _status;
    private Properties keysAndValues; // not _ because of XML Serialization
    private transient boolean _available;

    /**
     * Used by everything including tests
     */
    public RemoteSlave(String name, SlaveManagerImpl manager) {
        init(name, manager);
        keysAndValues = new Properties();
        ipMasks = new ArrayList();
        renameQueue = new HashMap();
        commit();
    }

    private void addQueueRename(String fileName, String destName) {
        if (isAvailable()) {
            throw new IllegalStateException(
                "Slave is available, you cannot queue an operation");
        }

        if (renameQueue.containsKey(fileName)) {
            throw new IllegalArgumentException(fileName +
                " is already in the queue for " + getName());
        }

        renameQueue.put(fileName, destName);
        commit();
    }

    /**
     * If X # of errors occur in Y amount of time, kick slave offline
     */
    public void addNetworkError(SocketException e) {
        // set slave offline if too many network errors
        long errortimeout = Long.parseLong(getProperty("errortimeout", "60000")); // one minute

        if (errortimeout <= 0) {
            errortimeout = 60000;
        }

        int maxerrors = Integer.parseInt(getProperty("maxerrors", "5"));

        if (maxerrors < 0) {
            maxerrors = 5;
        }

        _errors -= ((System.currentTimeMillis() - _lasterror) / errortimeout);

        if (_errors < 0) {
            _errors = 0;
        }

        _errors++;
        _lasterror = System.currentTimeMillis();

        if (_errors > maxerrors) {
            setOffline("Too many network errors - " + e.getMessage());
            logger.error(e);
        }
    }

    private void addQueueDelete(String fileName) {
        addQueueRename(fileName, null);
    }

    /**
     * Rename files.
     */
    public void rename(String from, String toDirPath, String toName)
        throws IOException {
        try {
            getSlave().rename(from, toDirPath, toName);
        } catch (RemoteException e) {
            handleRemoteException(e);
            addQueueRename(from, toDirPath + "/" + toName);
        } catch (IOException e) {
            setOffline(e.getMessage());
            addQueueRename(from, toDirPath + "/" + toName);
            throw e;
        } catch (SlaveUnavailableException e) {
            addQueueRename(from, toDirPath + "/" + toName);
        }
    }

    /**
     * Delete files.
     */
    public void deleteFile(String path) {
        try {
            getSlave().delete(path);
        } catch (RemoteException e) {
            handleRemoteException(e);
            addQueueDelete(path);
        } catch (FileNotFoundException e) {
            return;
        } catch (IOException e) {
            setOffline(
                "IOException deleting file, check logs for specific error");
            addQueueDelete(path);
            logger.error(e);
        } catch (SlaveUnavailableException e) {
            addQueueDelete(path);
        }
    }

    public void processQueue() throws RemoteException {
        for (Iterator iter = renameQueue.keySet().iterator(); iter.hasNext();) {
            String sourceFile = (String) iter.next();
            String destFile = (String) renameQueue.get(sourceFile);
            iter.remove();

            if (destFile == null) {
                try {
                    _slave.delete(sourceFile);
                } catch (IOException e) {
                    logger.error("Caught IOException during processQueue() - " +
                        e);

                    // just remove and continue, we can't do much
                    // if the OS has the file locked
                }
            } else {
                String fileName = destFile.substring(destFile.lastIndexOf("/") +
                        1);
                String destDir = destFile.substring(0, destFile.lastIndexOf("/"));

                try {
                    _slave.rename(sourceFile, destDir, fileName);
                } catch (IOException e) {
                    logger.error("Caught IOException during processQueue() - " +
                        e);

                    // just remove and continue, we can't do much except keep it in the queue
                    // if the OS has the file locked
                    continue;
                }
            }
        }
    }

    public void setProperty(String name, String value) {
        keysAndValues.setProperty(name, value);
        commit();
    }

    public String getProperty(String name, String def) {
        return keysAndValues.getProperty(name, def);
    }

    public Properties getProperties() {
        return keysAndValues;
    }

    public void commit() {
        if (_manager == null) {
            return; // for testing
        }

        try {
            XStream xst = new XStream(new DomDriver());
            SafeFileWriter out = new SafeFileWriter((_manager.getSlaveFile(
                        this.getName())));

            try {
                out.write(xst.toXML(this));
            } finally {
                out.close();
            }

            Logger.getLogger(RemoteSlave.class).debug("wrote " + getName());
        } catch (IOException ex) {
            throw new RuntimeException("Error writing slavefile for " +
                this.getName() + ": " + ex.getMessage(), ex);
        }
    }

    /*        public void updateConfig(Properties config) {
                    if (name.equalsIgnoreCase("all")) {
                            throw new IllegalArgumentException(
                                    name
                                            + " is a reserved keyword, it can't be used as a slave name");
                    }
                    _config = config;
                    renameQueue = new HashMap();
            }
    */
    /*        public Element getConfigXML() {
                    Element root = new org.jdom.Element("slave");
                    Enumeration e = _config.keys();
                    while (e.hasMoreElements()) {
                            String key = (String) e.nextElement();
                            Element tmp = new Element(key);
                            tmp.setText((String) _config.get(key));
                            root.addContent(tmp);
                    }
                    Iterator i = _masks.iterator();
                    while (i.hasNext()) {
                            String mask = (String) i.next();
                            Element tmp = new Element("mask");
                            tmp.setText(mask);
                            root.addContent(tmp);
                    }
                    return root;
            }*/
    public int compareTo(Object o) {
        if (!(o instanceof RemoteSlave)) {
            throw new IllegalArgumentException();
        }

        return getName().compareTo(((RemoteSlave) o).getName());
    }

    public boolean equals(Object obj) {
        return ((RemoteSlave) obj).getName().equals(getName());
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
        return ipMasks;
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
        if (!isAvailable()) {
            throw new SlaveUnavailableException("slave is offline");
        }

        return _slave;
    }

    /**
     * Returns the RemoteSlave's stored SlaveStatus, can return a status before remerge() is completed
     */
    public synchronized SlaveStatus getStatus()
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
            handleRemoteException(e);
        }
    }

    /**
     * @param ex RemoteException
     */
    public synchronized void handleRemoteException(RemoteException ex) {
        logger.warn("Exception from " + getName() + ", removing", ex);
        setOffline(ex.getCause().getMessage());
    }

    public int hashCode() {
        return getName().hashCode();
    }

    public void setAvailable(boolean available) {
        _available = available;
    }

    public boolean isAvailable() {
        return _available;
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

    public void setLastDownloadSending(long lastDownloadSending) {
        _lastDownloadSending = lastDownloadSending;
    }

    public void setLastUploadReceiving(long lastUploadReceiving) {
        _lastUploadReceiving = lastUploadReceiving;
    }

    public synchronized void setOffline(String reason) {
        if (!isAvailable()) {
            return; // already offline
        }

        if (_manager == null) {
            throw new RuntimeException("_manager == null");
        }

        if (_slave != null) {
            _manager.getGlobalContext().dispatchFtpEvent(new SlaveEvent(
                    "DELSLAVE", reason, this));
        }

        _slave = null;
        _status = null;
        _inetAddress = null;
        _maxPath = 0;
        setAvailable(false);
    }

    public void setSlave(Slave slave, InetAddress inetAddress,
        SlaveStatus status, int maxPath) throws RemoteException {
        if (slave == null) {
            throw new IllegalArgumentException();
        }

        _slave = slave;
        _inetAddress = inetAddress;
        _status = status;
        _maxPath = maxPath;
        processQueue();
        _errors = 0;
        _lasterror = System.currentTimeMillis();
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
    public LinkedRemoteFile getSlaveRoot()
        throws IOException, SlaveUnavailableException {
        if (_slave == null) {
            throw new SlaveUnavailableException(
                "Cannot getSlaveRoot() with Offline Slave");
        }

        return _slave.getSlaveRoot();
    }

    public void setLastDirection(char direction, long l) {
        switch (direction) {
        case Transfer.TRANSFER_RECEIVING_UPLOAD:
            setLastUploadReceiving(l);

            return;

        case Transfer.TRANSFER_SENDING_DOWNLOAD:
            setLastDownloadSending(l);

            return;

        default:
            throw new IllegalArgumentException();
        }
    }

    /**
     * Returns the RemoteSlave's stored SlaveStatus, will not return a status before remerge() is completed
     */
    public synchronized SlaveStatus getStatusAvailable()
        throws SlaveUnavailableException {
        if (isAvailable()) {
            return getStatus();
        }

        throw new SlaveUnavailableException("Slave is not online");
    }

    /**
     * @return true if the mask is a valid mask
     */
    public boolean addMask(String mask) {
        if ((mask.indexOf("@") == -1) || mask.endsWith("@") ||
                mask.startsWith("@")) {
            // @ has to exist as well as not being the first/last character
            return false;
        }

        ipMasks.add(mask);
        commit();

        return true;
    }

    /**
     * @return true if the mask was removed successfully
     */
    public boolean removeMask(String mask) {
        boolean value = ipMasks.remove(mask);

        if (value) {
            commit();
        }

        return value;
    }

    protected void init(String name, SlaveManagerImpl impl) {
        _name = name;
        _manager = impl;
    }

    public boolean hasKeyword(String string) {
        StringTokenizer st = new StringTokenizer(getProperty("keywords", ""),
                " ");

        while (st.hasMoreElements()) {
            if (st.nextToken().equals(string)) {
                return true;
            }
        }

        return false;
    }

    public String getProperty(String name) {
        return FtpConfig.getProperty(keysAndValues, name);
    }
}
