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
package org.drftpd.commands.list;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;

import org.apache.log4j.Logger;
import org.drftpd.Checksum;
import org.drftpd.GlobalContext;
import org.drftpd.commandmanager.CommandInterface;
import org.drftpd.commandmanager.CommandRequest;
import org.drftpd.commandmanager.CommandResponse;
import org.drftpd.commandmanager.StandardCommandManager;
import org.drftpd.exceptions.NoAvailableSlaveException;
import org.drftpd.master.BaseFtpConnection;
import org.drftpd.master.FtpReply;
import org.drftpd.master.RemoteSlave;
import org.drftpd.usermanager.User;
import org.drftpd.vfs.DirectoryHandle;
import org.drftpd.vfs.FileHandle;
import org.drftpd.vfs.InodeHandleInterface;
import org.drftpd.vfs.LinkHandle;
import org.drftpd.vfs.ObjectNotValidException;


/**
 * @author mog
 * @version $Id$
 */
public class MLST extends CommandInterface {
	private static final Logger logger = Logger.getLogger(MLST.class);

	public static final SimpleDateFormat timeval = new SimpleDateFormat("yyyyMMddHHmmss.SSS");

	private StandardCommandManager _cManager;

	public void initialize(String method, String pluginName, StandardCommandManager cManager) {
		super.initialize(method, pluginName, cManager);
		_cManager = cManager;
		_featReplies = new String[] {
				"MLST type*,x.crc32*,size*,modify*,unix.owner*,unix.group*,x.slaves*,x.xfertime*"
		};
	}

	public CommandResponse doMLST(CommandRequest request) {
		return doMLSTandMLSD(request, true);
	}

	public CommandResponse doMLSD(CommandRequest request) {
		return doMLSTandMLSD(request, false);
	}

	private CommandResponse doMLSTandMLSD(CommandRequest request, boolean isMlst) {
		DirectoryHandle dir = request.getCurrentDirectory();
		User user = request.getSession().getUserNull(request.getUser());
		
		if (request.hasArgument()) {
			try {
				dir = dir.getDirectory(request.getArgument(), user);
			} catch (FileNotFoundException e) {
				return StandardCommandManager.genericResponse("RESPONSE_550_REQUESTED_ACTION_NOT_TAKEN");
			}  catch (ObjectNotValidException e) {
				return new CommandResponse(500, "Target is not a directory, MLST only works on Directories");
			}
		}

		if (!GlobalContext.getConfig().checkPathPermission("privpath", 
				request.getSession().getUserNull(request.getUser()), dir, true)) {
			return StandardCommandManager.genericResponse("RESPONSE_550_REQUESTED_ACTION_NOT_TAKEN");
		}

		BaseFtpConnection conn = (BaseFtpConnection) request.getSession();
		PrintWriter out = conn.getControlWriter();
		PrintWriter os = null; // listing writer.

		if (isMlst) {
			conn.printOutput("250-MLST"+LIST.NEWLINE);
			os = out;
		} else {
			if (!conn.getTransferState().isPasv() && !conn.getTransferState().isPort()) {
				return StandardCommandManager.genericResponse("RESPONSE_503_BAD_SEQUENCE_OF_COMMANDS");
			}

			try {
				os = new PrintWriter(new OutputStreamWriter(conn.getTransferState().getDataSocketForLIST().getOutputStream()));
			} catch (IOException ex) {
				logger.warn(ex);
				return new CommandResponse(425, ex.getMessage());
			}

			conn.printOutput(new FtpReply(StandardCommandManager.genericResponse("RESPONSE_150_OK")));
		}

		ListElementsContainer container = new ListElementsContainer(request.getSession(), request.getUser(),_cManager);
		container = ListUtils.list(dir, container);

		for (InodeHandleInterface inode : container.getElements()) {
			os.write(toMLST(inode) + LIST.NEWLINE);
		}

		if (isMlst) 
			return new CommandResponse(250, "End of MLST");
		else {
			os.close();
			return StandardCommandManager.genericResponse("RESPONSE_226_CLOSING_DATA_CONNECTION");
		}
	}

	public static String toMLST(InodeHandleInterface inode) {
		StringBuffer ret = new StringBuffer();

		try {
			if (inode.isLink()) {
				ret.append("type=OS.unix=slink:" + ((LinkHandle) inode).getTargetString() + ";");
			} else if (inode.isFile()) {
				ret.append("type=file;");
			} else if (inode.isDirectory()) {
				ret.append("type=dir;");
			} else {
				throw new RuntimeException("type");
			}

			FileHandle file = null;
			boolean isFileHandle = false;
			if (inode.isFile() && inode instanceof FileHandle) {
				file = (FileHandle) inode;
				isFileHandle = true;
			}

			try {
				if (isFileHandle && file.getCheckSum() != 0) {
					ret.append("x.crc32=" + Checksum.formatChecksum(file.getCheckSum())+ ";");
				}
			} catch (NoAvailableSlaveException e) {
				logger.debug("Unable to fetch checksum for: "+inode.getPath());
			}

			ret.append("size=" + inode.getSize() + ";");
			ret.append("modify=" + timeval.format(new Date(inode.lastModified())) +";");

			ret.append("unix.owner=" + inode.getUsername() + ";");
			ret.append("unix.group=" + inode.getGroup() + ";");

			if (isFileHandle) {
				Iterator<RemoteSlave> iter = file.getSlaves().iterator();
				ret.append("x.slaves=");

				if (iter.hasNext()) {
					ret.append(iter.next().getName());

					while (iter.hasNext()) {
						ret.append("," + iter.next().getName());
					}
				}

				ret.append(";");
			}

			if (isFileHandle && file.getXfertime() != 0) {
				ret.append("x.xfertime=" + file.getXfertime() + ";");
			}

			ret.append(" " + inode.getName());
		} catch (FileNotFoundException e) {
			logger.error("The file was there and now it's gone, how?", e);
		}
		return ret.toString();
	}
}
