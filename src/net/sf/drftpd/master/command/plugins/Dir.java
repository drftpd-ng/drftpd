package net.sf.drftpd.master.command.plugins;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.StringTokenizer;

import net.sf.drftpd.Bytes;
import net.sf.drftpd.Checksum;
import net.sf.drftpd.ObjectExistsException;
import net.sf.drftpd.event.DirectoryFtpEvent;
import net.sf.drftpd.event.irc.IRCListener;
import net.sf.drftpd.master.BaseFtpConnection;
import net.sf.drftpd.master.FtpReply;
import net.sf.drftpd.master.FtpRequest;
import net.sf.drftpd.master.UploaderPosition;
import net.sf.drftpd.master.command.CommandHandler;
import net.sf.drftpd.master.command.CommandManager;
import net.sf.drftpd.master.command.CommandManagerFactory;
import net.sf.drftpd.master.command.UnhandledCommandException;
import net.sf.drftpd.master.usermanager.NoSuchUserException;
import net.sf.drftpd.master.usermanager.User;
import net.sf.drftpd.master.usermanager.UserFileException;
import net.sf.drftpd.remotefile.LinkedRemoteFile;
import net.sf.drftpd.remotefile.LinkedRemoteFile.NonExistingFile;
import net.sf.drftpd.util.ListUtils;
import net.sf.drftpd.util.ReplacerUtils;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.tanesha.replacer.FormatterException;
import org.tanesha.replacer.ReplacerEnvironment;
import org.tanesha.replacer.ReplacerFormat;

/**
 * @author mog
 * @version $Id: Dir.java,v 1.16 2004/02/04 00:28:18 zubov Exp $
 */
public class Dir implements CommandHandler, Cloneable {
	private final static SimpleDateFormat DATE_FMT =
		new SimpleDateFormat("yyyyMMddHHmmss.SSS");

	private static final Logger logger = Logger.getLogger(Dir.class);
	protected LinkedRemoteFile _renameFrom = null;

	public Dir() {
		super();
	}

	/**
	 * <code>CDUP &lt;CRLF&gt;</code><br>
	 *
	 * This command is a special case of CWD, and is included to
	 * simplify the implementation of programs for transferring
	 * directory trees between operating systems having different
	 * syntaxes for naming the parent directory.  The reply codes
	 * shall be identical to the reply codes of CWD.      
	 */
	private FtpReply doCDUP(BaseFtpConnection conn) {

		// reset state variables
		conn.resetState();

		// change directory
		try {
			conn.setCurrentDirectory(
				conn.getCurrentDirectory().getParentFile());
		} catch (FileNotFoundException ex) {
		}

		return new FtpReply(
			200,
			"Directory changed to " + conn.getCurrentDirectory().getPath());
	}

	/**
	 * <code>CWD  &lt;SP&gt; &lt;pathname&gt; &lt;CRLF&gt;</code><br>
	 *
	 * This command allows the user to work with a different
	 * directory for file storage or retrieval without
	 * altering his login or accounting information.  Transfer
	 * parameters are similarly unchanged.  The argument is a
	 * pathname specifying a directory.
	 */
	private FtpReply doCWD(BaseFtpConnection conn) {
		FtpRequest request = conn.getRequest();
		// reset state variables
		conn.resetState();

		if (!request.hasArgument()) {
			return FtpReply.RESPONSE_501_SYNTAX_ERROR;
		}

		LinkedRemoteFile newCurrentDirectory;
		try {
			newCurrentDirectory =
				conn.getCurrentDirectory().lookupFile(request.getArgument());
		} catch (FileNotFoundException ex) {
			return new FtpReply(550, ex.getMessage());
		}
		if (!conn
			.getConfig()
			.checkPrivPath(conn.getUserNull(), newCurrentDirectory)) {
			return new FtpReply(550, request.getArgument() + ": Not found");
			// reply identical to FileNotFoundException.getMessage() above
		}

		if (!newCurrentDirectory.isDirectory()) {
			return new FtpReply(
				550,
				request.getArgument() + ": Not a directory");
		}
		conn.setCurrentDirectory(newCurrentDirectory);

		FtpReply response =
			new FtpReply(
				200,
				"Directory changed to " + newCurrentDirectory.getPath());
		conn.getConfig().directoryMessage(
			response,
			conn.getUserNull(),
			newCurrentDirectory);
		try {
			Collection uploaders =
				IRCListener.topFileUploaders(newCurrentDirectory.getFiles());

			ReplacerFormat format = null;
			try {
				format = ReplacerUtils.finalFormat(Dir.class, "cwd.uploaders");
			} catch (FormatterException e1) {
			}
			ReplacerEnvironment env =
				BaseFtpConnection.getReplacerEnvironment(
					null,
					conn.getUserNull());
			for (Iterator iter = uploaders.iterator(); iter.hasNext();) {
				UploaderPosition stat = (UploaderPosition) iter.next();

				User user;
				try {
					user =
						conn.getUserManager().getUserByName(stat.getUsername());
				} catch (NoSuchUserException e2) {
					continue;
				} catch (UserFileException e2) {
					logger.log(Level.FATAL, "Error reading userfile", e2);
					continue;
				}

				env.add("targetuser", stat.getUsername());
				env.add("targetgroup", user.getGroupName());
				env.add("files", "" + stat.getFiles());
				env.add("bytes", Bytes.formatBytes(stat.getBytes()));
				//response.addComment(conn.jprintf(Dir.class.getName(), "cwd.uploaders"));
				try {
					if (format == null) {
						response.addComment("cwd.uploaders");
					} else {
						response.addComment(
							ReplacerUtils.finalJprintf(format, env));
					}
				} catch (FormatterException e) {
					response.addComment("cwd.uploaders");
				}

			}
		} catch (RuntimeException ex) {
			logger.error("", ex);
		}
		return response;
	}

	/**
	 * <code>DELE &lt;SP&gt; &lt;pathname&gt; &lt;CRLF&gt;</code><br>
	 *
	 * This command causes the file specified in the pathname to be
	 * deleted at the server site.
	 */
	private FtpReply doDELE(BaseFtpConnection conn) {
		FtpRequest request = conn.getRequest();
		// reset state variables
		conn.resetState();

		// argument check
		if (!request.hasArgument()) {
			//out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
			return FtpReply.RESPONSE_501_SYNTAX_ERROR;
		}

		// get filenames
		String fileName = request.getArgument();
		LinkedRemoteFile requestedFile;
		try {
			//requestedFile = getVirtualDirectory().lookupFile(fileName);
			requestedFile = conn.getCurrentDirectory().lookupFile(fileName);
		} catch (FileNotFoundException ex) {
			return new FtpReply(550, "File not found: " + ex.getMessage());
		}
		// check permission
		if (requestedFile
			.getUsername()
			.equals(conn.getUserNull().getUsername())) {
			if (!conn
				.getConfig()
				.checkDeleteOwn(conn.getUserNull(), requestedFile)) {
				return FtpReply.RESPONSE_530_ACCESS_DENIED;
			}
		} else if (
			!conn.getConfig().checkDelete(conn.getUserNull(), requestedFile)) {
			return FtpReply.RESPONSE_530_ACCESS_DENIED;
		}

		FtpReply reply = (FtpReply) FtpReply.RESPONSE_250_ACTION_OKAY.clone();

		User uploader;
		try {
			uploader =
				conn.getUserManager().getUserByName(
					requestedFile.getUsername());
			uploader.updateCredits(
				(long) - (requestedFile.length() * uploader.getRatio()));
		} catch (UserFileException e) {
			reply.addComment("Error removing credits: " + e.getMessage());
		} catch (NoSuchUserException e) {
			reply.addComment("Error removing credits: " + e.getMessage());
		}

		conn.getConnectionManager().dispatchFtpEvent(
			new DirectoryFtpEvent(conn.getUserNull(), "DELE", requestedFile));
		requestedFile.delete();
		return reply;
	}

	/**
	 * <code>MDTM &lt;SP&gt; &lt;pathname&gt; &lt;CRLF&gt;</code><br>
	 * 
	 * Returns the date and time of when a file was modified.
	 */
	private FtpReply doMDTM(BaseFtpConnection conn) {
		FtpRequest request = conn.getRequest();
		// argument check
		if (!request.hasArgument()) {
			return FtpReply.RESPONSE_501_SYNTAX_ERROR;
		}

		// reset state variables
		conn.resetState();

		// get filenames
		String fileName = request.getArgument();
		LinkedRemoteFile reqFile;
		try {
			reqFile = conn.getCurrentDirectory().lookupFile(fileName);
		} catch (FileNotFoundException ex) {
			return FtpReply.RESPONSE_550_REQUESTED_ACTION_NOT_TAKEN;
		}
		//fileName = user.getVirtualDirectory().getAbsoluteName(fileName);
		//String physicalName =
		//	user.getVirtualDirectory().getPhysicalName(fileName);
		//File reqFile = new File(physicalName);

		// now print date
		//if (reqFile.exists()) {
		return new FtpReply(
			213,
			DATE_FMT.format(new Date(reqFile.lastModified())));
		//out.print(ftpStatus.getResponse(213, request, user, args));
		//} else {
		//	out.write(ftpStatus.getResponse(550, request, user, null));
		//}
	}

	/**
	 * <code>MKD  &lt;SP&gt; &lt;pathname&gt; &lt;CRLF&gt;</code><br>
	 *
	 * This command causes the directory specified in the pathname
	 * to be created as a directory (if the pathname is absolute)
	 * or as a subdirectory of the current working directory (if
	 * the pathname is relative).
	 * 
	 * 
	 *                MKD
	 *                   257
	 *                   500, 501, 502, 421, 530, 550
	 */
	private FtpReply doMKD(BaseFtpConnection conn) {
		FtpRequest request = conn.getRequest();
		// reset state variables
		conn.resetState();

		// argument check
		if (!request.hasArgument()) {
			return FtpReply.RESPONSE_501_SYNTAX_ERROR;
		}

		// get filenames
		//String dirName = request.getArgument();
		//if (!VirtualDirectory.isLegalFileName(fileName)) {
		//	out.println(
		//		"553 Requested action not taken. File name not allowed.");
		//	return;
		//}

		LinkedRemoteFile.NonExistingFile ret =
			conn.getCurrentDirectory().lookupNonExistingFile(
				conn.getConnectionManager().getConfig().getDirName(
					request.getArgument()));
		LinkedRemoteFile dir = ret.getFile();
		//logger.debug("Parent directory is " + dir);
		for (Iterator iter = dir.getFiles().iterator(); iter.hasNext();) {
			LinkedRemoteFile temp = (LinkedRemoteFile) iter.next();
			logger.debug(temp);
		}
		if (!conn.getConfig().checkMakeDir(conn.getUserNull(), dir)) {
			return FtpReply.RESPONSE_530_ACCESS_DENIED;
		}
		String createdDirName = ret.getPath();
		//logger.debug("creating " + createdDirName);
		if (createdDirName == null) {
			return new FtpReply(
				550,
				"Requested action not taken. "
					+ request.getArgument()
					+ " already exists");
		}

		if (!ListUtils.isLegalFileName(createdDirName)) {
			return FtpReply.RESPONSE_553_REQUESTED_ACTION_NOT_TAKEN;
		}

		try {
			LinkedRemoteFile createdDir =
				dir.createDirectory(
					conn.getUserNull().getUsername(),
					conn.getUserNull().getGroupName(),
					createdDirName);

			//if (conn.getConfig().checkDirLog(conn.getUserNull(), createdDir)) {
			conn.getConnectionManager().dispatchFtpEvent(
				new DirectoryFtpEvent(conn.getUserNull(), "MKD", createdDir));
			//}
			return new FtpReply(
				257,
				"\"" + createdDir.getPath() + "\" created.");
		} catch (ObjectExistsException ex) {
			//logger.debug("object exists", ex);
			return new FtpReply(
				550,
				"directory " + createdDirName + " already exists");
		}

		// check permission
		//		if (!getVirtualDirectory().hasCreatePermission(physicalName, true)) {
		//			out.write(ftpStatus.getResponse(450, request, user, args));
		//			return;
		//		}
	}

	/**
	 * <code>PWD  &lt;CRLF&gt;</code><br>
	 *
	 * This command causes the name of the current working
	 * directory to be returned in the reply.
	 */
	private FtpReply doPWD(BaseFtpConnection conn) {

		// reset state variables
		conn.resetState();
		return new FtpReply(
			257,
			"\""
				+ conn.getCurrentDirectory().getPath()
				+ "\" is current directory");
	}

	/**
	 * <code>RMD  &lt;SP&gt; &lt;pathname&gt; &lt;CRLF&gt;</code><br>
	 *
	 * This command causes the directory specified in the pathname
	 * to be removed as a directory (if the pathname is absolute)
	 * or as a subdirectory of the current working directory (if
	 * the pathname is relative).
	 */
	private FtpReply doRMD(BaseFtpConnection conn) {
		FtpRequest request = conn.getRequest();
		// reset state variables
		conn.resetState();

		// argument check
		if (!request.hasArgument()) {
			return FtpReply.RESPONSE_501_SYNTAX_ERROR;
		}

		// get file names
		String fileName = request.getArgument();
		LinkedRemoteFile requestedFile;
		try {
			requestedFile = conn.getCurrentDirectory().lookupFile(fileName);
		} catch (FileNotFoundException e) {
			return new FtpReply(550, fileName + ": " + e.getMessage());
		}

		if (requestedFile
			.getUsername()
			.equals(conn.getUserNull().getUsername())) {
			if (!conn
				.getConfig()
				.checkDeleteOwn(conn.getUserNull(), requestedFile)) {
				return FtpReply.RESPONSE_530_ACCESS_DENIED;
			}
		} else if (
			!conn.getConfig().checkDelete(conn.getUserNull(), requestedFile)) {
			return FtpReply.RESPONSE_530_ACCESS_DENIED;
		}

		if (!requestedFile.isDirectory()) {
			return new FtpReply(550, fileName + ": Not a directory");
		}
		if (requestedFile.dirSize() != 0) {
			return new FtpReply(550, fileName + ": Directory not empty");
		}

		// now delete
		//if (conn.getConfig().checkDirLog(conn.getUserNull(), requestedFile)) {
		conn.getConnectionManager().dispatchFtpEvent(
			new DirectoryFtpEvent(conn.getUserNull(), "RMD", requestedFile));
		//}
		requestedFile.delete();
		return FtpReply.RESPONSE_250_ACTION_OKAY;
	}

	/**
	 * <code>RNFR &lt;SP&gt; &lt;pathname&gt; &lt;CRLF&gt;</code><br>
	 *
	 * This command specifies the old pathname of the file which is
	 * to be renamed.  This command must be immediately followed by
	 * a "rename to" command specifying the new file pathname.
	 * 
	 *                RNFR
				  450, 550
				  500, 501, 502, 421, 530
				  350
	
	 */
	private FtpReply doRNFR(BaseFtpConnection conn) {
		FtpRequest request = conn.getRequest();

		// reset state variable
		conn.resetState();

		// argument check
		if (!request.hasArgument()) {
			return FtpReply.RESPONSE_501_SYNTAX_ERROR;
		}

		// set state variable

		// get filenames
		//String fileName = request.getArgument();
		//fileName = user.getVirtualDirectory().getAbsoluteName(fileName);
		//mstRenFr = user.getVirtualDirectory().getPhysicalName(fileName);

		try {
			_renameFrom =
				conn.getCurrentDirectory().lookupFile(request.getArgument());
		} catch (FileNotFoundException e) {
			conn.resetState();
			return FtpReply.RESPONSE_550_REQUESTED_ACTION_NOT_TAKEN;
		}

		//check permission
		if (_renameFrom
			.getUsername()
			.equals(conn.getUserNull().getUsername())) {
			if (!conn
				.getConfig()
				.checkRenameOwn(conn.getUserNull(), _renameFrom)) {
				conn.resetState();
				return FtpReply.RESPONSE_530_ACCESS_DENIED;
			}
		} else if (
			!conn.getConfig().checkRename(conn.getUserNull(), _renameFrom)) {
			conn.resetState();
			return FtpReply.RESPONSE_530_ACCESS_DENIED;
		}
		return new FtpReply(350, "File exists, ready for destination name");
	}

	/**
	 * <code>RNTO &lt;SP&gt; &lt;pathname&gt; &lt;CRLF&gt;</code><br>
	 *
	 * This command specifies the new pathname of the file
	 * specified in the immediately preceding "rename from"
	 * command.  Together the two commands cause a file to be
	 * renamed.
	 */
	private FtpReply doRNTO(BaseFtpConnection conn) {
		FtpRequest request = conn.getRequest();

		// argument check
		if (!request.hasArgument()) {
			conn.resetState();
			//out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
			return FtpReply.RESPONSE_501_SYNTAX_ERROR;
		}

		// set state variables
		if (_renameFrom == null) {
			conn.resetState();
			return FtpReply.RESPONSE_503_BAD_SEQUENCE_OF_COMMANDS;
		}

		NonExistingFile ret =
			conn.getCurrentDirectory().lookupNonExistingFile(
				request.getArgument());
		LinkedRemoteFile toDir = ret.getFile();
		String name = ret.getPath();

		LinkedRemoteFile fromFile = _renameFrom;
		conn.resetState();

		if (name == null)
			name = fromFile.getName();
		//String to = toDir.getPath() + "/" + name;

		// check permission
		if (_renameFrom
			.getUsername()
			.equals(conn.getUserNull().getUsername())) {
			if (!conn.getConfig().checkRenameOwn(conn.getUserNull(), toDir)) {
				conn.resetState();
				return FtpReply.RESPONSE_530_ACCESS_DENIED;
			}
		} else if (!conn.getConfig().checkRename(conn.getUserNull(), toDir)) {
			conn.resetState();
			return FtpReply.RESPONSE_530_ACCESS_DENIED;
		}

		try {
			fromFile.renameTo(toDir.getPath(), name);
		} catch (IOException e) {
			logger.warn("", e);
			return FtpReply.RESPONSE_553_REQUESTED_ACTION_NOT_TAKEN;
		}

		//out.write(FtpResponse.RESPONSE_250_ACTION_OKAY.toString());
		return new FtpReply(
			250,
			request.getCommand() + " command successfull.");
	}

	private FtpReply doSITE_CHOWN(BaseFtpConnection conn)
		throws UnhandledCommandException {
		FtpRequest req = conn.getRequest();
		StringTokenizer st =
			new StringTokenizer(conn.getRequest().getArgument());
		String owner = st.nextToken();
		String group = null;
		int pos = owner.indexOf('.');
		if (pos != -1) {
			group = owner.substring(pos + 1);
			owner = owner.substring(0, pos);
		} else if ("SITE CHGRP".equals(req.getCommand())) {
			group = owner;
			owner = null;
		} else if (!"SITE CHOWN".equals(req.getCommand())) {
			throw UnhandledCommandException.create(Dir.class, req);
		}
		FtpReply reply = new FtpReply(200);

		while (st.hasMoreTokens()) {
			try {
				LinkedRemoteFile file =
					conn.getCurrentDirectory().lookupFile(st.nextToken());
				if (owner != null)
					file.setOwner(owner);
				if (group != null)
					file.setGroup(group);
			} catch (FileNotFoundException e) {
				reply.addComment(e.getMessage());
			}
		}
		return FtpReply.RESPONSE_200_COMMAND_OK;
	}

	/**
	 * USAGE: site wipe [-r] <file/directory>
	 *                                                                                 
	 *         This is similar to the UNIX rm command.
	 *         In glftpd, if you just delete a file, the uploader loses credits and
	 *         upload stats for it.  There are many people who didn't like that and
	 *         were unable/too lazy to write a shell script to do it for them, so I
	 *         wrote this command to get them off my back.
	 *                                                                                 
	 *         If the argument is a file, it will simply be deleted. If it's a
	 *         directory, it and the files it contains will be deleted.  If the
	 *         directory contains other directories, the deletion will be aborted.
	 *                                                                                 
	 *         To remove a directory containing subdirectories, you need to use
	 *         "site wipe -r dirname". BE CAREFUL WHO YOU GIVE ACCESS TO THIS COMMAND.
	 *         Glftpd will check if the parent directory of the file/directory you're
	 *         trying to delete is writable by its owner. If not, wipe will not
	 *         execute, so to protect directories from being wiped, make their parent
	 *         555.
	 *                                                                                 
	 *         Also, wipe will only work where you have the right to delete (in
	 *         glftpd.conf). Delete right and parent directory's mode of 755/777/etc
	 *         will cause glftpd to SWITCH TO ROOT UID and wipe the file/directory.
	 *         "site wipe -r /" will not work, but "site wipe -r /incoming" WILL, SO
	 *         BE CAREFUL.
	 *                                                                                 
	 *         This command will remove the deleted files/directories from the dirlog
	 *         and dupefile databases.
	 *                                                                                 
	 *         To give access to this command, add "-wipe -user flag =group" to the
	 *         config file (similar to other site commands).
	 * 
	 * @param request
	 * @param out
	 */
	private FtpReply doSITE_WIPE(BaseFtpConnection conn) {
		conn.resetState();
		if (!conn.getUserNull().isAdmin()) {
			return FtpReply.RESPONSE_530_ACCESS_DENIED;
		}
		if (!conn.getRequest().hasArgument()) {
			return FtpReply.RESPONSE_501_SYNTAX_ERROR;
		}

		String arg = conn.getRequest().getArgument();

		boolean recursive;
		if (arg.startsWith("-r ")) {
			arg = arg.substring(3);
			recursive = true;
		} else {
			recursive = false;
		}

		LinkedRemoteFile wipeFile;
		try {
			wipeFile = conn.getCurrentDirectory().lookupFile(arg);
		} catch (FileNotFoundException e) {
			return new FtpReply(
				200,
				"Can't wipe: "
					+ arg
					+ " does not exist or it's not a plain file/directory");
		}
		if (wipeFile.isDirectory() && wipeFile.dirSize() != 0 && !recursive) {
			return new FtpReply(200, "Can't wipe, directory not empty");
		}
		//if (conn.getConfig().checkDirLog(conn.getUserNull(), wipeFile)) {
		conn.getConnectionManager().dispatchFtpEvent(
			new DirectoryFtpEvent(conn.getUserNull(), "WIPE", wipeFile));
		//}
		wipeFile.delete();
		return FtpReply.RESPONSE_200_COMMAND_OK;
	}

	/**
	 * <code>SIZE &lt;SP&gt; &lt;pathname&gt; &lt;CRLF&gt;</code><br>
	 *
	 * Returns the size of the file in bytes.
	 */
	private FtpReply doSIZE(BaseFtpConnection conn) {
		FtpRequest request = conn.getRequest();
		conn.resetState();
		if (!request.hasArgument()) {
			return FtpReply.RESPONSE_501_SYNTAX_ERROR;
		}
		LinkedRemoteFile file;
		try {
			file = conn.getCurrentDirectory().lookupFile(request.getArgument());
		} catch (FileNotFoundException ex) {
			return FtpReply.RESPONSE_550_REQUESTED_ACTION_NOT_TAKEN;
		}
		return new FtpReply(213, Long.toString(file.length()));
	}

	/**
	 * http://www.southrivertech.com/support/titanftp/webhelp/xcrc.htm
	 * 
	 * Originally implemented by CuteFTP Pro and Globalscape FTP Server
	 */
	private FtpReply doXCRC(BaseFtpConnection conn) {
		FtpRequest request = conn.getRequest();
		if (!request.hasArgument()) {
			return FtpReply.RESPONSE_501_SYNTAX_ERROR;
		}
		StringTokenizer st = new StringTokenizer(request.getArgument());
		LinkedRemoteFile myFile;
		try {
			myFile = conn.getCurrentDirectory().lookupFile(st.nextToken());
		} catch (FileNotFoundException e) {
			return FtpReply.RESPONSE_550_REQUESTED_ACTION_NOT_TAKEN;
		}

		if (st.hasMoreTokens()) {
			if (!st.nextToken().equals("0")
				|| !st.nextToken().equals(Long.toString(myFile.length()))) {
				return FtpReply.RESPONSE_504_COMMAND_NOT_IMPLEMENTED_FOR_PARM;
			}
		}
		try {
			return new FtpReply(
				250,
				"XCRC Successful. "
					+ Checksum.formatChecksum(myFile.getCheckSum()));
		} catch (IOException e1) {
			logger.warn("", e1);
			return new FtpReply(550, "IO error: " + e1.getMessage());
		}

	}

	public FtpReply execute(BaseFtpConnection conn)
		throws UnhandledCommandException {
		FtpRequest request = conn.getRequest();
		String cmd = request.getCommand();
		if ("CDUP".equals(cmd))
			return doCDUP(conn);
		if ("CWD".equals(cmd))
			return doCWD(conn);
		if ("MKD".equals(cmd))
			return doMKD(conn);
		if ("PWD".equals(cmd))
			return doPWD(conn);
		if ("RMD".equals(cmd))
			return doRMD(conn);
		if ("RNFR".equals(cmd))
			return doRNFR(conn);
		if ("RNTO".equals(cmd))
			return doRNTO(conn);
		if ("SITE WIPE".equals(cmd))
			return doSITE_WIPE(conn);
		if ("XCRC".equals(cmd))
			return doXCRC(conn);
		if ("MDTM".equals(cmd))
			return doMDTM(conn);
		if ("SIZE".equals(cmd))
			return doSIZE(conn);
		if ("DELE".equals(cmd))
			return doDELE(conn);
		if ("SITE CHOWN".equals(cmd) || "SITE CHGRP".equals(cmd))
			return doSITE_CHOWN(conn);
		throw UnhandledCommandException.create(Dir.class, request);

	}

	public String[] getFeatReplies() {
		return null;
	}

	public CommandHandler initialize(
		BaseFtpConnection conn,
		CommandManager initializer) {
		try {
			return (Dir) clone();
		} catch (CloneNotSupportedException e) {
			throw new RuntimeException(e);
		}
	}
	public void load(CommandManagerFactory initializer) {
	}
	public void unload() {
	}
}
