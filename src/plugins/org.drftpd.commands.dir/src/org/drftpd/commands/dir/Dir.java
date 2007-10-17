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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.StringTokenizer;

import org.apache.log4j.Logger;
import org.drftpd.Checksum;
import org.drftpd.GlobalContext;
import org.drftpd.commandmanager.CommandInterface;
import org.drftpd.commandmanager.CommandRequest;
import org.drftpd.commandmanager.CommandResponse;
import org.drftpd.commandmanager.StandardCommandManager;
import org.drftpd.dynamicdata.Key;
import org.drftpd.event.DirectoryFtpEvent;
import org.drftpd.exceptions.FileExistsException;
import org.drftpd.exceptions.NoAvailableSlaveException;
import org.drftpd.master.Session;
import org.drftpd.usermanager.NoSuchUserException;
import org.drftpd.usermanager.User;
import org.drftpd.usermanager.UserFileException;
import org.drftpd.vfs.DirectoryHandle;
import org.drftpd.vfs.FileHandle;
import org.drftpd.vfs.InodeHandle;
import org.drftpd.vfs.LinkHandle;
import org.drftpd.vfs.ListUtils;
import org.drftpd.vfs.ObjectNotValidException;
import org.drftpd.vfs.VirtualFileSystem;

import se.mog.io.PermissionDeniedException;


/**
 * @author mog
 * @author djb61
 * @version $Id$
 */
public class Dir extends CommandInterface {
    private final static SimpleDateFormat DATE_FMT = new SimpleDateFormat(
            "yyyyMMddHHmmss.SSS");
    private static final Logger logger = Logger.getLogger(Dir.class);

    private static final Key RENAMEFROM = new Key(Dir.class, "renamefrom", InodeHandle.class);

    public static final Key DELEFILE = new Key(Dir.class, "delefile", FileHandle.class);


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
    	DirectoryHandle newCurrentDirectory = request.getCurrentDirectory().getParent();
    	return new CommandResponse(200,
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

        DirectoryHandle newCurrentDirectory = null;
        User user = request.getSession().getUserNull(request.getUser());
        
        try {
        	newCurrentDirectory = request.getCurrentDirectory().getDirectory(request.getArgument(), user);
        } catch (FileNotFoundException ex) {
        	return new CommandResponse(550, ex.getMessage());
        } catch (ObjectNotValidException e) {
        	return new CommandResponse(550, request.getArgument() + ": is not a directory");
		}

        /*	TODO this should be reimplemented as part of the port
         * of permissions to pre hooks.
         */
        /*if (!conn.getGlobalContext().getConfig().checkPathPermission("privpath", conn.getUserNull(), newCurrentDirectory, true)) {
            return new Reply(550, request.getArgument() + ": Not found");

            // reply identical to FileNotFoundException.getMessage() above
        }*/

        CommandResponse response = new CommandResponse(250,
                "Directory changed to " + newCurrentDirectory.getPath(),
                newCurrentDirectory, request.getUser());

        /* TODO Since we aren't using conn for anything else at the moment
         * it would be preferable to not restrict this to FTP frontend only
         * , browsing a directory structure may well be a useful feature
         * for another frontend
         */
        //GlobalContext.getGlobalContext().getConfig().directoryMessage(response,
        //  request.getUser() == null ? null : GlobalContext.getGlobalContext().getUserManager().getUserByNameUnchecked(request.getUser()),
        //		  newCurrentDirectory);

        // diz,mp3,racestats will all be hooked externally in the new commandhandlers
        // show cwd_mp3.txt if this is an mp3 release
/*        ResourceBundle bundle = ResourceBundle.getBundle(Dir.class.getName());
        if (conn.getGlobalContext().getZsConfig().id3Enabled()) {
            try {
                ID3Tag id3tag = newCurrentDirectory.lookupFile(newCurrentDirectory.lookupMP3File())
                                                   .getID3v1Tag();
                String mp3text = bundle.getString("cwd.id3info.text");
                ReplacerEnvironment env = BaseFtpConnection.getReplacerEnvironment(null,
                        conn.getUserNull());
                ReplacerFormat id3format = null;

                try {
                    id3format = ReplacerFormat.createFormat(mp3text);
                } catch (FormatterException e1) {
                    logger.warn(e1);
                }

                env.add("artist", id3tag.getArtist().trim());
                env.add("album", id3tag.getAlbum().trim());
                env.add("genre", id3tag.getGenre());
                env.add("year", id3tag.getYear());

                try {
                    if (id3format == null) {
                        response.addComment("broken 1");
                    } else {
                        response.addComment(SimplePrintf.jprintf(id3format, env));
                    }
                } catch (FormatterException e) {
                    response.addComment("broken 2");
                    logger.warn("", e);
                }
            } catch (FileNotFoundException e) {
                // no mp3 found
                //logger.warn("",e);
            } catch (IOException e) {
                logger.warn("", e);
            } catch (NoAvailableSlaveException e) {
                logger.warn("", e);
            }
        }
        // diz files
		if (conn.getGlobalContext().getZsConfig().dizEnabled()) {
			if (DIZPlugin.zipFilesOnline(newCurrentDirectory) > 0) {
				try {
					DIZFile diz = new DIZFile(DIZPlugin
							.getZipFile(newCurrentDirectory));

					ReplacerFormat format = null;
					ReplacerEnvironment env = BaseFtpConnection
							.getReplacerEnvironment(null, conn.getUserNull());

					if (diz.getDiz() != null) {
						try {
							format = ReplacerFormat.createFormat(diz.getDiz());
							response.addComment(SimplePrintf.jprintf(format,
									env));
						} catch (FormatterException e) {
							logger.warn(e);
						}
					}
				} catch (FileNotFoundException e) {
					// do nothing, continue on
				} catch (NoAvailableSlaveException e) {
					// do nothing, continue on
				}
			}
		}

*/
        return response;
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
    		//out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
    		return StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
    	}

    	// get filenames
    	String fileName = request.getArgument();
    	InodeHandle requestedFile;
    	CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_250_ACTION_OKAY");
    	User user = request.getSession().getUserNull(request.getUser());

    	try {

    		requestedFile = request.getCurrentDirectory().getInodeHandle(fileName, user); 

    		// Store the file being deleted in the response keyedmap
    		if (requestedFile.isFile()) {
    			response.setObject(DELEFILE, requestedFile);
    		}

    		if (requestedFile.isDirectory()) {
    			DirectoryHandle victim = (DirectoryHandle) requestedFile;
    			if (victim.isEmpty(user)) {
    				return new CommandResponse(550, requestedFile.getPath()
    						+ ": Directory not empty");
    			}
    		}
			
    		requestedFile.delete(user);

    		User uploader;

    		try {
    			uploader = GlobalContext.getGlobalContext().getUserManager().getUserByName(
    					requestedFile.getUsername());
    			uploader.updateCredits((long) -(requestedFile.getSize() * GlobalContext.getConfig().getCreditCheckRatio(
    							requestedFile.getParent(), uploader)));
    			if (!GlobalContext.getConfig().checkPathPermission(
    					"nostatsup", uploader, request.getCurrentDirectory())) {
    				uploader.updateUploadedBytes(-requestedFile.getSize());
    			}
    		} catch (UserFileException e) {
    			response.addComment("Error removing credits & stats: "+ e.getMessage());
    		} catch (NoSuchUserException e) {
    			response.addComment("User " + requestedFile.getUsername()
    					+ " does not exist, cannot remove credits on deletion");
    		}

    		if (requestedFile.isFile()) {
    			GlobalContext.getEventService().publish(new DirectoryFtpEvent(
    					request.getSession().getUserNull(request.getUser()), "DELE", requestedFile.getParent()));
    		} else if (requestedFile.isDirectory()) {
    			GlobalContext.getEventService().publish(new DirectoryFtpEvent(
    					request.getSession().getUserNull(request.getUser()), "RMD", (DirectoryHandle)requestedFile));
    		}
    	} catch (FileNotFoundException e) {
    		// good! we're done :)
    		return new CommandResponse(550, e.getMessage());
    	} catch (PermissionDeniedException e) {
    		return StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED");
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

        //fileName = user.getVirtualDirectory().getAbsoluteName(fileName);
        //String physicalName =
        //	user.getVirtualDirectory().getPhysicalName(fileName);
        //File reqFile = new File(physicalName);
        // now print date
        //if (reqFile.exists()) {
        try {
        	return new CommandResponse(213,
    			    DATE_FMT.format(new Date(reqFile.lastModified())));
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
        String path = request.getArgument();
        DirectoryHandle fakeDirectory = request.getCurrentDirectory().getNonExistentDirectoryHandle(path);
        String dirName = fakeDirectory.getName();

        if (!ListUtils.isLegalFileName(dirName)) {
        	return StandardCommandManager.genericResponse("RESPONSE_553_REQUESTED_ACTION_NOT_TAKEN");
        }

        try {
        	DirectoryHandle newDir = null;
            try {
				newDir = fakeDirectory.getParent().createDirectory(session.getUserNull(request.getUser()), dirName);
			} catch (FileNotFoundException e) {
				return new CommandResponse(550, "Parent directory does not exist");
			} catch (PermissionDeniedException e) {
				return StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED");
			}
  
			GlobalContext.getEventService().publish(new DirectoryFtpEvent(
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
    	// strange, the ftp rfc says it is exactly equal to DELE, we allow DELE to delete files
    	// that might be wrong, but saves me from writing this method...
/*        FtpRequest request = conn.getRequest();

        // argument check
        if (!request.hasArgument()) {
            return Reply.RESPONSE_501_SYNTAX_ERROR;
        }

        // get file names
        String fileName = request.getArgument();
        LinkedRemoteFile requestedFile;

        try {
            requestedFile = conn.getCurrentDirectory().lookupFile(fileName);
        } catch (FileNotFoundException e) {
            return new Reply(550, fileName + ": " + e.getMessage());
        }

        if (requestedFile.getUsername().equals(conn.getUserNull().getName())) {
            if (!conn.getGlobalContext().getConfig().checkPathPermission("deleteown", conn.getUserNull(), requestedFile)) {
                return Reply.RESPONSE_530_ACCESS_DENIED;
            }
        } else if (!conn.getGlobalContext().getConfig().checkPathPermission("delete", conn.getUserNull(), requestedFile)) {
            return Reply.RESPONSE_530_ACCESS_DENIED;
        }

        if (!requestedFile.isDirectory()) {
            return new Reply(550, fileName + ": Not a directory");
        }

        if (requestedFile.dirSize() != 0) {
            return new Reply(550, fileName + ": Directory not empty");
        }

        // now delete
        //if (conn.getConfig().checkDirLog(conn.getUserNull(), requestedFile)) {
        conn.getGlobalContext().dispatchFtpEvent(new DirectoryFtpEvent(
                conn.getUserNull(), "RMD", requestedFile));

        //}
        requestedFile.delete();

        return Reply.RESPONSE_250_ACTION_OKAY;
*/    }

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

        // set state variable
        // get filenames
        //String fileName = request.getArgument();
        //fileName = user.getVirtualDirectory().getAbsoluteName(fileName);
        //mstRenFr = user.getVirtualDirectory().getPhysicalName(fileName);
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
        InodeHandle fromInode = (InodeHandle) request.getSession().getObject(RENAMEFROM, null);
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
        DirectoryHandle toDir = null;
        String newName = null;
        User user = request.getSession().getUserNull(request.getUser());
        
        try {
			toDir = request.getCurrentDirectory().getDirectory(argument, user);
	        // toDir exists and is a directory, so we're just changing the parent directory and not the name
			newName = fromInode.getName();
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
        	return StandardCommandManager.genericResponse("RESPONSE_553_REQUESTED_ACTION_NOT_TAKEN_FILE_EXISTS");
		}
        InodeHandle toInode = null;
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
			return StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED");
		} catch (FileNotFoundException e) {
			logger.info("FileNotFoundException on renameTo()", e);

			return new CommandResponse(500, "FileNotFound - " + e.getMessage());
		} catch (IOException e) {
			logger.info("IOException on renameTo()", e);

			return new CommandResponse(500, "IOException - " + e.getMessage());
		}
		
		/*logger.debug("after rename toInode-" +toInode);
		logger.debug("after rename toInode.getPath()-" + toInode.getPath());
		logger.debug("after rename toInode.getParent()-" + toInode.getParent());
		logger.debug("after rename toInode.getParent().getPath()-" + toInode.getParent().getPath());*/
		
		return new CommandResponse(250, request.getCommand().toUpperCase() + " command successful.");
    }

    public CommandResponse doSITE_CHOWN(CommandRequest request) {

        StringTokenizer st = new StringTokenizer(request.getArgument());
        String owner = st.nextToken();
        String group = null;
        int pos = owner.indexOf('.');

        /* TODO this chgrp isn't actually accessible by site chgrp
         * so we should either remove this (and in doing so remove the need
         * for this to use getOriginalCommand) or map it to some other command
         */
        if (pos != -1) {
            group = owner.substring(pos + 1);
            owner = owner.substring(0, pos);
        } else if ("SITE CHGRP".equals(request.getCommand())) {
            group = owner;
            owner = null;
        } else if (!"SITE CHOWN".equals(request.getCommand())) {
        	return StandardCommandManager.genericResponse("RESPONSE_202_COMMAND_NOT_IMPLEMENTED");
        }

        CommandResponse response = new CommandResponse(200);
        User user = request.getSession().getUserNull(request.getUser());
        
        while (st.hasMoreTokens()) {
            try {
                InodeHandle file = request.getCurrentDirectory().getInodeHandle(st.nextToken(), user);

                if (owner != null) {
                    file.setUsername(owner);
                }

                if (group != null) {
                    file.setGroup(group);
                }
            } catch (FileNotFoundException e) {
                response.addComment(e.getMessage());
            }
        }

        response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
        return response;
    }

    public CommandResponse doSITE_LINK(CommandRequest request) {
        if (!request.hasArgument()) {
        	return StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
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

        if (arg.startsWith("-r ")) {
            arg = arg.substring(3);
            recursive = true;
        } else {
            recursive = false;
        }

        InodeHandle wipeFile;
        User user = request.getSession().getUserNull(request.getUser());

        try {
			wipeFile = request.getCurrentDirectory().getInodeHandle(arg, user);

			if (wipeFile.isDirectory() && !recursive) {
				if (((DirectoryHandle) wipeFile).isEmpty(user)) {
					return new CommandResponse(550, "Can't wipe, directory not empty");
				}
			}

			// if (conn.getConfig().checkDirLog(conn.getUserNull(), wipeFile)) {
			GlobalContext.getEventService().publish(
					new DirectoryFtpEvent(request.getSession().getUserNull(request.getUser()), "WIPE", wipeFile
							.getParent()));

			// }
			wipeFile.delete(request.getSession().getUserNull(request.getUser()));
		} catch (FileNotFoundException e) {
			return StandardCommandManager.genericResponse("RESPONSE_550_REQUESTED_ACTION_NOT_TAKEN");
		} catch (PermissionDeniedException e) {
			return StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED");
		}

		return StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
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

//    public String getHelp(String cmd) {
//        ResourceBundle bundle = ResourceBundle.getBundle(Dir.class.getName());
//        if ("".equals(cmd))
//            return bundle.getString("help.general")+"\n";
//        else if("link".equals(cmd) || "link".equals(cmd) || "wipe".equals(cmd))
//            return bundle.getString("help."+cmd)+"\n";
//        else
//            return "";
//    }
}
