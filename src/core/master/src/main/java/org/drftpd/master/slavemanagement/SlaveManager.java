/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * DrFTPD is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with DrFTPD; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.drftpd.master.slavemanagement;

import com.cedarsoftware.util.io.JsonReader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.drftpd.common.exceptions.RemoteIOException;
import org.drftpd.common.exceptions.SSLUnavailableException;
import org.drftpd.common.network.AsyncCommandArgument;
import org.drftpd.common.network.SSLGetContext;
import org.drftpd.common.protocol.AbstractIssuer;
import org.drftpd.common.util.PropertyHelper;
import org.drftpd.master.GlobalContext;
import org.drftpd.master.cron.TimeEventInterface;
import org.drftpd.master.exceptions.FatalException;
import org.drftpd.master.exceptions.NoAvailableSlaveException;
import org.drftpd.master.exceptions.SlaveFileException;
import org.drftpd.master.exceptions.SlaveUnavailableException;
import org.drftpd.master.network.RemoteTransfer;
import org.drftpd.master.protocol.AbstractBasicIssuer;
import org.drftpd.master.protocol.MasterProtocolCentral;
import org.drftpd.master.vfs.DirectoryHandle;
import org.drftpd.slave.exceptions.ObjectNotFoundException;

import javax.net.ssl.SSLSocket;
import java.beans.XMLDecoder;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author mog
 * @version $Id$
 */
public class SlaveManager implements Runnable, TimeEventInterface {
    protected static final int actualTimeout = 60000; // one minute, evaluated
    private static final Logger logger = LogManager.getLogger(SlaveManager.class.getName());
    private static final String slavePath = "userdata/slaves/";
    private static final int socketTimeout = 10000; // 10 seconds, for Socket
    // on a SocketTimeout
    private static AbstractBasicIssuer _basicIssuer = null;

    protected Map<String, RemoteSlave> _rSlaves = new ConcurrentHashMap<>();
    protected ServerSocket _serverSocket;
    protected MasterProtocolCentral _central;
    private int _port;
    private boolean _sslSlaves;
    private boolean _listForSlaves = true;

    protected SlaveManager() {}

    public SlaveManager(Properties p) throws SlaveFileException {
        _sslSlaves = p.getProperty("master.slaveSSL", "false").equalsIgnoreCase("true");
        try {
            if (_sslSlaves && GlobalContext.getGlobalContext().getSSLContext() == null) {
                throw new SSLUnavailableException("Secure connections to slave required but SSL isn't available");
            }
        } catch (Exception e) {
            throw new FatalException(e);
        }

        _port = Integer.parseInt(PropertyHelper.getProperty(p, "master.bindport"));
        _central = new MasterProtocolCentral();
        loadSlaves();
    }

    public static AbstractBasicIssuer getBasicIssuer() {
        // avoid unnecessary lookups.
        if (_basicIssuer == null) {
            _basicIssuer = (AbstractBasicIssuer) GlobalContext.getGlobalContext().getSlaveManager().
                    getProtocolCentral().getIssuerForClass(AbstractBasicIssuer.class);
        }
        return _basicIssuer;
    }

    private void loadSlaves() throws SlaveFileException {
        File slavePathFile = new File(slavePath);
        if (!slavePathFile.exists() && !slavePathFile.mkdirs()) {
            throw new SlaveFileException(new IOException("Error creating directories: " + slavePathFile));
        }

        String[] names = slavePathFile.list();
        if (names == null) {
            throw new SlaveFileException("Unable to list directory: "+slavePathFile);
        }
        for (String slavePath : names) {

            // Ignore anything that does not end with these extensions
            if (!slavePath.endsWith(".xml") && !slavePath.endsWith(".json")) {
                continue;
            }

            String slaveName = slavePath.substring(0, slavePath.lastIndexOf('.'));

            try {
                getSlaveByNameUnchecked(slaveName);
            } catch (ObjectNotFoundException e) {
                throw new SlaveFileException(e);
            }
        }
    }

    public void newSlave(String slaveName) {
        addSlave(new RemoteSlave(slaveName));
    }

    public synchronized void addSlave(RemoteSlave rSlave) {
        _rSlaves.put(rSlave.getName(), rSlave);
    }

    private RemoteSlave getSlaveByNameUnchecked(String slaveName) throws ObjectNotFoundException {
        if (slaveName == null) {
            throw new NullPointerException();
        }
        try (InputStream in = new FileInputStream(getSlaveFile(slaveName)); JsonReader reader = new JsonReader(in)) {
            logger.debug("Loading slave '{}' Json data from disk.", slaveName);
            RemoteSlave rSlave = (RemoteSlave) reader.readObject();
            if (rSlave.getName().equals(slaveName)) {
                _rSlaves.put(slaveName, rSlave);
                return rSlave;
            }
            logger.warn("Tried to lookup a slave with the same name, different case", new Throwable());
            throw new ObjectNotFoundException();
        } catch (FileNotFoundException e) {
            // Lets see if there is a legacy xml slave file to load
            return getSlaveByXMLNameUnchecked(slaveName);
        } catch (Exception e) {
            throw new FatalException("Error loading " + slaveName + " : " + e.getMessage(), e);
        }
    }

    private RemoteSlave getSlaveByXMLNameUnchecked(String slaveName) throws ObjectNotFoundException {
        RemoteSlave rSlave;
        File xmlSlaveFile = getXMLSlaveFile(slaveName);
        try (XMLDecoder in = new XMLDecoder(new BufferedInputStream(new FileInputStream(xmlSlaveFile)))) {
            logger.debug("Loading slave '{}' XML data from disk.", slaveName);
            ClassLoader prevCL = Thread.currentThread().getContextClassLoader();
            rSlave = (RemoteSlave) in.readObject();
            Thread.currentThread().setContextClassLoader(prevCL);

            if (rSlave.getName().equals(slaveName)) {
                _rSlaves.put(slaveName, rSlave);
                // Commit new json slave file and delete old xml
                rSlave.commit();
                if (!xmlSlaveFile.delete()) {
                    logger.error("Failed to delete old xml slave file: {}", xmlSlaveFile.getName());
                }
                return rSlave;
            }
            logger.warn("Tried to lookup a slave with the same name, different case", new Throwable());
            throw new ObjectNotFoundException();
        } catch (FileNotFoundException e) {
            throw new ObjectNotFoundException(e);
        } catch (Exception e) {
            throw new FatalException("Error loading " + slaveName, e);
        }
    }

    protected File getSlaveFile(String slaveName) {
        return new File(slavePath + slaveName + ".json");
    }

    protected File getXMLSlaveFile(String slaveName) {
        return new File(slavePath + slaveName + ".xml");
    }

    public void addShutdownHook() {
        // add shutdown hook last
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            getGlobalContext().getSlaveManager().listForSlaves(false);
            logger.info("Running shutdown hook");
            for (RemoteSlave rSlave : _rSlaves.values()) {
                rSlave.shutdown();
            }
        }));
    }

    public void delSlave(String slaveName) {
        RemoteSlave rSlave;

        try {
            rSlave = getRemoteSlave(slaveName);
            File rSlaveFile = getSlaveFile(rSlave.getName());
            if (!rSlaveFile.delete()) {
                logger.error("Something did not go right while deleting {}", rSlaveFile);
            }
            rSlave.setOffline("Slave has been deleted");
            _rSlaves.remove(slaveName);
            getGlobalContext().getRoot().removeSlave(rSlave);
        } catch (ObjectNotFoundException e) {
            throw new IllegalArgumentException("Slave not found");
        } catch (FileNotFoundException e) {
            logger.debug("FileNotFoundException in delSlave()", e);
        }
    }

    public HashSet<RemoteSlave> findSlavesBySpace(int numOfSlaves, Set<RemoteSlave> exemptSlaves, boolean ascending) {
        Collection<RemoteSlave> slaveList = getSlaves();
        HashMap<Long, RemoteSlave> map = new HashMap<>();

        for (RemoteSlave rSlave : slaveList) {
            if (exemptSlaves.contains(rSlave)) {
                continue;
            }

            Long size;

            try {
                size = rSlave.getSlaveStatusAvailable().getDiskSpaceAvailable();
            } catch (SlaveUnavailableException e) {
                continue;
            }

            map.put(size, rSlave);
        }

        ArrayList<Long> sorted = new ArrayList<>(map.keySet());

        if (ascending) {
            Collections.sort(sorted);
        } else {
            sorted.sort(Collections.reverseOrder());
        }

        HashSet<RemoteSlave> returnMe = new HashSet<>();

        for (ListIterator<Long> iter = sorted.listIterator(); iter.hasNext(); ) {
            if (iter.nextIndex() == numOfSlaves) {
                break;
            }
            Long key = iter.next();
            RemoteSlave rSlave = map.get(key);
            returnMe.add(rSlave);
        }

        return returnMe;
    }

    public RemoteSlave findSmallestFreeSlave() {
        Collection<RemoteSlave> slaveList = getSlaves();
        long smallSize = Integer.MAX_VALUE;
        RemoteSlave smallSlave = null;

        for (RemoteSlave rSlave : slaveList) {
            long size = Integer.MAX_VALUE;

            try {
                size = rSlave.getSlaveStatusAvailable().getDiskSpaceAvailable();
            } catch (SlaveUnavailableException e) {
                continue;
            }

            if (size < smallSize) {
                smallSize = size;
                smallSlave = rSlave;
            }
        }

        return smallSlave;
    }

    /**
     * Not cached at all since RemoteSlave objects cache their SlaveStatus
     */
    public SlaveStatus getAllStatus() {
        SlaveStatus allStatus = new SlaveStatus();

        for (RemoteSlave rSlave : getSlaves()) {
            try {
                allStatus = allStatus.append(rSlave.getSlaveStatusAvailable());
            } catch (SlaveUnavailableException e) {
                // slave is offline, continue
            }
        }

        return allStatus;
    }

    public HashMap<String, SlaveStatus> getAllStatusArray() {
        HashMap<String, SlaveStatus> ret = new HashMap<>(
                getSlaves().size());

        for (RemoteSlave rSlave : getSlaves()) {
            try {
                ret.put(rSlave.getName(), rSlave.getSlaveStatus());
            } catch (SlaveUnavailableException e) {
                ret.put(rSlave.getName(), null);
            }
        }

        return ret;
    }

    /**
     * Returns a modifiable list of available RemoteSlave's
     */
    public Collection<RemoteSlave> getAvailableSlaves() throws NoAvailableSlaveException {
        ArrayList<RemoteSlave> availableSlaves = new ArrayList<>();

        for (RemoteSlave rSlave : getSlaves()) {
            if (!rSlave.isAvailable()) {
                continue;
            }

            availableSlaves.add(rSlave);
        }

        if (availableSlaves.isEmpty()) {
            throw new NoAvailableSlaveException("No slaves online");
        }

        return availableSlaves;
    }

    public GlobalContext getGlobalContext() {
        return GlobalContext.getGlobalContext();
    }

    public RemoteSlave getRemoteSlave(String s) throws ObjectNotFoundException {
        RemoteSlave rSlave = _rSlaves.get(s);
        if (rSlave == null) {
            return getSlaveByNameUnchecked(s);
        }
        return rSlave;
    }

    public List<RemoteSlave> getSlaves() {
        if (_rSlaves == null) {
            throw new NullPointerException();
        }
        List<RemoteSlave> slaveList = new ArrayList<>(_rSlaves.values());
        Collections.sort(slaveList);
        return slaveList;
    }

    /**
     * Returns true if one or more slaves are online, false otherwise.
     *
     * @return true if one or more slaves are online, false otherwise.
     */
    public boolean hasAvailableSlaves() {
        for (RemoteSlave remoteSlave : _rSlaves.values()) {
            if (remoteSlave.isAvailable()) {
                return true;
            }
        }
        return false;
    }

    public void listForSlaves(boolean portOpen) {
        _listForSlaves = portOpen;
    }

    public void run() {
        try {
            if (_sslSlaves) {
                _serverSocket = SSLGetContext.getSSLContext().getServerSocketFactory().createServerSocket(_port);
            } else {
                _serverSocket = new ServerSocket(_port);
            }
            // _serverSocket.setReuseAddress(true);
            logger.info("Listening for slaves on port {}", _port);
        } catch (Exception e) {
            throw new FatalException(e);
        }

        Socket socket = null;

        while (_listForSlaves) {
            RemoteSlave rSlave;
            ObjectInputStream in;
            ObjectOutputStream out;

            try {
                socket = _serverSocket.accept();
                logger.debug("[{}] Accepted new connection", socket.getRemoteSocketAddress());
                socket.setSoTimeout(socketTimeout);
                if (socket instanceof SSLSocket) {
                    if (GlobalContext.getConfig().getCipherSuites() != null) {
                        ((SSLSocket) socket).setEnabledCipherSuites(GlobalContext.getConfig().getCipherSuites());
                    }
                    if (GlobalContext.getConfig().getSSLProtocols() != null) {
                        ((SSLSocket) socket).setEnabledProtocols(GlobalContext.getConfig().getSSLProtocols());
                    }
                    logger.debug("[{}] Enabled ciphers for this new connection are as follows: '{}'",
                            socket.getRemoteSocketAddress(), Arrays.toString(((SSLSocket) socket).getEnabledCipherSuites()));
                    logger.debug("[{}] Enabled protocols for this new connection are as follows: '{}'",
                            socket.getRemoteSocketAddress(), Arrays.toString(((SSLSocket) socket).getEnabledProtocols()));
                    ((SSLSocket) socket).setUseClientMode(false);
                    ((SSLSocket) socket).startHandshake();
                }

                out = new ObjectOutputStream(new BufferedOutputStream(socket.getOutputStream()));
                out.flush();
                in = new ObjectInputStream(new BufferedInputStream(socket.getInputStream()));

                String slaveName = RemoteSlave.getSlaveNameFromObjectInput(in);

                try {
                    rSlave = getRemoteSlave(slaveName);
                } catch (ObjectNotFoundException e) {
                    out.writeObject(new AsyncCommandArgument("error", "error", slaveName
                            + " does not exist, use \"site addslave\""));
                    logger.error("Slave {} does not exist, use \"site addslave\"", slaveName);
                    socket.close();
                    continue;
                }

                if (rSlave.isOnline()) {
                    out.writeObject(new AsyncCommandArgument("", "error",
                            "Already online"));
                    out.flush();
                    socket.close();
                    throw new IOException("Already online: " + slaveName);
                }
            } catch (Exception e) {
                if (socket != null) {
                    try {
                        socket.close();
                    } catch (IOException ignored) {
                    }
                }

                logger.error("", e);

                continue;
            }

            try {
                if (!rSlave.checkConnect(socket)) {
                    out.writeObject(new AsyncCommandArgument("", "error", socket.getInetAddress() + " is not a valid mask for " + rSlave.getName()));
                    logger.error("{} is not a valid ip for {}", socket.getInetAddress(), rSlave.getName());
                    socket.close();

                    continue;
                }

                rSlave.connect(socket, in, out);
            } catch (Exception e) {
                rSlave.setOffline(e);
                logger.error(e);
            } catch (Throwable t) {
                logger.fatal("Throwable in SlaveManager loop", t);
            }
        }
    }

    /**
     * Cancels all transfers in directory
     */
    public void cancelTransfersInDirectory(DirectoryHandle dir) {
        for (RemoteSlave rs : getSlaves()) {
            try {
                for (RemoteTransfer rt : rs.getTransfers()) {
                    String path = rt.getPathNull();
                    if (path != null) {
                        if (path.startsWith(dir.getPath())) {
                            rt.abort("Directory is nuked");
                        }
                    }
                }
            } catch (SlaveUnavailableException ignore) {
            }
        }
    }

    /**
     * Accepts directories and does the physical deletes asynchronously Waits
     * for a response and handles errors on each slave Use
     * RemoteSlave.simpleDelete(path) if you want to delete files
     *
     * @param directory The directory path to delete
     */
    public void deleteOnAllSlaves(DirectoryHandle directory) {
        HashMap<RemoteSlave, String> slaveMap = new HashMap<>();
        Collection<RemoteSlave> slaves = new ArrayList<>(_rSlaves.values());
        for (RemoteSlave rSlave : slaves) {
            String index;
            try {
                AbstractBasicIssuer basicIssuer = (AbstractBasicIssuer) getIssuerForClass(AbstractBasicIssuer.class);
                index = basicIssuer.issueDeleteToSlave(rSlave, directory.getPath());
                slaveMap.put(rSlave, index);
            } catch (SlaveUnavailableException e) {
                rSlave.addQueueDelete(directory.getPath());
            }
        }
        for (Entry<RemoteSlave, String> slaveEntry : slaveMap.entrySet()) {
            RemoteSlave rSlave = slaveEntry.getKey();
            String index = slaveEntry.getValue();
            try {
                rSlave.fetchResponse(index, 300000);
            } catch (SlaveUnavailableException e) {
                rSlave.addQueueDelete(directory.getPath());
            } catch (RemoteIOException e) {
                if (e.getCause() instanceof FileNotFoundException) {
                    continue;
                }
                rSlave.setOffline("IOException deleting file, check logs for specific error");
                rSlave.addQueueDelete(directory.getPath());
                logger.error("IOException deleting file, file will be deleted when slave comes online", e);
                rSlave.addQueueDelete(directory.getPath());
            }
        }
    }

    public void renameOnAllSlaves(String fromPath, String toDirPath, String toName) {
        synchronized (this) {
            for (RemoteSlave rSlave : _rSlaves.values()) {
                rSlave.simpleRename(fromPath, toDirPath, toName);
            }
        }
    }

    public MasterProtocolCentral getProtocolCentral() {
        return _central;
    }

    public AbstractIssuer getIssuerForClass(Class<?> clazz) {
        return _central.getIssuerForClass(clazz);
    }

    public void resetDay(Date d) {
        for (RemoteSlave rs : _rSlaves.values()) {
            rs.resetDay(d);
            rs.commit();
        }
    }

    public void resetHour(Date d) {
        for (RemoteSlave rs : _rSlaves.values()) {
            rs.resetHour(d);
            rs.commit();
        }
    }

    public void resetMonth(Date d) {
        for (RemoteSlave rs : _rSlaves.values()) {
            rs.resetMonth(d);
            rs.commit();
        }
    }

    public void resetWeek(Date d) {
        for (RemoteSlave rs : _rSlaves.values()) {
            rs.resetWeek(d);
            rs.commit();
        }
    }

    public void resetYear(Date d) {
        for (RemoteSlave rs : _rSlaves.values()) {
            rs.resetYear(d);
            rs.commit();
        }
    }
}
