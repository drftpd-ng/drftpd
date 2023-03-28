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
package org.drftpd.slave;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.drftpd.common.exceptions.SSLServiceException;
import org.drftpd.common.network.SSLGetContext;
import org.drftpd.common.network.SSLService;
import org.elasticsearch.common.ssl.SslConfiguration;
import org.elasticsearch.common.ssl.SslConfigurationLoader;

import org.drftpd.common.exceptions.AsyncResponseException;
import org.drftpd.common.exceptions.SSLUnavailableException;
import org.drftpd.common.io.PermissionDeniedException;
import org.drftpd.common.io.PhysicalFile;
import org.drftpd.common.network.AsyncCommandArgument;
import org.drftpd.common.network.AsyncResponse;
import org.drftpd.common.slave.DiskStatus;
import org.drftpd.common.slave.TransferIndex;
import org.drftpd.common.util.ConfigLoader;
import org.drftpd.common.util.PortRange;
import org.drftpd.common.util.PropertyHelper;
import org.drftpd.slave.diskselection.DiskSelectionInterface;
import org.drftpd.slave.exceptions.FileExistsException;
import org.drftpd.slave.network.AsyncResponseDiskStatus;
import org.drftpd.slave.network.AsyncResponseTransferStatus;
import org.drftpd.slave.network.Transfer;
import org.drftpd.slave.protocol.QueuedOperation;
import org.drftpd.slave.protocol.SlaveProtocolCentral;
import org.drftpd.slave.vfs.Root;
import org.drftpd.slave.vfs.RootCollection;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLSocket;
import java.io.*;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.zip.CRC32;
import java.util.zip.CheckedInputStream;

/**
 * @author mog
 * @author zubov
 * @version $Id$
 */
public class Slave extends SslConfigurationLoader {

    public static final boolean isWindows = System.getProperty("os.name").startsWith("Windows");

    private static final String SETTING_PREFIX = "master.ssl.";

    private static final Logger logger = LogManager.getLogger(Slave.class);

    private static final int socketTimeout = 10000; // 10 seconds, for Socket

    private static final int actualTimeout = 60000; // one minute, evaluated on a SocketTimeout

    private int _bufferSize;

    private int _maxPathLength;

    private String[] _cipherSuites;

    private String[] _sslProtocols;

    private SslConfiguration _sslConfig;

    private boolean _downloadChecksums;

    private RootCollection _roots;

    private SSLSocket _socket;

    private ObjectInputStream _sin;

    private ObjectOutputStream _sout;

    private Map<TransferIndex, Transfer> _transfers;

    private boolean _uploadChecksums;

    private PortRange _portRange;

    private int _timeout;

    private SlaveProtocolCentral _central;

    private DiskSelectionInterface _diskSelection = null;

    private boolean _ignorePartialRemerge;

    private boolean _threadedRemerge;

    private int _threadedThreads;

    private boolean _concurrentRootIteration;

    private InetAddress _bindIP;

    private boolean _online;

    private Properties _cfg;

    public Slave(Properties p) throws IOException, SSLUnavailableException {
        super(SETTING_PREFIX);
        _cfg = p;
        try {
            _sslConfig = load(Paths.get(""));
            SSLService.getSSLService().registerSSLConfiguration(SETTING_PREFIX, _sslConfig);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        logger.info("Master connection SSL/TLS initialized as: {}", _sslConfig.toString());
        String masterHost = PropertyHelper.getProperty(p, "master.host");
        int masterPort;
        try {
            masterPort = Integer.parseInt(PropertyHelper.getProperty(p, "master.bindport"));
        } catch(NumberFormatException e) {
            logger.error("Unable to parse port from configuration", e);
            throw new RuntimeException(e);
        }
        InetSocketAddress masterIsa = new InetSocketAddress(masterHost, masterPort);

        // Whatever interface the slave uses to connect to the master, is the
        // interface that the master will report to clients requesting PASV
        // transfers from this slave, unless pasv_addr is set on the master for this
        // slave
        String slaveName = PropertyHelper.getProperty(p, "slave.name");

        logger.info("Slave {} connecting to master at {}", slaveName, masterIsa);

        // Initialize to null
        _bindIP = null;
        try {
            String bindIP = PropertyHelper.getProperty(p, "bind.ip", "");
            logger.debug("'bind.ip' has been resolved to {}", bindIP);
            if (bindIP.length() > 0) {
                _bindIP = InetAddress.getByName(bindIP);
            }
        } catch(UnknownHostException e) {
            logger.warn("'bind.ip' is not a valid ip address");
        } catch(Exception e) {
            logger.error("Unknown error occurred trying to get 'bind.ip' config", e);
        }
        _timeout = Integer.parseInt(PropertyHelper.getProperty(p, "slave.timeout", String.valueOf(actualTimeout)));

        _uploadChecksums = p.getProperty("enableuploadchecksums", "true").equals("true");
        _downloadChecksums = p.getProperty("enabledownloadchecksums", "true").equals("true");
        _bufferSize = Integer.parseInt(p.getProperty("bufferSize", "0"));
        _maxPathLength = Integer.parseInt(p.getProperty("maxPathLength", "4096"));

        _concurrentRootIteration = p.getProperty("concurrent.root.iteration", "false").equalsIgnoreCase("true");
        _roots = getDefaultRootBasket();
        loadDiskSelection(p);

        _transfers = new ConcurrentHashMap<TransferIndex, Transfer>();

        try {
            int minport = Integer.parseInt(p.getProperty("slave.portfrom"));
            int maxport = Integer.parseInt(p.getProperty("slave.portto"));
            _portRange = new PortRange(minport, maxport, _bufferSize);
        } catch (NumberFormatException e) {
            logger.warn("Unable to read port range from config, falling back to default random port range " +
                    "specified by the operating system");
            _portRange = new PortRange(_bufferSize);
        }

        _ignorePartialRemerge = p.getProperty("ignore.partialremerge", "false").equalsIgnoreCase("true");
        _threadedRemerge = p.getProperty("threadedremerge", "false").equalsIgnoreCase("true");
        _threadedThreads = 0;
        try {
            _threadedThreads = Integer.parseInt(p.getProperty("threadedthreads", "0"));
        } catch (NumberFormatException e) {
            logger.warn("Unable to read threadedthreads from config, falling back to cpu core calculation");
        }

        // Initialize this before we connect a socket
        _central = new SlaveProtocolCentral(this);

        try {
            _socket = (SSLSocket) SSLService.getSSLService().sslSocketFactory(_sslConfig).createSocket();
        } catch (IOException | SSLServiceException e) {
            throw new RuntimeException("Something went wrong connecting to master", e);
        }

        if (getBindIP() != null) {
            try {
                _socket.bind(new InetSocketAddress(getBindIP(), 0));
            } catch (IOException e) {
                throw new IOException("Unable to bind to ["+getBindIP()+":0]", e);
            }
        }

        _socket.setSoTimeout(socketTimeout);
        _socket.connect(masterIsa);
        _socket.setUseClientMode(true);

        logger.debug("[{}] Enabled ciphers for this new connection are as follows: '{}'", _socket.getRemoteSocketAddress(), Arrays.toString(_socket.getEnabledCipherSuites()));
        logger.debug("[{}] Enabled protocols for this new connection are as follows: '{}'", _socket.getRemoteSocketAddress(), Arrays.toString(_socket.getEnabledProtocols()));

        try {
            _socket.startHandshake();
        } catch (SSLHandshakeException e) {
            throw new SSLUnavailableException("Handshake failure, maybe master isn't SSL ready or SSL is disabled.", e);
        }

        _sout = new ObjectOutputStream(new BufferedOutputStream(_socket.getOutputStream()));
        _sout.flush();
        _sin = new ObjectInputStream(new BufferedInputStream(_socket.getInputStream()));

        _sout.writeObject(slaveName);
        _sout.flush();
        _sout.reset();
    }

    public Properties getConfig() {
        return _cfg;
    }

    public static void main(String... args) throws Exception {
        Slave.boot();
    }

    public static void boot() throws Exception {
        System.out.println("DrFTPD Slave starting, further logging will be done through log4j");
        Thread.currentThread().setName("Slave Main Thread");

        Properties p = ConfigLoader.loadConfig("slave.conf");
        Slave s = new Slave(p);

        // Register to master
        s.getProtocolCentral().handshakeWithMaster();

        try {
            s.sendResponse(new AsyncResponseDiskStatus(s.getDiskStatus()));
        } catch (Throwable t) {
            logger.fatal("Error, check config on master for this slave");
        }
        s.setOnline(true);
        try {
            s.listenForCommands();
        } finally {
            s.shutdown();
        }
    }

    private void loadDiskSelection(Properties cfg) {
        String desiredDs = PropertyHelper.getProperty(cfg, "diskselection");
        try {
            Class<?> aClass = Class.forName(desiredDs);
            _diskSelection = (DiskSelectionInterface) aClass.getConstructor(Slave.class).newInstance(this);
        } catch (Exception e) {
            throw new RuntimeException("Cannot create instance of diskselection, check 'diskselection' in the configuration file", e);
        }
    }

    public DiskSelectionInterface getDiskSelection() {
        return _diskSelection;
    }

    private RootCollection getDefaultRootBasket() throws IOException {
        ArrayList<Root> roots = new ArrayList<>();

        for (int i = 1; true; i++) {
            String rootString = _cfg.getProperty("slave.root." + i);

            if (rootString == null) {
                break;
            }

            logger.info("slave.root.{}: {}", i, rootString);
            roots.add(new Root(rootString));
        }

        return new RootCollection(this, roots);
    }

    public void shutdown() {
        if (_sin != null) {
            try {
                _sin.close();
            } catch (IOException ignored) {
            }
            _sin = null;
        }
        if (_sout != null) {
            try {
                _sout.flush();
                _sout.close();
            } catch (IOException ignored) {
            }
            _sout = null;
        }
        if (_socket != null) {
            try {
                _socket.close();
            } catch (IOException ignored) {
            }
            _socket = null;
        }
        setOnline(false);
    }

    public boolean isOnline() {
        return _online;
    }

    public void setOnline(boolean online) {
        _online = online;
    }

    public void addTransfer(Transfer transfer) {
        _transfers.put(transfer.getTransferIndex(), transfer);
    }

    public long checkSum(String path) throws IOException {
        return checkSum(_roots.getFile(path));
    }

    public long checkSum(PhysicalFile file) throws IOException {
        logger.debug("Checksumming: {}", file.getPath());

        CRC32 crc32 = new CRC32();
        try (CheckedInputStream in = new CheckedInputStream(new BufferedInputStream(new FileInputStream(file)), crc32)) {
            byte[] buf = new byte[16384];
            while (true) {
                if (in.read(buf) == -1) {
                    break;
                }
            }
            return crc32.getValue();
        }
    }

    public void delete(String path) throws IOException {
        // now deletes files as well as directories, recursive!
        Collection<Root> files;
        try {
            files = _roots.getMultipleRootsForFile(path);
        } catch (FileNotFoundException e) {
            // all is good, it's already gone
            return;
        }

        for (Iterator<Root> iter = files.iterator(); iter.hasNext(); ) {
            Root root = iter.next();
            PhysicalFile file = root.getFile(path);

            if (!file.exists()) {
                iter.remove();
                continue;
                // should never occur
            }

            if (file.isDirectory()) {
                if (!file.deleteRecursive()) {
                    throw new PermissionDeniedException("delete failed on " + path);
                }
                logger.info("DELETEDIR: {}", path);
            } else if (file.isFile()) {
                File dir = new PhysicalFile(file.getParentFile());
                logger.info("DELETE: {}", path);
                logger.info("rmfile: {}", file.getPath());
                if (!file.delete()) {
                    throw new PermissionDeniedException("delete failed on " + path);
                }

                String[] dirList = dir.list();

                // If the parent directory is empty, then loop to delete it along with empty
                // parents
                while ((dirList != null) && (dirList.length == 0)) {
                    // Stop at the root
                    if (dir.getPath().length() <= root.getPath().length()) {
                        break;
                    }

                    // Get the parent dir
                    java.io.File tmpFile = dir.getParentFile();

                    try {
                        if (Files.deleteIfExists(dir.toPath())) {
                            logger.info("Dir empty, rmdir: {}", dir.getPath());
                        } else {
                            logger.info("dir was empty, but doesn't exist anymore, that is fine {}", dir.getPath());
                        }
                    } catch (DirectoryNotEmptyException dnee) {
                        logger.info("dir was not empty, that is fine, we keep {}", dir.getPath());
                        break;
                    }

                    // If the parent dir doesn't exist, break the loop
                    if (tmpFile == null) {
                        break;
                    }

                    // Rearm the loop on the parent dir
                    dir = new PhysicalFile(tmpFile);
                    dirList = dir.list();
                }
            }
        }
    }

    public int getBufferSize() {
        return _bufferSize;
    }

    public int getMaxPathLength() {
        return _maxPathLength;
    }

    public boolean getDownloadChecksums() {
        return _downloadChecksums;
    }

    public RootCollection getRoots() {
        return _roots;
    }

    public DiskStatus getDiskStatus() {
        return new DiskStatus(_roots.getTotalDiskSpaceAvailable(), _roots.getTotalDiskSpaceCapacity());
    }

    public Transfer getTransfer(TransferIndex index) {
        return _transfers.get(index);
    }

    public boolean getUploadChecksums() {
        return _uploadChecksums;
    }

    private AsyncResponse handleCommand(AsyncCommandArgument ac) {
        return _central.handleCommand(ac);
    }

    private void listenForCommands() throws IOException {
        long lastCommandReceived = System.currentTimeMillis();
        while (true) {
            AsyncCommandArgument ac;

            try {
                ac = (AsyncCommandArgument) _sin.readObject();

                if (ac == null) {
                    continue;
                }
                lastCommandReceived = System.currentTimeMillis();
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            } catch (EOFException e) {
                logger.debug("Lost connection to the master, may have been kicked offline");
                return;
            } catch (SocketTimeoutException e) {
                // if no communication for slave.timeout (_timeout) time, than
                // connection to the master is dead or there is a configuration
                // error
                if (_timeout < (System.currentTimeMillis() - lastCommandReceived)) {
                    logger.error("Slave is going offline as it hasn't received any communication from the master in {} milliseconds", System.currentTimeMillis() - lastCommandReceived);
                    throw new RuntimeException(e);
                }
                continue;
            }

            logger.debug("Slave fetched {}", ac);
            class AsyncCommandHandler implements Runnable {
                private final AsyncCommandArgument _command;

                public AsyncCommandHandler(AsyncCommandArgument command) {
                    _command = command;
                }

                public void run() {
                    try {
                        sendResponse(handleCommand(_command));
                    } catch (Throwable e) {
                        sendResponse(new AsyncResponseException(_command.getIndex(), e));
                    }
                }
            }
            Thread t = new Thread(new AsyncCommandHandler(ac));
            t.setName("AsyncCommandHandler - " + ac.getClass());
            t.start();
        }
    }

    public void removeTransfer(Transfer transfer) {
        if (_transfers.remove(transfer.getTransferIndex()) == null) {
            throw new IllegalStateException();
        }
    }

    public void rename(String from, String toDirPath, String toName) throws IOException {
        for (Iterator<Root> iter = _roots.iterator(); iter.hasNext(); ) {
            Root root = iter.next();

            File fromfile = root.getFile(from);

            if (!fromfile.exists()) {
                continue;
            }

            File toDir = root.getFile(toDirPath);
            File tofile = new File(toDir.getPath() + File.separator + toName);

            if (!toDir.exists() && !toDir.mkdirs()) {
                throw new PermissionDeniedException(
                        "renameTo(" + fromfile + ", " + tofile + ") failed to create destination folder");
            }

            // !Windows == true on Linux/Unix/AIX...
            // !Windows && equalsignore == true on Windows
            if (tofile.exists() && !(isWindows && fromfile.getName().equalsIgnoreCase(toName))) {
                throw new FileExistsException(
                        "cannot rename from " + fromfile + " to " + tofile + ", destination exists");
            }

            if (!fromfile.renameTo(tofile)) {
                throw new PermissionDeniedException("renameTo(" + fromfile + ", " + tofile + ") failed");
            }
        }
    }

    public synchronized void sendResponse(AsyncResponse response) {
        if (response == null) {
            // handler doesn't return anything or it sends reply on it's own
            // (threaded for example)
            return;
        }

        try {
            _sout.writeObject(response);
            _sout.flush();
            _sout.reset();
            if (!(response instanceof AsyncResponseTransferStatus)) {
                logger.debug("Slave wrote response - {}", response);
            }

            if (response instanceof AsyncResponseException) {
                logger.debug("", ((AsyncResponseException) response).getThrowable());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @return The current list of Transfer objects
     */
    public ArrayList<Transfer> getTransfersList() {
        return new ArrayList<>(_transfers.values());
    }

    public String[] getCipherSuites() {
        // returns null if none are configured explicitly
        if (_cipherSuites == null) {
            return null;
        }
        return _cipherSuites;
    }

    public String[] getSSLProtocols() {
        // returns null if none are configured explicitly
        if (_sslProtocols == null) {
            return null;
        }
        return _sslProtocols;
    }

    public Map<TransferIndex, Transfer> getTransferMap() {
        return _transfers;
    }

    public SSLContext getSSLContext() {
        try {
            return SSLGetContext.getSSLContext();
        } catch(GeneralSecurityException | IOException e) {
            return null;
        }
    }

    public PortRange getPortRange() {
        return _portRange;
    }

    public InetAddress getBindIP() {
        return _bindIP;
    }

    public ObjectInputStream getInputStream() {
        return _sin;
    }

    public ObjectOutputStream getOutputStream() {
        return _sout;
    }

    public SlaveProtocolCentral getProtocolCentral() {
        return _central;
    }

    public boolean ignorePartialRemerge() {
        return _ignorePartialRemerge;
    }

    public boolean threadedRemerge() {
        return _threadedRemerge;
    }

    public int threadedThreads() {
        return _threadedThreads;
    }

    public boolean concurrentRootIteration() {
        return _concurrentRootIteration;
    }

    protected String getSettingAsString(String key) throws Exception {
        logger.debug("Looking up key: {} as String", key);
        return getConfig().getProperty(key);
    }

    protected char[] getSecureSetting(String key) throws Exception {
        logger.debug("!!NOT IMPLEMENTED!! Looking up key: {} as char[] - !!NOT IMPLEMENTED!!", key);
        return null;
    }

    protected List<String> getSettingAsList(String key) throws Exception {
        logger.debug("Looking up key: {} as List<String>", key);
        List<String> data = PropertyHelper.getStringListedProperty(getConfig(), key);
        if (data == null) {
            return null;
        }
        logger.debug("Got List<String> for {} as -> [{}]", key, Arrays.toString(data.toArray()));
        return data;
    }
}
