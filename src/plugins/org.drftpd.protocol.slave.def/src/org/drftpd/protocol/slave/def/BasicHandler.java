/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 *
 * DrFTPD is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * DrFTPD; if not, write to the Free Software Foundation, Inc., 59 Temple Place,
 * Suite 330, Boston, MA 02111-1307 USA
 */
package org.drftpd.protocol.slave.def;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.log4j.Logger;
import org.drftpd.ActiveConnection;
import org.drftpd.PassiveConnection;
import org.drftpd.exceptions.TransferDeniedException;
import org.drftpd.io.PermissionDeniedException;
import org.drftpd.io.PhysicalFile;
import org.drftpd.master.QueuedOperation;
import org.drftpd.protocol.slave.AbstractHandler;
import org.drftpd.protocol.slave.SlaveProtocolCentral;
import org.drftpd.slave.ConnectInfo;
import org.drftpd.slave.LightRemoteInode;
import org.drftpd.slave.RootCollection;
import org.drftpd.slave.RootPathContents;
import org.drftpd.slave.Slave;
import org.drftpd.slave.Transfer;
import org.drftpd.slave.TransferIndex;
import org.drftpd.slave.TransferStatus;
import org.drftpd.slave.async.AsyncCommandArgument;
import org.drftpd.slave.async.AsyncResponse;
import org.drftpd.slave.async.AsyncResponseChecksum;
import org.drftpd.slave.async.AsyncResponseDiskStatus;
import org.drftpd.slave.async.AsyncResponseException;
import org.drftpd.slave.async.AsyncResponseMaxPath;
import org.drftpd.slave.async.AsyncResponseRemerge;
import org.drftpd.slave.async.AsyncResponseSSLCheck;
import org.drftpd.slave.async.AsyncResponseTransfer;
import org.drftpd.slave.async.AsyncResponseTransferStatus;
import org.tanukisoftware.wrapper.WrapperManager;

/**
 * Basic operations handling. 
 * @author fr0w
 * @author zubov
 * @author mog
 * @version $Id$
 */
public class BasicHandler extends AbstractHandler {
	private static final Logger logger = Logger.getLogger(BasicHandler.class);

	// The following variables are static as they are used to signal between
	// remerging and the pause/resume functions, due to the way the handler
	// map works these are run against separate object instances.
	private static AtomicBoolean remergePaused = new AtomicBoolean();
	private static Object remergeWaitObj = new Object();
			
	public BasicHandler(SlaveProtocolCentral central) {
		super(central);
	}

	/**
	 * Simply delegates the request to the Slave object.
	 * @param path
	 */
	private String mapPathToRenameQueue(String path) {
		return getSlaveObject().mapPathToRenameQueue(path);
	}
	
	// TODO check this.
	public AsyncResponse handleAbort(AsyncCommandArgument aca) {
		String[] args = aca.getArgs().split(",");
		TransferIndex ti = new TransferIndex(Integer.parseInt(args[0]));
		
		HashMap<TransferIndex, Transfer> transfers = getSlaveObject().getTransferMap();

		if (!transfers.containsKey(ti)) {
			return null;
		}

		Transfer t = transfers.get(ti);
		t.abort(args[1]);
		return new AsyncResponse(aca.getIndex());
	}

	public AsyncResponse handleConnect(AsyncCommandArgument ac) {
		String[] data = ac.getArgs().split(",");
		String[] data2 = data[0].split(":");
		boolean encrypted = data[1].equals("true");
		boolean useSSLClientHandshake = data[2].equals("true");
		InetAddress address;

		try {
			address = InetAddress.getByName(data2[0]);
		} catch (UnknownHostException e1) {
			return new AsyncResponseException(ac.getIndex(), e1);
		}

		int port = Integer.parseInt(data2[1]);
		Transfer t = new Transfer(new ActiveConnection(encrypted ? getSlaveObject().getSSLContext() : null,
				new InetSocketAddress(address, port), useSSLClientHandshake),
				getSlaveObject(), new TransferIndex());
		
		getSlaveObject().addTransfer(t);

		return new AsyncResponseTransfer(ac.getIndex(), new ConnectInfo(port, t
				.getTransferIndex(), t.getTransferStatus()));
	}

	public AsyncResponse handleDelete(AsyncCommandArgument ac) {
		try {
			try {
				getSlaveObject().delete(mapPathToRenameQueue(ac.getArgs()));
			} catch (PermissionDeniedException e) {
				if (Slave.isWin32) {
					synchronized (getSlaveObject().getRenameQueue()) {
						getSlaveObject().getRenameQueue()
								.add(new QueuedOperation(ac.getArgs(), null));
					}
				} else {
					throw e;
				}
			}
			sendResponse(new AsyncResponseDiskStatus(getSlaveObject().getDiskStatus()));
			return new AsyncResponse(ac.getIndex());
		} catch (IOException e) {
			return new AsyncResponseException(ac.getIndex(), e);
		}
	}

	public AsyncResponse handleListen(AsyncCommandArgument ac) {
		String[] data = ac.getArgs().split(":");
		boolean encrypted = data[0].equals("true");
		boolean useSSLClientMode = data[1].equals("true");
		PassiveConnection c = null;

		try {
			c = new PassiveConnection(encrypted ? getSlaveObject().getSSLContext() : null,
					getSlaveObject().getPortRange(), useSSLClientMode);
			
		} catch (IOException e) {
			return new AsyncResponseException(ac.getIndex(), e);
		}

		Transfer t = new Transfer(c, getSlaveObject(), new TransferIndex());
		getSlaveObject().addTransfer(t);

		return new AsyncResponseTransfer(ac.getIndex(), new ConnectInfo(c
				.getLocalPort(), t.getTransferIndex(), t.getTransferStatus()));
	}

	public AsyncResponse handleMaxpath(AsyncCommandArgument ac) {
		return new AsyncResponseMaxPath(ac.getIndex(), Slave.isWin32 ? 255 : Integer.MAX_VALUE);
	}

	public AsyncResponse handlePing(AsyncCommandArgument ac) {
		return new AsyncResponse(ac.getIndex());
	}

	public AsyncResponse handleReceive(AsyncCommandArgument ac) {
		String[] args = ac.getArgs().split(",", 5);
		char type = args[0].charAt(0);
		long position = Long.parseLong(args[1]);
		TransferIndex transferIndex = new TransferIndex(Integer.parseInt(args[2]));
		String inetAddress = args[3];
		String path = mapPathToRenameQueue(args[4]);
		String fileName = path.substring(path.lastIndexOf("/") + 1);
		String dirName = path.substring(0, path.lastIndexOf("/"));
		Transfer t = getSlaveObject().getTransfer(transferIndex);
		getSlaveObject().sendResponse(new AsyncResponse(ac.getIndex())); // return calling thread
		// on master
		try {
			return new AsyncResponseTransferStatus(t.receiveFile(dirName, type,
					fileName, position, inetAddress));
		} catch (IOException e) {
			return (new AsyncResponseTransferStatus(new TransferStatus(
					transferIndex, e)));
		} catch(TransferDeniedException e) {
			return (new AsyncResponseTransferStatus(new TransferStatus(
					transferIndex, e)));
		}
	}

	public AsyncResponse handleRemergePause(AsyncCommandArgument ac) {
		remergePaused.set(true);
		return new AsyncResponse(ac.getIndex());
	}

	public AsyncResponse handleRemergeResume(AsyncCommandArgument ac) {
		remergePaused.set(false);
		synchronized(remergeWaitObj) {
			remergeWaitObj.notifyAll();
		}
		return new AsyncResponse(ac.getIndex());
	}

	public AsyncResponse handleRemerge(AsyncCommandArgument ac) {
		try {
			String[] argsArray = ac.getArgsArray();
			long skipAgeCutoff = 0L;
			boolean partialRemerge = Boolean.parseBoolean(argsArray[1]) && !getSlaveObject().ignorePartialRemerge();
			if (partialRemerge) {
				skipAgeCutoff = Long.parseLong(argsArray[2]);
				long masterTime = Long.parseLong(argsArray[3]);
				if (skipAgeCutoff != Long.MIN_VALUE) {
					skipAgeCutoff += System.currentTimeMillis() - masterTime;
				}
				Date cutoffDate = new Date(skipAgeCutoff);
				logger.info("Partial remerge enabled, skipping all files last modified before " + cutoffDate.toString());
			} else {
				logger.info("Partial remerge disabled, performing full remerge");
			}
			if (getSlaveObject().concurrentRootIteration()) {
				handleRemergeRecursiveConcurrent(getSlaveObject().getRoots(), argsArray[0], partialRemerge, skipAgeCutoff);
			} else {
				handleRemergeRecursive2(getSlaveObject().getRoots(), argsArray[0], partialRemerge, skipAgeCutoff);
			}

			return new AsyncResponse(ac.getIndex());
		} catch (Throwable e) {
			logger.error("Exception during merging", e);

			return new AsyncResponseException(ac.getIndex(), e);
		}
	}

	private void handleRemergeRecursive2(RootCollection rootCollection,
			String path, boolean partialRemerge, long skipAgeCutoff) {
		while (remergePaused.get()) {
			synchronized(remergeWaitObj) {
				try {
					remergeWaitObj.wait();
				} catch (InterruptedException e) {
					// Either we have been woken properly in which case we will exit the
					// loop or we have not in which case we will wait again.
				}
			}
		}
		TreeSet<String> inodes = rootCollection.getLocalInodes(path);
		ArrayList<LightRemoteInode> fileList = new ArrayList<LightRemoteInode>();

		boolean inodesModified = false;
		long pathLastModified = rootCollection.getLastModifiedForPath(path);
		// Need to check the last modified of the parent itself to detect where
		// files have been deleted but none changed or added
		if (partialRemerge && pathLastModified > skipAgeCutoff) {
			inodesModified = true;
		}
		for (String inode : inodes) {
			String fullPath = path + "/" + inode;
			PhysicalFile file;
			try {
				file = rootCollection.getFile(fullPath);
			} catch (FileNotFoundException e) {
				// something is screwy, we just found the file, it has to exist
				// race condition i guess, stop deleting files outside drftpd!
				logger.error("Error getting file " + path
						+ " even though we just listed it, check permissions",
						e);
				continue;
			}
			try {
				if (file.isSymbolicLink()) {
					// ignore it, but log an error
					logger.warn("You have a symbolic link " + fullPath
							+ " -- these are ignored by drftpd");
					continue;
				}
			} catch (IOException e) {
				logger
						.warn("You have a symbolic link that couldn't be read at "
								+ fullPath + " -- these are ignored by drftpd");
				continue;
			}
			if (partialRemerge && file.lastModified() > skipAgeCutoff) {
				inodesModified = true;
			}
			if (file.isDirectory()) {
				handleRemergeRecursive2(rootCollection, fullPath, partialRemerge, skipAgeCutoff);
			}
			fileList.add(new LightRemoteInode(file));
		}
		if (!partialRemerge || inodesModified) {
			sendResponse(new AsyncResponseRemerge(path, fileList, pathLastModified));
			logger.debug("Sending " + path + " to the master");
		} else {
			logger.debug("Skipping send of " + path + " as no files changed since last merge");
		}
	}

	private void handleRemergeRecursiveConcurrent(RootCollection rootCollection,
			String path, boolean partialRemerge, long skipAgeCutoff) {
		while (remergePaused.get()) {
			synchronized(remergeWaitObj) {
				try {
					remergeWaitObj.wait();
				} catch (InterruptedException e) {
					// Either we have been woken properly in which case we will exit the
					// loop or we have not in which case we will wait again.
				}
			}
		}

		RootPathContents rootContents = rootCollection.getLocalInodesConcurrent(path);
		ArrayList<LightRemoteInode> fileList = new ArrayList<LightRemoteInode>();

		boolean inodesModified = false;
		long pathLastModified = rootContents.getLastModified();
		// Need to check the last modified of the parent itself to detect where
		// files have been deleted but none changed or added
		if (partialRemerge && pathLastModified > skipAgeCutoff) {
			inodesModified = true;
		}
		for (Map.Entry<String, File> entry : rootContents.getInodes().entrySet()) {
			PhysicalFile file = new PhysicalFile(entry.getValue());
			String fullPath = path + "/" + entry.getKey();
			try {
				if (file.isSymbolicLink()) {
					// ignore it, but log an error
					logger.warn("You have a symbolic link " + fullPath
							+ " -- these are ignored by drftpd");
					continue;
				}
			} catch (IOException e) {
				logger
						.warn("You have a symbolic link that couldn't be read at "
								+ fullPath + " -- these are ignored by drftpd");
				continue;
			}
			if (partialRemerge && file.lastModified() > skipAgeCutoff) {
				inodesModified = true;
			}
			if (file.isDirectory()) {
				handleRemergeRecursiveConcurrent(rootCollection, fullPath, partialRemerge, skipAgeCutoff);
			}
			fileList.add(new LightRemoteInode(file));
		}
		if (!partialRemerge || inodesModified) {
			sendResponse(new AsyncResponseRemerge(path, fileList, pathLastModified));
			logger.debug("Sending " + path + " to the master");
		} else {
			logger.debug("Skipping send of " + path + " as no files changed since last merge");
		}
	}
	
	public AsyncResponse handleRename(AsyncCommandArgument ac) {
		StringTokenizer st = new StringTokenizer(ac.getArgs(), ",");
		String from = mapPathToRenameQueue(st.nextToken());
		String toDir = st.nextToken();
		String toFile = st.nextToken();

		try {
			try {
				getSlaveObject().rename(from, toDir, toFile);
			} catch (PermissionDeniedException e) {
				if (Slave.isWin32) {
					String simplePath = null;
					if (toDir.endsWith("/")) {
						simplePath = toDir + toFile;
					} else {
						simplePath = toDir + "/" + toFile;
					}
					synchronized (getSlaveObject().getRenameQueue()) {
						getSlaveObject().getRenameQueue().add(new QueuedOperation(from, simplePath));
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

	public AsyncResponse handleSend(AsyncCommandArgument ac) {
		String[] args = ac.getArgs().split(",", 5);
		char type = args[0].charAt(0);
		long position = Long.parseLong(args[1]);
		TransferIndex transferIndex = new TransferIndex(Integer.parseInt(args[2]));
		String inetAddress = args[3];
		String path = mapPathToRenameQueue(args[4]);
		Transfer t = getSlaveObject().getTransfer(transferIndex);
		sendResponse(new AsyncResponse(ac.getIndex()));

		// calling thread on master
		try {
			return new AsyncResponseTransferStatus(t.sendFile(path, type,
					position, inetAddress));
		} catch (IOException e) {
			return new AsyncResponseTransferStatus(new TransferStatus(t
					.getTransferIndex(), e));
		} catch (TransferDeniedException e) {
			return new AsyncResponseTransferStatus(new TransferStatus(t
					.getTransferIndex(), e));
		}
	}
	
	public AsyncResponse handleChecksum(AsyncCommandArgument ac) {
		try {
			return new AsyncResponseChecksum(ac.getIndex(), getSlaveObject().checkSum(ac.getArgs()));
		} catch (IOException e) {
			return new AsyncResponseException(ac.getIndex(), e);
		}
	}
	
	public AsyncResponse handleShutdown(AsyncCommandArgument ac) {
		logger.info("The master has requested that I shutdown");
		WrapperManager.stop(0);
		return null;
	}
	
	public AsyncResponse handleError(AsyncCommandArgument ac) {
		System.err.println("error - " + ac);
		System.exit(0);
		return null;
	}

	public AsyncResponse handleSSLCheck(AsyncCommandArgument ac) {
		return new AsyncResponseSSLCheck(ac.getIndex(), getSlaveObject().getSSLContext() != null);
	}
}
