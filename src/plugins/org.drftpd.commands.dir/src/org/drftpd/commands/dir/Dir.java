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
package org.drftpd.commands.dir;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.drftpd.Bytes;
import org.drftpd.Checksum;
import org.drftpd.GlobalContext;
import org.drftpd.commandmanager.*;
import org.drftpd.dynamicdata.Key;
import org.drftpd.event.DirectoryFtpEvent;
import org.drftpd.exceptions.FileExistsException;
import org.drftpd.exceptions.NoAvailableSlaveException;
import org.drftpd.io.PermissionDeniedException;
import org.drftpd.master.Session;
import org.drftpd.usermanager.User;
import org.drftpd.vfs.*;

import java.io.FileNotFoundException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.StringTokenizer;


/**
 * @author mog
 * @author djb61
 * @version $Id$
 */
public class Dir extends CommandInterface {
	private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat(
	"yyyyMMddHHmmss.SSS");
	private static final Logger logger = LogManager.getLogger(Dir.class);

	public static final Key<InodeHandle> RENAMEFROM = new Key<>(Dir.class, "renamefrom");
	public static final Key<InodeHandle> RENAMETO = new Key<>(Dir.class, "renameto");
	
	// This Keys are place holders for usefull information that gets removed during
	// deletion operations but are need to process hooks.
	public static final Key<String> USERNAME = new Key<>(Dir.class, "username");
	public static final Key<Long> FILESIZE = new Key<>(Dir.class, "fileSize");
	public static final Key<String> FILENAME = new Key<>(Dir.class, "fileName");
	public static final Key<Boolean> ISFILE = new Key<>(Dir.class, "isFile");
	public static final Key<Long> XFERTIME = new Key<>(Dir.class, "xferTime");

	public static final Key<Boolean> WIPE_RECURSIVE = new Key<>(Dir.class, "wipe_recursive");
	public static final Key<String> WIPE_PATH = new Key<>(Dir.class, "wipe_path");
	
	/**
	 * <code>CDUP &lt;CRLF&gt;</code><br>
	 *
	 * This command is a special case of CWD, and is included to
	 * simplify the implementation of programs for transferring
	 * directory trees between operating systems having different
	 * syntaxes for naming the parent directory.  The reply codes
	 * shall be identical to the reply codes of CWD.
	 */
	public CommandResponse doCDUP(CommandRequest request) {
		// change directory
		if (request.getCurrentDirectory().isRoot()) {
			return new CommandResponse(250,
				"Directory remains " + request.getCurrentDirectory().getPath());
		}
		DirectoryHandle newCurrentDirectory = request.getCurrentDirectory().getParent();
		return new CommandResponse(250,
			"Directory changed to " + newCurrentDirectory.getPath(),
			newCurrentDirectory, request.getUser());
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
	public CommandResponse doCWD(CommandRequest request) {

		if (!request.hasArgument()) {
			return StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
		}

		DirectoryHandle newCurrentDirectory;
		User user = request.getSession().getUserNull(request.getUser());

        try {
            DirectoryHandle currentDirectory = request.getCurrentDirectory();
            if (currentDirectory.exists()) {
                // If the current directory exist, proceed as usual
                newCurrentDirectory = currentDirectory.getDirectory(request.getArgument(), user);
            } else {
                // If directly no longer exists (wipe, nuke), try to change from root
                newCurrentDirectory = new DirectoryHandle("/").getDirectory(request.getArgument(), user);
            }
        } catch (FileNotFoundException ex) {
            return new CommandResponse(550, ex.getMessage());
        } catch (ObjectNotValidException e) {
            return new CommandResponse(550, request.getArgument() + ": is not a directory");
        }

        return new CommandResponse(250,
                "Directory changed to " + newCurrentDirectory.getPath(),
                newCurrentDirectory, request.getUser());
    }

	private void addVictimInformationToResponse(InodeHandle victim,
			CommandResponse response) throws FileNotFoundException {
		response.setObject(FILENAME, victim.getName());
		response.setObject(FILESIZE, victim.getSize());
		response.setObject(USERNAME, victim.getUsername());
		response.setObject(ISFILE, victim.isFile());
		response.setObject(XFERTIME, victim.isFile() ? ((FileHandle)victim).getXfertime() : 0L);
	}

	/**
	 * <code>DELE &lt;SP&gt; &lt;pathname&gt; &lt;CRLF&gt;</code><br>
	 *
	 * This command causes the file specified in the pathname to be
	 * deleted at the server site.
	 */
	public CommandResponse doDELE(CommandRequest request) {

		// argument check
		if (!request.hasArgument()) {
			// out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
			return StandardCommandManager
			.genericResponse("RESPONSE_501_SYNTAX_ERROR");
		}

		// get filenames
		String fileName = request.getArgument();
		CommandResponse response = StandardCommandManager
		.genericResponse("RESPONSE_250_ACTION_OKAY");
		User user = request.getSession().getUserNull(request.getUser());
		InodeHandle victim;
		try {
			victim = request.getCurrentDirectory().getInodeHandle(fileName,
					user);

			addVictimInformationToResponse(victim, response);

			if (victim.isDirectory()) {
				DirectoryHandle victimDir = (DirectoryHandle) victim;
				if (!victimDir.isEmpty(user)) {
					return new CommandResponse(550, victim.getPath()
							+ ": Directory not empty");
				}
			}

			victim.delete(user); // the file is only deleted here.
		} catch (FileNotFoundException e) {
			// this isn't good or bad, there could have easily been a race in
			// another thread to delete this file
			return new CommandResponse(550, e.getMessage());
		} catch (PermissionDeniedException e) {
			// The Permission Denied Exception actually tells why it is not allowed
			// It is too much (potentially unsafe) information for all users
			// If the logging is set to debug we can see the underlying exception which helps troubleshooting
			logger.debug(e);
			return StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED");
		}
		if (victim.isFile() || victim.isLink()) { // link or file
			GlobalContext.getEventService().publishAsync(
					new DirectoryFtpEvent(request.getSession().getUserNull(
							request.getUser()), "DELE", victim.getParent()));
			// strange that we're sending the parent directory of the file being
			// deleted without mentioning the file that was deleted...
		} else { // if (requestedFile.isDirectory()) {
			GlobalContext.getEventService()
			.publishAsync(
					new DirectoryFtpEvent(request.getSession()
							.getUserNull(request.getUser()), "RMD",
							(DirectoryHandle) victim));
		}
		return response;
	}


	/**
	 * <code>MDTM &lt;SP&gt; &lt;pathname&gt; &lt;CRLF&gt;</code><br>
	 *
	 * Returns the date and time of when a file was modified.
	 */
	public CommandResponse doMDTM(CommandRequest request) {

		// argument check
		if (!request.hasArgument()) {
			return StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
		}

		// get filenames
		String fileName = request.getArgument();
		InodeHandle reqFile;
		User user = request.getSession().getUserNull(request.getUser());

		try {
			reqFile = request.getCurrentDirectory().getInodeHandle(fileName, user);
		} catch (FileNotFoundException ex) {
			return StandardCommandManager.genericResponse("RESPONSE_550_REQUESTED_ACTION_NOT_TAKEN");
		}

		try {
			synchronized(DATE_FMT) {
				return new CommandResponse(213,
						DATE_FMT.format(new Date(reqFile.lastModified())));
			}
		} catch (FileNotFoundException e) {
			return new CommandResponse(550, e.getMessage());
		}

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
	public CommandResponse doMKD(CommandRequest request) {
		// argument check
		if (!request.hasArgument()) {
			return StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
		}

		Session session = request.getSession();
		String path = VirtualFileSystem.fixPath(request.getArgument());
		DirectoryHandle fakeDirectory = request.getCurrentDirectory().getNonExistentDirectoryHandle(path);
		String dirName = fakeDirectory.getName();

		if (!ListUtils.isLegalFileName(dirName)) {
			return StandardCommandManager.genericResponse("RESPONSE_553_REQUESTED_ACTION_NOT_TAKEN");
		}

		try {
			try {
				if (!fakeDirectory.getParent().equals(request.getCurrentDirectory()) && 
						InodeHandle.isLink(fakeDirectory.getParent().getPath())) {
					fakeDirectory = new LinkHandle(fakeDirectory.getParent().getPath())
					.getTargetDirectory(session.getUserNull(request.getUser()))
					.getNonExistentDirectoryHandle(dirName);
				}
			} catch (FileNotFoundException e1) {
				return new CommandResponse(550, "Parent directory does not exist");
			} catch (ObjectNotValidException e) {
				return new CommandResponse(550, "Parent directory does not exist");
			}
			DirectoryHandle newDir;
			try {
				newDir = fakeDirectory.getParent().createDirectory(session.getUserNull(request.getUser()), dirName);
			} catch (FileNotFoundException e) {
				return new CommandResponse(550, "Parent directory does not exist");
			} catch (PermissionDeniedException e) {
				// The Permission Denied Exception actually tells why it is not allowed
				// It is too much (potentially unsafe) information for all users
				// If the logging is set to debug we can see the underlying exception which helps troubleshooting
				logger.debug(e);
				return StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED");
			}

			GlobalContext.getEventService().publishAsync(new DirectoryFtpEvent(
					session.getUserNull(request.getUser()), "MKD", newDir));

			return new CommandResponse(257, "\"" + newDir.getPath() +
			"\" created.");

		} catch (FileExistsException ex) {
			return new CommandResponse(550,
					"directory " + dirName + " already exists");
		}
	}

	/**
	 * <code>PWD  &lt;CRLF&gt;</code><br>
	 *
	 * This command causes the name of the current working
	 * directory to be returned in the reply.
	 */
	public CommandResponse doPWD(CommandRequest request) {
		return new CommandResponse(257,
				"\"" + request.getCurrentDirectory().getPath() +
		"\" is current directory");
	}

	/**
	 * <code>RMD  &lt;SP&gt; &lt;pathname&gt; &lt;CRLF&gt;</code><br>
	 *
	 * This command causes the directory specified in the pathname
	 * to be removed as a directory (if the pathname is absolute)
	 * or as a subdirectory of the current working directory (if
	 * the pathname is relative).
	 */
	public CommandResponse doRMD(CommandRequest request) {
		return doDELE(request);
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
	public CommandResponse doRNFR(CommandRequest request) {

		// argument check
		if (!request.hasArgument()) {
			return StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
		}

		User user = request.getSession().getUserNull(request.getUser());
		try {
			request.getSession().setObject(RENAMEFROM, request.getCurrentDirectory().getInodeHandle(request.getArgument(), user));
		} catch (FileNotFoundException e) {
			return StandardCommandManager.genericResponse("RESPONSE_550_REQUESTED_ACTION_NOT_TAKEN");
		}

		return new CommandResponse(350, "File exists, ready for destination name");
	}

	/**
	 * <code>RNTO &lt;SP&gt; &lt;pathname&gt; &lt;CRLF&gt;</code><br>
	 *
	 * This command specifies the new pathname of the file
	 * specified in the immediately preceding "rename from"
	 * command.  Together the two commands cause a file to be
	 * renamed.
	 */
	public CommandResponse doRNTO(CommandRequest request) {

		// argument check
		if (!request.hasArgument()) {
			return StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
		}

		// set state variables
		InodeHandle fromInode = request.getSession().getObject(RENAMEFROM, null);
		if (fromInode == null) {
			return StandardCommandManager.genericResponse("RESPONSE_503_BAD_SEQUENCE_OF_COMMANDS");
		}

		String argument = VirtualFileSystem.fixPath(request.getArgument());
		if (!(argument.startsWith(VirtualFileSystem.separator))) {
			// Not a full path, let's make it one
			if (request.getCurrentDirectory().isRoot()) {
				argument = VirtualFileSystem.separator + argument;
			} else {
				argument = request.getCurrentDirectory().getPath() + VirtualFileSystem.separator + argument;
			}
		}
		DirectoryHandle toDir;
		String newName;
		User user = request.getSession().getUserNull(request.getUser());

		try {
			toDir = request.getCurrentDirectory().getDirectory(argument, user);
			// toDir exists and is a directory, so we're just changing the parent directory and not the name
			// unless toInode and fromInode are the same (i.e. a case change)
			if (fromInode.isDirectory() && fromInode.equals(toDir)) {
				toDir = request.getCurrentDirectory().getDirectory(VirtualFileSystem.stripLast(argument), user);
				newName = VirtualFileSystem.getLast(argument);
			} else {
				// There are two possibilites here, the target is an existing link or a directory
				// Check to see if a link with the target name exists
				newName = fromInode.getName();
				boolean linkRename = false;
				DirectoryHandle argParentDir = null;
				try {
					argParentDir = request.getCurrentDirectory().getDirectory(VirtualFileSystem.stripLast(argument), user);
					String linkName = VirtualFileSystem.getLast(argument);
					// If a link exists and is the same link as the from inode this is valid
					try {
						InodeHandle linkHandle = argParentDir.getLink(linkName, user);
						if (fromInode.isLink() && fromInode.equals(linkHandle)) {
							// Changing case of existing link
							toDir = argParentDir;
							newName = linkName;
							linkRename = true;
						}
					} catch (FileNotFoundException e2) {
						// No link exists so we are moving an inode to a new parent without changing its name
					} catch (ObjectNotValidException e2) {
						// Target is an existing directory
					}
				} catch (FileNotFoundException e1) {
					// Destination doesn't exist, shouldn't be possible as the full argument does exist
					logger.warn("Destination doesn't exist, this shouldn't happen", e1);
					return StandardCommandManager.genericResponse("RESPONSE_550_REQUESTED_ACTION_NOT_TAKEN");
				} catch (ObjectNotValidException e1) {
					// Destination isn't a Directory, shouldn't be possible as the full argument is a dir
					logger.warn("Destination isn't a Directory, this shouldn't happen", e1);
					return StandardCommandManager.genericResponse("RESPONSE_550_REQUESTED_ACTION_NOT_TAKEN");
				}
				if (!linkRename && fromInode.isDirectory() && newName.equalsIgnoreCase(VirtualFileSystem.getLast(argument))) {
					// Source is a directory and a directory with the same name already exists in the target, the move
					// should not be allowed. Rather than returning an error here we will allow the VFS to reject this so that
					// permissions can be checked otherwise this could leak knowledge of the contents of parts of the VFS the
					// user does not have access to.
					toDir = argParentDir;
					newName = VirtualFileSystem.getLast(argument);
				}
			}
		} catch (FileNotFoundException e) {
			// Directory does not exist, that means they may have specified _renameFrom's new name
			// as the last part of the argument
			try {
				toDir = request.getCurrentDirectory().getDirectory(VirtualFileSystem.stripLast(argument), user);
				newName = VirtualFileSystem.getLast(argument);
			} catch (FileNotFoundException e1) {
				// Destination doesn't exist
				logger.debug("Destination doesn't exist", e1);
				return StandardCommandManager.genericResponse("RESPONSE_550_REQUESTED_ACTION_NOT_TAKEN");
			} catch (ObjectNotValidException e1) {
				// Destination isn't a Directory
				logger.debug("Destination isn't a Directory", e1);
				return StandardCommandManager.genericResponse("RESPONSE_550_REQUESTED_ACTION_NOT_TAKEN");
			}
		} catch (ObjectNotValidException e) {
			// Target exists but is not a dir, this is invalid unless the target is a file and the same
			// file as the source (i.e. a case change)
			try {
				toDir = request.getCurrentDirectory().getDirectory(VirtualFileSystem.stripLast(argument), user);
				newName = VirtualFileSystem.getLast(argument);
				if (!toDir.equals(fromInode.getParent()) || !newName.equalsIgnoreCase(fromInode.getName())) {
					return StandardCommandManager.genericResponse("RESPONSE_553_REQUESTED_ACTION_NOT_TAKEN_FILE_EXISTS");
				}
			} catch (FileNotFoundException e1) {
				// Destination doesn't exist, shouldn't be possible as the full argument does exist
				logger.warn("Destination doesn't exist, this shouldn't happen", e1);
				return StandardCommandManager.genericResponse("RESPONSE_550_REQUESTED_ACTION_NOT_TAKEN");
			} catch (ObjectNotValidException e1) {
				// Destination isn't a Directory, shouldn't be possible as the full argument is a dir
				logger.warn("Destination isn't a Directory, this shouldn't happen", e1);
				return StandardCommandManager.genericResponse("RESPONSE_550_REQUESTED_ACTION_NOT_TAKEN");
			}
		}
		InodeHandle toInode;
		if (fromInode.isDirectory()) {
			toInode = new DirectoryHandle(toDir.getPath() + VirtualFileSystem.separator + newName);
		} else if (fromInode.isFile()) {
			toInode = new FileHandle(toDir.getPath() + VirtualFileSystem.separator + newName);
		} else if (fromInode.isLink()) {
			toInode = new LinkHandle(toDir.getPath() + VirtualFileSystem.separator + newName);
		} else {
			return new CommandResponse(500, "Someone has extended the VFS beyond File/Directory/Link");
		}

		try {
			/*logger.debug("before rename toInode-" +toInode);
			logger.debug("before rename toInode.getPath()-" + toInode.getPath());
			logger.debug("before rename toInode.getParent()-" + toInode.getParent());
			logger.debug("before rename toInode.getParent().getPath()-" + toInode.getParent().getPath());*/

			fromInode.renameTo(request.getSession().getUserNull(request.getUser()), toInode);
		} catch (PermissionDeniedException e) {
			// The Permission Denied Exception actually tells why it is not allowed
			// It is too much (potentially unsafe) information for all users
			// If the logging is set to debug we can see the underlying exception which helps troubleshooting
			logger.debug(e);
			return StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED");
		} catch (FileNotFoundException e) {
			logger.info("FileNotFoundException on renameTo()", e);
			return new CommandResponse(500, "FileNotFound - " + e.getMessage());
		} catch (FileExistsException e) {
			return StandardCommandManager.genericResponse("RESPONSE_553_REQUESTED_ACTION_NOT_TAKEN_FILE_EXISTS");
		}

		request.getSession().setObject(RENAMETO, toInode);

		/*logger.debug("after rename toInode-" +toInode);
		logger.debug("after rename toInode.getPath()-" + toInode.getPath());
		logger.debug("after rename toInode.getParent()-" + toInode.getParent());
		logger.debug("after rename toInode.getParent().getPath()-" + toInode.getParent().getPath());*/

		return new CommandResponse(250, request.getCommand().toUpperCase() + " command successful.");
	}

	public CommandResponse doSITE_CHOWN(CommandRequest request) throws ImproperUsageException {
		if (!request.hasArgument()) {
			throw new ImproperUsageException();
		}

		StringTokenizer st = new StringTokenizer(request.getArgument());
		if (st.countTokens() < 2) {
			throw new ImproperUsageException();
		}

		String owner = st.nextToken();
		String group = null;
		boolean recursive = false;

		if (owner.equalsIgnoreCase("-r")) {
			recursive = true;
			owner = st.nextToken();
		}

		int pos = owner.indexOf(':');

		if (pos > 0) {
			// Both user and group specified
			group = owner.substring(pos + 1);
			owner = owner.substring(0, pos);
		} else if (pos == 0) {
			// First char is ':', only change group
			group = owner.substring(1);
			owner = null;
		}

		CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");

		User user = request.getSession().getUserNull(request.getUser());

		if (!st.hasMoreTokens()) {
			throw new ImproperUsageException();
		}

		while (st.hasMoreTokens()) {
			try {
				InodeHandle file = request.getCurrentDirectory().getInodeHandle(st.nextToken(), user);

				if (owner != null) {
					file.setUsername(owner);
				}

				if (group != null) {
					file.setGroup(group);
				}

				if (file.isDirectory() && recursive) {
					recursiveCHOWN((DirectoryHandle)file, owner, group, user, response);
				}
			} catch (FileNotFoundException e) {
				response.addComment(e.getMessage());
			}
		}

		return response;
	}
	private void recursiveCHOWN(DirectoryHandle dir, String owner, String group, User user, CommandResponse response) {
		try {
			for (InodeHandle inode : dir.getInodeHandles(user)) {
				if (owner != null) {
					inode.setUsername(owner);
				}
				if (group != null) {
					inode.setGroup(group);
				}
				if (inode.isDirectory()) {
					recursiveCHOWN((DirectoryHandle)inode, owner, group, user, response);
				}
			}
		} catch (FileNotFoundException e) {
			response.addComment(e.getMessage());
		}
	}

	public CommandResponse doSITE_LINK(CommandRequest request) throws ImproperUsageException {
		if (!request.hasArgument()) {
			throw new ImproperUsageException();
		}

		StringTokenizer st = new StringTokenizer(request.getArgument(),
		" ");

		if (st.countTokens() != 2) {
			return StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
		}

		String targetName = st.nextToken();
		String linkName = st.nextToken();
		User user = request.getSession().getUserNull(request.getUser());

		try {
			request.getCurrentDirectory().getInodeHandleUnchecked(targetName); // checks if the inode exists.
			request.getCurrentDirectory().createLink(user, linkName, targetName); // create the link
		} catch (FileExistsException e) {
			return StandardCommandManager.genericResponse("RESPONSE_553_REQUESTED_ACTION_NOT_TAKEN_FILE_EXISTS");
		} catch (FileNotFoundException e) {
			return StandardCommandManager.genericResponse("RESPONSE_550_REQUESTED_ACTION_NOT_TAKEN");
		} catch (PermissionDeniedException e) {
			// The Permission Denied Exception actually tells why it is not allowed
			// It is too much (potentially unsafe) information for all users
			// If the logging is set to debug we can see the underlying exception which helps troubleshooting
			logger.debug(e);
			return StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED");
		}

		return StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
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
	public CommandResponse doSITE_WIPE(CommandRequest request) {
		if (!request.hasArgument()) {
			return StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
		}

		String arg = request.getArgument();

		boolean recursive;

		CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
		if (arg.startsWith("-r ")) {
			arg = arg.substring(3);
			response.setObject(WIPE_RECURSIVE,true);
			recursive = true;
		} else {
			response.setObject(WIPE_RECURSIVE,false);
			recursive = false;
		}

		response.setObject(WIPE_PATH,VirtualFileSystem.fixPath(arg));
		InodeHandle wipeFile;
		User user = request.getSession().getUserNull(request.getUser());

		try {
			wipeFile = request.getCurrentDirectory().getInodeHandle(arg, user);

			if (wipeFile.isDirectory() && !recursive) {
				if (!((DirectoryHandle) wipeFile).isEmpty(user)) {
					return new CommandResponse(550, "Can't wipe, directory not empty");
				}
			}

			if (wipeFile.isLink()) {
				InodeHandle linkFile = wipeFile;
				wipeFile = ((LinkHandle) wipeFile).getTargetInodeUnchecked();
				linkFile.delete(request.getSession().getUserNull(request.getUser()));
			}			
			
			wipeFile.delete(request.getSession().getUserNull(request.getUser()));
			if (wipeFile.isDirectory()) {
				GlobalContext.getEventService().publishAsync(
						new DirectoryFtpEvent(request.getSession().getUserNull(request.getUser()), "WIPE", (DirectoryHandle)wipeFile));
			}
		} catch (FileNotFoundException e) {
			return StandardCommandManager.genericResponse("RESPONSE_550_REQUESTED_ACTION_NOT_TAKEN");
		} catch (PermissionDeniedException e) {
			// The Permission Denied Exception actually tells why it is not allowed
			// It is too much (potentially unsafe) information for all users
			// If the logging is set to debug we can see the underlying exception which helps troubleshooting
			logger.debug(e);
			return StandardCommandManager.genericResponse("RESPONSE_550_REQUESTED_ACTION_NOT_TAKEN");
		}

		return response;
	}

	/**
	 * <code>SIZE &lt;SP&gt; &lt;pathname&gt; &lt;CRLF&gt;</code><br>
	 * 
	 * Returns the size of the file in bytes.
	 */
	public CommandResponse doSIZE(CommandRequest request) {

		if (!request.hasArgument()) {
			return StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
		}

		InodeHandle file;
		User user = request.getSession().getUserNull(request.getUser());

		try {
			file = request.getCurrentDirectory().getInodeHandle(request.getArgument(), user);
			return new CommandResponse(213, Long.toString(file.getSize()));
		} catch (FileNotFoundException ex) {
			return StandardCommandManager.genericResponse("RESPONSE_550_REQUESTED_ACTION_NOT_TAKEN");
		}
	}

	/**
	 * http://www.southrivertech.com/support/titanftp/webhelp/xcrc.htm
	 *
	 * Originally implemented by CuteFTP Pro and Globalscape FTP Server
	 */
	public CommandResponse doXCRC(CommandRequest request) {

		if (!request.hasArgument()) {
			return StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
		}

		StringTokenizer st = new StringTokenizer(request.getArgument());
		FileHandle myFile;
		User user = request.getSession().getUserNull(request.getUser());

		try {
			myFile = request.getCurrentDirectory().getFile(st.nextToken(), user);
		} catch (FileNotFoundException e) {
			return StandardCommandManager.genericResponse("RESPONSE_550_REQUESTED_ACTION_NOT_TAKEN");
		} catch (ObjectNotValidException e) {
			return StandardCommandManager.genericResponse("RESPONSE_504_COMMAND_NOT_IMPLEMENTED_FOR_PARM");
		}

		try {
			if (st.hasMoreTokens()) {
				if (!st.nextToken().equals("0")
						|| !st.nextToken().equals(
								Long.toString(myFile.getSize()))) {
					return StandardCommandManager.genericResponse("RESPONSE_504_COMMAND_NOT_IMPLEMENTED_FOR_PARM");
				}
			}

			return new CommandResponse(250, "XCRC Successful. "
					+ Checksum.formatChecksum(myFile.getCheckSum()));
		} catch (NoAvailableSlaveException e1) {
			logger.warn("", e1);

			return new CommandResponse(550, "NoAvailableSlaveException: "
					+ e1.getMessage());
		} catch (FileNotFoundException e) {
			return StandardCommandManager.genericResponse("RESPONSE_550_REQUESTED_ACTION_NOT_TAKEN");
		}
	}

	public CommandResponse doSITE_FIXSIZE(CommandRequest request) {
		long difference = 0;
		try {
			difference = request.getCurrentDirectory().validateSizeRecursive();
		} catch (FileNotFoundException e) {
			return new CommandResponse(500, e.getMessage());
		}
		CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
		response.addComment("Difference was " + Bytes.formatBytes(difference));
		return response;
	}

	public CommandResponse doSITE_FIXSLAVECOUNT(CommandRequest request) {
		try {
			request.getCurrentDirectory().recalcSlaveRefCounts();
		} catch (FileNotFoundException e) {
			return new CommandResponse(500, e.getMessage());
		}
		CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
		return response;
	}
}
