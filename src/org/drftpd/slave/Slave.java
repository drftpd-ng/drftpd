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
package org.drftpd.slave;

import com.Ostermiller.util.StringTokenizer;

import net.sf.drftpd.Bytes;
import net.sf.drftpd.FatalException;
import net.sf.drftpd.FileExistsException;
import net.sf.drftpd.ID3Tag;
import net.sf.drftpd.MP3File;
import net.sf.drftpd.PermissionDeniedException;
import net.sf.drftpd.SFVFile;
import net.sf.drftpd.master.config.FtpConfig;
import net.sf.drftpd.remotefile.FileRemoteFile;
import net.sf.drftpd.remotefile.LinkedRemoteFile;
import net.sf.drftpd.remotefile.LinkedRemoteFileInterface;
import net.sf.drftpd.slave.ActiveConnection;
import net.sf.drftpd.slave.PassiveConnection;
import net.sf.drftpd.slave.Root;
import net.sf.drftpd.slave.RootBasket;
import net.sf.drftpd.slave.SlaveStatus;
import net.sf.drftpd.slave.TransferStatus;
import net.sf.drftpd.util.PortRange;
import net.sf.drftpd.util.SSLGetContext;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Logger;

import org.drftpd.remotefile.LightRemoteFile;

import org.drftpd.slave.async.AsyncCommand;
import org.drftpd.slave.async.AsyncCommandArgument;
import org.drftpd.slave.async.AsyncResponse;
import org.drftpd.slave.async.AsyncResponseChecksum;
import org.drftpd.slave.async.AsyncResponseException;
import org.drftpd.slave.async.AsyncResponseID3Tag;
import org.drftpd.slave.async.AsyncResponseMaxPath;
import org.drftpd.slave.async.AsyncResponseRemerge;
import org.drftpd.slave.async.AsyncResponseSFVFile;
import org.drftpd.slave.async.AsyncResponseSlaveStatus;
import org.drftpd.slave.async.AsyncResponseTransfer;
import org.drftpd.slave.async.AsyncResponseTransferStatus;

import se.mog.io.File;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Properties;
import java.util.zip.CRC32;
import java.util.zip.CheckedInputStream;

import javax.net.ssl.SSLContext;


/**
 * @author mog
 * @version $Id: Slave.java,v 1.8 2004/11/08 02:37:34 zubov Exp $
 */
public class Slave {
    public static final boolean isWin32 = System.getProperty("os.name")
                                                .startsWith("Windows");
    private static final Logger logger = Logger.getLogger(Slave.class);
    private static final int TIMEOUT = 10000;
    public static final String VERSION = "DrFTPD 1.2-CVS";
    private int _bufferSize;
    private SSLContext _ctx;
    private boolean _downloadChecksums;
    private long _receivedBytes;
    private RootBasket _roots;
    private Socket _s;
    private long _sentBytes;
    private ObjectInputStream _sin;
    private ObjectOutputStream _sout;
    private HashMap _transfers;
    private boolean _uploadChecksums;
    private PortRange _portRange;
    private InetAddress _externalAddress = null;

    public Slave(Properties p) throws IOException {
        InetSocketAddress addr = new InetSocketAddress(FtpConfig.getProperty(
                    p, "master.host"),
                Integer.parseInt(FtpConfig.getProperty(p, "master.bindport")));
        logger.info("Connecting to master at " + addr);

        String slavename = FtpConfig.getProperty(p, "slave.name");

        try {
        _externalAddress = InetAddress.getByName(FtpConfig.getProperty(p,
                    "slave.interface"));
        } catch(NullPointerException e) {
        	//value is already null
        	//_externalAddress = null;
        }
        _s = new Socket();
        _s.connect(addr);

        _sout = new ObjectOutputStream(_s.getOutputStream());
        _sin = new ObjectInputStream(_s.getInputStream());

        //TODO sendReply()
        _sout.writeObject(slavename);
        _sout.flush();

        try {
            _ctx = SSLGetContext.getSSLContext();
        } catch (Exception e) {
            logger.warn("Error loading SSLContext", e);
        }

        _uploadChecksums = p.getProperty("enableuploadchecksums", "true")
                            .equals("true");
        _downloadChecksums = p.getProperty("enabledownloadchecksums", "true")
                              .equals("true");
        _bufferSize = Integer.parseInt(p.getProperty("bufferSize", "0"));
        _roots = getDefaultRootBasket(p);
        _transfers = new HashMap();
        int minport = Integer.parseInt(p.getProperty("slave.portfrom", "0"));
        int maxport = Integer.parseInt(p.getProperty("slave.portto", "0"));
        if (minport == 0 || maxport == 0) {
            _portRange = new PortRange();
        } else {
            _portRange = new PortRange(minport,maxport);
        }
    }

    public static LinkedRemoteFile getDefaultRoot(RootBasket rootBasket)
        throws IOException {
        LinkedRemoteFile linkedroot = new LinkedRemoteFile(new FileRemoteFile(
                    rootBasket), null);

        return linkedroot;
    }

    public static RootBasket getDefaultRootBasket(Properties cfg)
        throws IOException {
        RootBasket roots;

        // START: RootBasket
        long defaultMinSpaceFree = Bytes.parseBytes(cfg.getProperty(
                    "slave.minspacefree", "50mb"));
        ArrayList rootStrings = new ArrayList();

        for (int i = 1; true; i++) {
            String rootString = cfg.getProperty("slave.root." + i);

            if (rootString == null) {
                break;
            }

            System.out.println("slave.root." + i + ": " + rootString);

            /*
             * long minSpaceFree;
             *
             * try { minSpaceFree = Long.parseLong(cfg.getProperty("slave.root." +
             * i + ".minspacefree")); } catch (NumberFormatException ex) {
             * minSpaceFree = defaultMinSpaceFree; }
             *
             * int priority;
             *
             * try { priority = Integer.parseInt(cfg.getProperty("slave.root." +
             * i + ".priority")); } catch (NumberFormatException ex) { priority =
             * 0; }
             */
            rootStrings.add(new Root(rootString));
        }

        try {
            roots = new RootBasket(rootStrings);
        } catch (IOException e) {
            throw new FatalException(e);
        }

        // END: RootBasket
        System.gc();

        return roots;
    }

    public static void main(String[] args) throws Exception {
        BasicConfigurator.configure();
        System.out.println(
            "DrFTPD Slave starting, further logging will be done through log4j");

        Properties p = new Properties();
        p.load(new FileInputStream("slave.conf"));

        Slave s = new Slave(p);
        s.listenForCommands();
    }

    public void addTransfer(Transfer transfer) {
        synchronized (_transfers) {
            _transfers.put(transfer.getTransferIndex(), transfer);
        }
    }

    public long checkSum(String path) throws IOException {
        logger.debug("Checksumming: " + path);

        CheckedInputStream in = null;

        try {
            CRC32 crc32 = new CRC32();
            in = new CheckedInputStream(new FileInputStream(_roots.getFile(path)),
                    crc32);

            byte[] buf = new byte[4096];

            while (in.read(buf) != -1) {
            }

            return crc32.getValue();
        } finally {
            in.close();
        }
    }

    public void delete(String path) throws IOException {
        Collection files = _roots.getMultipleRootsForFile(path);

        for (Iterator iter = files.iterator(); iter.hasNext();) {
            Root root = (Root) iter.next();
            File file = root.getFile(path);

            if (!file.exists()) {
                throw new FileNotFoundException(file.getAbsolutePath() +
                    " does not exist.");
            }

            if (!file.delete()) {
                throw new PermissionDeniedException("delete failed on " + path);
            }

            File dir = new File(file.getParentFile());

            //TODO don't go above empty root
            logger.debug("DELETE: " + path);

            while (dir.list().length == 0) {
                file.delete();
                logger.debug("DELETEFS: " + file.getPath());

                java.io.File tmpFile = dir.getParentFile();

                if (tmpFile == null) {
                    break;
                }

                if (tmpFile.getPath().length() < root.getPath().length()) {
                    throw new SecurityException("Attempt to break out of root");
                }

                dir = new File(tmpFile);
            }
        }
    }

    public int getBufferSize() {
        return _bufferSize;
    }

    public boolean getDownloadChecksums() {
        return _downloadChecksums;
    }

    public ID3Tag getID3v1Tag(String path) throws IOException {
        String absPath = _roots.getFile(path).getAbsolutePath();
        logger.warn("Extracting ID3Tag info from: " + absPath);

        try {
            MP3File mp3 = new MP3File(absPath, "r");

            if (!mp3.hasID3v1Tag) {
                mp3.close();
                throw new IOException("No id3tag found for " + absPath);
            }

            ID3Tag id3tag = mp3.readID3v1Tag();
            mp3.close();

            return id3tag;
        } catch (FileNotFoundException e) {
            logger.warn("FileNotFoundException: ", e);
        } catch (IOException e) {
            logger.warn("IOException: ", e);
        }

        return null;
    }

    public RootBasket getRoots() {
        return _roots;
    }

    public SFVFile getSFVFile(String path) throws IOException {
        return new SFVFile(new BufferedReader(
                new FileReader(_roots.getFile(path))));
    }

    public LinkedRemoteFile getSlaveRoot() throws IOException {
        return Slave.getDefaultRoot(_roots);
    }

    public SlaveStatus getSlaveStatus() {
        int throughputUp = 0;
        int throughputDown = 0;
        int transfersUp = 0;
        int transfersDown = 0;
        long bytesReceived;
        long bytesSent;

        synchronized (_transfers) {
            bytesReceived = _receivedBytes;
            bytesSent = _sentBytes;

            for (Iterator i = _transfers.values().iterator(); i.hasNext();) {
                Transfer transfer = (Transfer) i.next();

                switch (transfer.getState()) {
                case RemoteTransfer.TRANSFER_RECEIVING_UPLOAD:
                    throughputUp += transfer.getXferSpeed();
                    transfersUp += 1;
                    bytesReceived += transfer.getTransfered();

                    break;

                case RemoteTransfer.TRANSFER_SENDING_DOWNLOAD:
                    throughputDown += transfer.getXferSpeed();
                    transfersDown += 1;
                    bytesSent += transfer.getTransfered();

                    break;

                case RemoteTransfer.TRANSFER_UNKNOWN:
                case RemoteTransfer.TRANSFER_THROUGHPUT:
                    break;

                default:
                    throw new FatalException("unrecognized direction");
                }
            }
        }

        try {
            return new SlaveStatus(_roots.getTotalDiskSpaceAvailable(),
                _roots.getTotalDiskSpaceCapacity(), bytesSent, bytesReceived,
                throughputUp, transfersUp, throughputDown, transfersDown);
        } catch (Exception ex) {
            ex.printStackTrace();
            throw new RuntimeException(ex.toString());
        }
    }

    public Transfer getTransfer(TransferIndex index) {
        synchronized (_transfers) {
            return (Transfer) _transfers.get(index);
        }
    }

    public boolean getUploadChecksums() {
        return _uploadChecksums;
    }

    private AsyncResponse handleChecksum(AsyncCommandArgument ac) {
        try {
            return new AsyncResponseChecksum(ac.getIndex(),
                checkSum(ac.getArgs()));
        } catch (IOException e) {
            return new AsyncResponseException(ac.getIndex(), e);
        }
    }

    private AsyncResponse handleCommand(AsyncCommand ac) {
        if (ac.getName().equals("status")) {
            return handleStatus(ac);
        }

        if (ac.getName().equals("remerge")) {
            return handleRemerge((AsyncCommandArgument) ac);
        }

        if (ac.getName().equals("checksum")) {
            return handleChecksum((AsyncCommandArgument) ac);
        }

        if (ac.getName().equals("connect")) {
            return handleConnect((AsyncCommandArgument) ac);
        }

        if (ac.getName().equals("delete")) {
            return handleDelete((AsyncCommandArgument) ac);
        }

        if (ac.getName().equals("id3tag")) {
            return handleID3Tag((AsyncCommandArgument) ac);
        }

        if (ac.getName().equals("listen")) {
            return handleListen((AsyncCommandArgument) ac);
        }

        if (ac.getName().equals("maxpath")) {
            return handleMaxpath(ac);
        }

        if (ac.getName().equals("ping")) {
            return handlePing(ac);
        }

        if (ac.getName().equals("receive")) {
            return handleReceive((AsyncCommandArgument) ac);
        }

        if (ac.getName().equals("rename")) {
            return handleRename((AsyncCommandArgument) ac);
        }

        if (ac.getName().equals("sfvfile")) {
            return handleSfvFile((AsyncCommandArgument) ac);
        }

        if (ac.getName().equals("send")) {
            return handleSend((AsyncCommandArgument) ac);
        }

        if (ac.getName().equals("abort")) {
            handleAbort((AsyncCommandArgument) ac);

            return null;
        }
        if (ac.getName().equals("error")) {
            throw new RuntimeException("error - " + ac);
        }

        return new AsyncResponseException(ac.getIndex(),
            new Exception(ac.getName() + " - Operation Not Supported"));
    }

    private void handleAbort(AsyncCommandArgument argument) {
        TransferIndex ti = new TransferIndex(Integer.parseInt(
                    argument.getArgs()));

        if (!_transfers.containsKey(ti)) {
            return;
        }

        Transfer t = (Transfer) _transfers.get(ti);
        t.abort();
    }

    private AsyncResponse handleConnect(AsyncCommandArgument ac) {
        String[] data = ac.getArgs().split(",");
        String[] data2 = data[0].split(":");
        InetAddress address;

        try {
            address = InetAddress.getByName(data2[0]);
        } catch (UnknownHostException e1) {
            return new AsyncResponseException(ac.getIndex(), e1);
        }

        int port = Integer.parseInt(data2[1]);
        boolean encrypted = data[1].equals("true");
        Transfer t = new Transfer(new ActiveConnection(encrypted ? _ctx : null,
                    new InetSocketAddress(address, port)), this,
                new TransferIndex());
        addTransfer(t);

        return new AsyncResponseTransfer(ac.getIndex(),
            new ConnectInfo(address, port, t.getTransferIndex(),
                t.getTransferStatus()));
    }

    private AsyncResponse handleDelete(AsyncCommandArgument ac) {
        try {
            delete(ac.getArgs());

            return new AsyncResponse(ac.getIndex());
        } catch (IOException e) {
            return new AsyncResponseException(ac.getIndex(), e);
        }
    }

    private AsyncResponse handleID3Tag(AsyncCommandArgument ac) {
        try {
            return new AsyncResponseID3Tag(ac.getIndex(),
                getID3v1Tag(ac.getArgs()));
        } catch (IOException e) {
            return new AsyncResponseException(ac.getIndex(), e);
        }
    }

    private AsyncResponse handleListen(AsyncCommandArgument ac) {
        boolean encrypted = ac.getArgs().equals("true");
        PassiveConnection c = null;

        try {
            c = new PassiveConnection(encrypted ? _ctx : null, _portRange);
        } catch (IOException e) {
            return new AsyncResponseException(ac.getIndex(), e);
        }

        Transfer t = new Transfer(c, this, new TransferIndex());
        addTransfer(t);

        return new AsyncResponseTransfer(ac.getIndex(),
            new ConnectInfo(_externalAddress, c.getLocalPort(),
                t.getTransferIndex(), t.getTransferStatus()));
    }

    private AsyncResponse handleMaxpath(AsyncCommand ac) {
        return new AsyncResponseMaxPath(ac.getIndex(), 255);
    }

    private AsyncResponse handlePing(AsyncCommand ac) {
        return new AsyncResponse(ac.getIndex());
    }

    private AsyncResponse handleReceive(AsyncCommandArgument ac) {
        StringTokenizer st = new StringTokenizer(ac.getArgs(), ",");
        char type = st.nextToken().charAt(0);
        long position = Long.parseLong(st.nextToken());
        TransferIndex transferIndex = new TransferIndex(Integer.parseInt(
                    st.nextToken()));
        String path = st.nextToken();
        String fileName = path.substring(path.lastIndexOf("/") + 1);
        String dirName = path.substring(0, path.lastIndexOf("/"));
        Transfer t = getTransfer(transferIndex);
        sendResponse(new AsyncResponse(ac.getIndex())); // return calling thread on master

        try {
            return new AsyncResponseTransferStatus(t.receiveFile(dirName, type,
                    fileName, position));
        } catch (IOException e) {
            return (new AsyncResponseTransferStatus(new TransferStatus(
                    transferIndex, e)));
        }
    }

    private AsyncResponse handleRemerge(AsyncCommandArgument ac) {
        try {
            handleRemergeRecursive(getSlaveRoot().lookupFile(ac.getArgs()));

            return new AsyncResponse(ac.getIndex());
        } catch (FileNotFoundException e) {
            return new AsyncResponse(ac.getIndex());
        } catch (IOException e) {
            return new AsyncResponseException(ac.getIndex(), e);
        }
    }

    private void handleRemergeRecursive(LinkedRemoteFileInterface file) {
        LinkedRemoteFile.CaseInsensitiveHashtable files = new LinkedRemoteFile.CaseInsensitiveHashtable();

        for (Iterator iter = file.getFiles().iterator(); iter.hasNext();) {
            LinkedRemoteFileInterface lrf = (LinkedRemoteFileInterface) iter.next();

            if (lrf.isDirectory()) {
                handleRemergeRecursive(lrf);
            }

            if (lrf.isFile()) {
                files.put(lrf.getName(),
                    new LightRemoteFile(lrf.getName(), lrf.lastModified(),
                        lrf.length()));
            }
        }

        sendResponse(new AsyncResponseRemerge(file.getPath(), files));
    }

    private AsyncResponse handleRename(AsyncCommandArgument ac) {
        StringTokenizer st = new StringTokenizer(ac.getArgs(), ",");
        String from = st.nextToken();
        String toDir = st.nextToken();
        String toFile = st.nextToken();

        try {
            rename(from, toDir, toFile);

            return new AsyncResponse(ac.getIndex());
        } catch (IOException e) {
            return new AsyncResponseException(ac.getIndex(), e);
        }
    }

    private AsyncResponse handleSend(AsyncCommandArgument ac) {
        StringTokenizer st = new StringTokenizer(ac.getArgs(), ",");
        char type = st.nextToken().charAt(0);
        long position = Long.parseLong(st.nextToken());
        TransferIndex transferIndex = new TransferIndex(Integer.parseInt(
                    st.nextToken()));
        String path = st.nextToken();
        Transfer t = getTransfer(transferIndex);
        sendResponse(new AsyncResponse(ac.getIndex())); // return calling thread on master

        try {
            return new AsyncResponseTransferStatus(t.sendFile(path, type,
                    position));
        } catch (IOException e) {
            return new AsyncResponseTransferStatus(new TransferStatus(
                    t.getTransferIndex(), e));
        }
    }

    private AsyncResponse handleSfvFile(AsyncCommandArgument ac) {
        try {
            return new AsyncResponseSFVFile(ac.getIndex(),
                getSFVFile(ac.getArgs()));
        } catch (IOException e) {
            return new AsyncResponseException(ac.getIndex(), e);
        }
    }

    private AsyncResponse handleStatus(AsyncCommand ac) {
        return new AsyncResponseSlaveStatus(ac.getIndex(), getSlaveStatus());
    }

    private void listenForCommands() {
        while (true) {
            AsyncCommand ac;

            try {
                ac = (AsyncCommand) _sin.readObject();

                if (ac == null) {
                    continue;
                }
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            } catch (IOException e) {
                logger.debug("IOException - " + e.getMessage());
                throw new FatalException(e);
            }

            logger.debug("Slave fetched " + ac);
            class AsyncCommandHandler implements Runnable {
                private AsyncCommand _command = null;

                public AsyncCommandHandler(AsyncCommand command) {
                    _command = command;
                }

                public void run() {
                    try {
                        sendResponse(handleCommand(_command));
                    } catch (Exception e) {
                        sendResponse(new AsyncResponseException(
                                _command.getIndex(), e));
                    }
                }
            }
            new Thread(new AsyncCommandHandler(ac)).start();
        }
    }

    public void removeTransfer(Transfer transfer) {
        synchronized (_transfers) {
            switch (transfer.getState()) {
            case RemoteTransfer.TRANSFER_RECEIVING_UPLOAD:
                _receivedBytes += transfer.getTransfered();

                break;

            case RemoteTransfer.TRANSFER_SENDING_DOWNLOAD:
                _sentBytes += transfer.getTransfered();

                break;

            default:
                throw new IllegalArgumentException();
            }

            if (_transfers.remove(transfer.getTransferIndex()) == null) {
                throw new IllegalStateException();
            }
        }
    }

    public void rename(String from, String toDirPath, String toName)
        throws IOException {
        for (Iterator iter = _roots.iterator(); iter.hasNext();) {
            Root root = (Root) iter.next();

            File fromfile = root.getFile(from);

            if (!fromfile.exists()) {
                continue;
            }

            File toDir = root.getFile(toDirPath);
            toDir.mkdirs();

            File tofile = new File(toDir.getPath() + File.separator + toName);

            //!win32 == true on linux
            //!win32 && equalsignore == true on win32
            if (tofile.exists() &&
                    !(isWin32 && fromfile.getName().equalsIgnoreCase(toName))) {
                throw new FileExistsException("cannot rename from " + fromfile +
                    " to " + tofile + ", destination exists");
            }

            if (!fromfile.renameTo(tofile)) {
                throw new IOException("renameTo(" + fromfile + ", " + tofile +
                    ") failed");
            }
        }
    }

    protected synchronized void sendResponse(AsyncResponse response) {
        if (response == null) {
            return;
        }

        try {
            _sout.writeObject(response);
            _sout.flush();
            logger.debug("Slave wrote response - " + response);
            if (response instanceof AsyncResponseException) {
                logger.debug("",((AsyncResponseException) response).getThrowable());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
