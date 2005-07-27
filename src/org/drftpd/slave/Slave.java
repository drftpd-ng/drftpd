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

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.EOFException;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Properties;
import java.util.Set;
import java.util.zip.CRC32;
import java.util.zip.CheckedInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.net.ssl.SSLContext;

import net.sf.drftpd.FileExistsException;
import net.sf.drftpd.ObjectNotFoundException;
import net.sf.drftpd.util.PortRange;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Logger;
import org.drftpd.ActiveConnection;
import org.drftpd.LightSFVFile;
import org.drftpd.PassiveConnection;
import org.drftpd.PropertyHelper;
import org.drftpd.SSLGetContext;
import org.drftpd.id3.ID3Tag;
import org.drftpd.id3.MP3File;
import org.drftpd.master.QueuedOperation;
import org.drftpd.remotefile.CaseInsensitiveHashtable;
import org.drftpd.remotefile.LightRemoteFile;
import org.drftpd.slave.async.AsyncCommand;
import org.drftpd.slave.async.AsyncCommandArgument;
import org.drftpd.slave.async.AsyncResponse;
import org.drftpd.slave.async.AsyncResponseChecksum;
import org.drftpd.slave.async.AsyncResponseDiskStatus;
import org.drftpd.slave.async.AsyncResponseDIZFile;
import org.drftpd.slave.async.AsyncResponseException;
import org.drftpd.slave.async.AsyncResponseID3Tag;
import org.drftpd.slave.async.AsyncResponseMaxPath;
import org.drftpd.slave.async.AsyncResponseRemerge;
import org.drftpd.slave.async.AsyncResponseSFVFile;
import org.drftpd.slave.async.AsyncResponseTransfer;
import org.drftpd.slave.async.AsyncResponseTransferStatus;

import se.mog.io.File;
import se.mog.io.PermissionDeniedException;

import com.Ostermiller.util.StringTokenizer;


/**
 * @author mog
 * @author zubov
 * @version $Id$
 */
public class Slave {
    public static final boolean isWin32 = System.getProperty("os.name")
                                                .startsWith("Windows");
    private static final Logger logger = Logger.getLogger(Slave.class);
    public static final String VERSION = "DrFTPD 2.0-rc5";
    private int _bufferSize;
    private SSLContext _ctx;
    private boolean _downloadChecksums;
    private RootCollection _roots;
    private Socket _s;
    private ObjectInputStream _sin;
    private ObjectOutputStream _sout;
    private HashMap _transfers;
    private boolean _uploadChecksums;
    private PortRange _portRange;
    private Set _renameQueue = null;
    
    protected Slave() {
    	
    }

    public Slave(Properties p) throws IOException {
        InetSocketAddress addr = new InetSocketAddress(PropertyHelper.getProperty(
                    p, "master.host"),
                Integer.parseInt(PropertyHelper.getProperty(p, "master.bindport")));
        logger.info("Connecting to master at " + addr);

        String slavename = PropertyHelper.getProperty(p, "slave.name");
        
        if (isWin32) {
        	_renameQueue = new HashSet();
        }

        _s = new Socket();
        _s.connect(addr);
        int timeout = 0;
        try {
        	timeout = Integer.parseInt(PropertyHelper.getProperty(p, "slave.timeout"));
        } catch (NullPointerException e) {
        	timeout = 300000; // 5 minute default
        }
        _s.setSoTimeout(timeout);

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

        if ((minport == 0) || (maxport == 0)) {
            _portRange = new PortRange();
        } else {
            _portRange = new PortRange(minport, maxport);
        }
    }

    public static RootCollection getDefaultRootBasket(Properties cfg)
        throws IOException {
        RootCollection roots;

        // START: RootBasket
        //long defaultMinSpaceFree = Bytes.parseBytes(cfg.getProperty(
       //             "slave.minspacefree", "50mb"));
        ArrayList rootStrings = new ArrayList();

        for (int i = 1; true; i++) {
            String rootString = cfg.getProperty("slave.root." + i);

            if (rootString == null) {
                break;
            }

            logger.info("slave.root." + i + ": " + rootString);

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

        roots = new RootCollection(rootStrings);

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
        if (isWin32) {
        	s.startFileLockThread();
        }
        try {
        s.sendResponse(new AsyncResponseDiskStatus(s.getDiskStatus()));
        } catch (Throwable t) {
        	logger.fatal("Error, check config on master for this slave");
        }
        s.listenForCommands();
    }
    
    public class FileLockRunnable implements Runnable {

		public void run() {
			while (true) {
				synchronized (_transfers) {
					try {
						_transfers.wait(300000);
					} catch (InterruptedException e) {
					}
					synchronized (_renameQueue) {
						for (Iterator iter = _renameQueue.iterator(); iter
								.hasNext();) {
							QueuedOperation qo = (QueuedOperation) iter.next();
							if (qo.getDestination() == null) { // delete
								try {
									delete(qo.getSource());
									// delete successfull
									iter.remove();
								} catch (PermissionDeniedException e) {
									// keep it in the queue
								} catch (FileNotFoundException e) {
									iter.remove();
								} catch (IOException e) {
									throw new RuntimeException("Win32 stinks",
											e);
								}
							} else { // rename
								String fileName = qo.getDestination()
										.substring(
												qo.getDestination()
														.lastIndexOf("/") + 1);
								String destDir = qo.getDestination()
										.substring(
												0,
												qo.getDestination()
														.lastIndexOf("/"));
								try {
									rename(qo.getSource(), destDir, fileName);
									// rename successfull
									iter.remove();
								} catch (PermissionDeniedException e) {
									// keep it in the queue
								} catch (FileNotFoundException e) {
									iter.remove();
								} catch (IOException e) {
									throw new RuntimeException("Win32 stinks",
											e);
								}
							}
						}
					}
				}
			}
		}
	}

	private void startFileLockThread() {
		Thread t = new Thread(new FileLockRunnable());
		t.setName("FileLockThread");
		t.start();
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
		// now deletes files as well as directories, recursive!
    	Collection files = null;
    	try {
    		files = _roots.getMultipleRootsForFile(path);
    	} catch (FileNotFoundException e) {
    		// all is good, it's already gone
    		return;
    	}

		for (Iterator iter = files.iterator(); iter.hasNext();) {
			Root root = (Root) iter.next();
			File file = root.getFile(path);

			if (!file.exists()) {
				iter.remove();
				continue;
				// should never occur
			}
			if (file.isDirectory()) {
				if (!file.deleteRecursive()) {
					throw new PermissionDeniedException("delete failed on "
							+ path);
				}
				logger.info("DELETEDIR: " + path);
			} else if (file.isFile()) {
				File dir = new File(file.getParentFile());
				logger.info("DELETE: " + path);
				file.delete();
				while (dir.list().length == 0) {
					if (dir.getPath().length() <= root.getPath().length()) {
						break;
					}

					java.io.File tmpFile = dir.getParentFile();

					dir.delete();
					logger.info("rmdir: " + dir.getPath());

					if (tmpFile == null) {
						break;
					}
					dir = new File(tmpFile);
				}
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

        MP3File mp3 = null;
        try {
            mp3 = new MP3File(absPath, "r");

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
        } finally {
            if (mp3 != null) mp3.close();
        }

        return null;
    }

    public RootCollection getRoots() {
        return _roots;
    }

    private LightSFVFile getSFVFile(String path) throws IOException {
        return new LightSFVFile(new BufferedReader(
                new FileReader(_roots.getFile(path))));
    }

    private String getDIZFile(String path) throws IOException {
		ZipEntry zipEntry = null;
		ZipInputStream zipInput = null;
		byte[] buf = new byte[20 * 1024];
		int numRd;
		try {

			zipInput = new ZipInputStream(new BufferedInputStream(
					new FileInputStream(_roots.getFile(path))));

			// Access a list of all of the files in the zip archive
			while ((zipEntry = zipInput.getNextEntry()) != null) {
				// Is this entry a DIZ file?
				if (zipEntry.getName().toLowerCase().endsWith(".diz")) {
					// Read 20 KBytes from the DIZ file, hopefully this
					// will be the entire file.
					numRd = zipInput.read(buf, 0, 20 * 1024);

					if (numRd > 0) {
						return new String(buf, 0, numRd);
					} else {
						throw new FileNotFoundException(
								"0 bytes read from .zip file - " + path);
					}

				}
			}
		} catch (Throwable t) {
			logger.error("Error extracting .diz from zipfile",t);
		} finally {
			try {
				if (zipInput != null) {
					zipInput.close();
				}
			} catch (IOException e) {
			}
		}
		throw new FileNotFoundException("No diz entry in - " + path);
	}

// public LinkedRemoteFile getSlaveRoot() throws IOException {
// return Slave.getDefaultRoot(_roots);
// }

    public DiskStatus getDiskStatus() {
            return new DiskStatus(_roots.getTotalDiskSpaceAvailable(),
                _roots.getTotalDiskSpaceCapacity());
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

        if (ac.getName().equals("dizfile")) {
            return handleDIZFile((AsyncCommandArgument) ac);
        }

        if (ac.getName().equals("send")) {
            return handleSend((AsyncCommandArgument) ac);
        }

        if (ac.getName().equals("abort")) {
            handleAbort((AsyncCommandArgument) ac);

            return null;
        }
        
        if (ac.getIndex().equals("shutdown")) {
        	logger.info("The master has requested that I shutdown");
        	System.exit(0);
        }

        if (ac.getIndex().equals("error")) {
        	System.err.println("error - " + ac);
            System.exit(0);
        }

        return new AsyncResponseException(ac.getIndex(),
            new Exception(ac.getName() + " - Operation Not Supported"));
    }

    private void handleAbort(AsyncCommandArgument aca) {
    	String[] args = aca.getArgs().split(",");
        TransferIndex ti = new TransferIndex(Integer.parseInt(args[0]));

        if (!_transfers.containsKey(ti)) {
            return;
        }

        Transfer t = (Transfer) _transfers.get(ti);
        t.abort(args[1]);
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
            new ConnectInfo(port, t.getTransferIndex(),
                t.getTransferStatus()));
    }

    private AsyncResponse handleDelete(AsyncCommandArgument ac) {
        try {
        	try {
        		delete(mapPathToRenameQueue(ac.getArgs()));
            } catch (PermissionDeniedException e) {
            	if (isWin32) {
            		synchronized (_renameQueue) {
            			_renameQueue.add(new QueuedOperation(ac.getArgs(), null));
            		}
            	} else {
            		throw e;
            	}
            }
            sendResponse(new AsyncResponseDiskStatus(getDiskStatus()));
            return new AsyncResponse(ac.getIndex());
        } catch (IOException e) {
            return new AsyncResponseException(ac.getIndex(), e);
        }
    }

    private AsyncResponse handleID3Tag(AsyncCommandArgument ac) {
        try {
            return new AsyncResponseID3Tag(ac.getIndex(),
                getID3v1Tag(mapPathToRenameQueue(ac.getArgs())));
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
            new ConnectInfo(c.getLocalPort(),
                t.getTransferIndex(), t.getTransferStatus()));
    }

    private AsyncResponse handleMaxpath(AsyncCommand ac) {
        return new AsyncResponseMaxPath(ac.getIndex(),
            isWin32 ? 255 : Integer.MAX_VALUE);
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
        String path = mapPathToRenameQueue(st.nextToken());
        String fileName = path.substring(path.lastIndexOf("/") + 1);
        String dirName = path.substring(0, path.lastIndexOf("/"));
        Transfer t = getTransfer(transferIndex);
        sendResponse(new AsyncResponse(ac.getIndex())); // return calling thread
														// on master
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
            handleRemergeRecursive(new FileRemoteFile(_roots));

            return new AsyncResponse(ac.getIndex());
        } catch (Throwable e) {
            logger.error("Exception during merging", e);

            return new AsyncResponseException(ac.getIndex(), e);
        }
    }

    private void handleRemergeRecursive(FileRemoteFile dir) {
        //sendResponse(new AsyncResponseRemerge(file.getPath(),
        // file.getFiles()));
        CaseInsensitiveHashtable mergeFiles = new CaseInsensitiveHashtable();

        Collection files = dir.getFiles();

        for (Iterator iter = files.iterator(); iter.hasNext();) {
            FileRemoteFile file = (FileRemoteFile) iter.next();
            
            // need to send directories and files
            mergeFiles.put(file.getName(), new LightRemoteFile(file));

            //keep only dirs for recursiveness
            if (!file.isDirectory()) {
                iter.remove();
            }
        }

        sendResponse(new AsyncResponseRemerge(dir.getPath(), mergeFiles));

        for (Iterator iter = files.iterator(); iter.hasNext();) {
            FileRemoteFile file = (FileRemoteFile) iter.next();
            handleRemergeRecursive(file);
        }
    }

    private AsyncResponse handleRename(AsyncCommandArgument ac) {
        StringTokenizer st = new StringTokenizer(ac.getArgs(), ",");
        String from = mapPathToRenameQueue(st.nextToken());
        String toDir = st.nextToken();
        String toFile = st.nextToken();

        try {
        	try {
            rename(from, toDir, toFile);
        	} catch (PermissionDeniedException e) {
        		if (isWin32) {
        			String simplePath = null;
        			if (toDir.endsWith("/")) {
        				simplePath = toDir + toFile;
        			} else {
        				simplePath = toDir + "/" + toFile;
        			}
        			synchronized (_renameQueue) {
        				_renameQueue.add(new QueuedOperation(from, simplePath));
        			}
        		} else {
        			throw e;
        		}
        	}

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
        String path = mapPathToRenameQueue(st.nextToken());
        Transfer t = getTransfer(transferIndex);
        sendResponse(new AsyncResponse(ac.getIndex())); // return

        // calling thread on master
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
                getSFVFile(mapPathToRenameQueue(ac.getArgs())));
        } catch (IOException e) {
            return new AsyncResponseException(ac.getIndex(), e);
        }
    }

    private AsyncResponse handleDIZFile(AsyncCommandArgument ac)
    {
        try
        {
            return new AsyncResponseDIZFile(ac.getIndex(),
                getDIZFile(ac.getArgs()));
        }
        catch (IOException e)
        {
            return new AsyncResponseException(ac.getIndex(), e);
        }
    }

    private void listenForCommands() throws IOException {
        while (true) {
            AsyncCommand ac = null;

			try {
				ac = (AsyncCommand) _sin.readObject();

				if (ac == null) {
					continue;
				}
			} catch (ClassNotFoundException e) {
				throw new RuntimeException(e);
			} catch (EOFException e) {
				logger
						.debug("Lost connection to the master, may have been kicked offline");
				return;
			} catch (SocketTimeoutException e) {
				// if no communication for slave.timeout time, send a diskstatus
				// this will uncover whatever underlying communication error
				// exists
				sendResponse(new AsyncResponseDiskStatus(getDiskStatus()));
				continue;
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
                    } catch (Throwable e) {
                        sendResponse(new AsyncResponseException(
                                _command.getIndex(), e));
                    }
                }
            }
            Thread t = new Thread(new AsyncCommandHandler(ac));
            t.setName("AsyncCommandHandler - " + ac.getClass());
            t.start();
        }
    }
    
    public String mapPathToRenameQueue(String path) {
    	if (!isWin32) { // there is no renameQueue
    		return path;
    	}
    	synchronized(_renameQueue) {
    		for (Iterator iter = _renameQueue.iterator(); iter.hasNext();) {
    			QueuedOperation qo = (QueuedOperation) iter.next();
    			if (qo.getDestination() == null) {
    				continue;
    			}
    			if (qo.getDestination().equals(path)) {
    				return qo.getSource();
    			}
    		}
        	return path;
    	}
    }

    public void removeTransfer(Transfer transfer) {
        synchronized (_transfers) {
            if (_transfers.remove(transfer.getTransferIndex()) == null) {
                throw new IllegalStateException();
            }
            _transfers.notifyAll();
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
                throw new PermissionDeniedException("renameTo(" + fromfile + ", " + tofile +
                    ") failed");
            }
        }
    }

    protected synchronized void sendResponse(AsyncResponse response) {
        if (response == null) {
            // handler doesn't return anything or it sends reply on it's own
            // (threaded for example)
            return;
        }

        try {
            _sout.writeObject(response);
            _sout.flush();
            if(!(response instanceof AsyncResponseTransferStatus)) {
            	logger.debug("Slave wrote response - " + response);
            }

            if (response instanceof AsyncResponseException) {
                logger.debug("",
                    ((AsyncResponseException) response).getThrowable());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
	/**
	 * @return The current list of Transfer objects
	 */
	public ArrayList getTransfers() {
		synchronized (_transfers) {
			return new ArrayList(_transfers.values());
		}
	}
}
