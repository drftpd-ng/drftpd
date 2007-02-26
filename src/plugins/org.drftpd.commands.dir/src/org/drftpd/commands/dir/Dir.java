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
import org.drftpd.event.DirectoryFtpEvent;
import org.drftpd.exceptions.FileExistsException;
import org.drftpd.exceptions.NoAvailableSlaveException;
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


/**
 * @author mog
 * @author djb61
 * @version $Id$
 */
public class Dir extends CommandInterface {
    private final static SimpleDateFormat DATE_FMT = new SimpleDateFormat(
            "yyyyMMddHHmmss.SSS");
    private static final Logger logger = Logger.getLogger(Dir.class);
    protected InodeHandle _renameFrom = null;


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
        
        try {
        	newCurrentDirectory = request.getCurrentDirectory().getDirectory(request.getArgument());
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

        // show race stats
        if (conn.getGlobalContext().getZsConfig().raceStatsEnabled()) {
            try {
                SFVFile sfvfile = newCurrentDirectory.lookupSFVFile();
                Collection racers = RankUtils.userSort(sfvfile.getFiles(),
                        "bytes", "high");
                Collection groups = RankUtils.topFileGroup(sfvfile.getFiles());

                String racerline = bundle.getString("cwd.racers.body");
                //logger.debug("racerline = " + racerline);
                String groupline = bundle.getString("cwd.groups.body");

                ReplacerEnvironment env = BaseFtpConnection.getReplacerEnvironment(null,
                        conn.getUserNull());

                //Start building race message
                String racetext = bundle.getString("cwd.racestats.header") + "\n";
                racetext += bundle.getString("cwd.racers.header") + "\n";

                ReplacerFormat raceformat = null;

                //Add racer stats
                int position = 1;

                for (Iterator iter = racers.iterator(); iter.hasNext();) {
                    UploaderPosition stat = (UploaderPosition) iter.next();
                    User raceuser;

                    try {
                        raceuser = conn.getGlobalContext().getUserManager()
                                       .getUserByName(stat.getUsername());
                    } catch (NoSuchUserException e2) {
                        continue;
                    } catch (UserFileException e2) {
                        logger.log(Level.FATAL, "Error reading userfile", e2);

                        continue;
                    }

                    ReplacerEnvironment raceenv = new ReplacerEnvironment();

                    raceenv.add("speed",
                        Bytes.formatBytes(stat.getXferspeed()) + "/s");
                    raceenv.add("user", stat.getUsername());
                    raceenv.add("group", raceuser.getGroup());
                    raceenv.add("files", "" + stat.getFiles());
                    raceenv.add("bytes", Bytes.formatBytes(stat.getBytes()));
                    raceenv.add("position", String.valueOf(position));
                    raceenv.add("percent",
                        Integer.toString(
                            (stat.getFiles() * 100) / sfvfile.size()) + "%");

                    try {
                        racetext += (SimplePrintf.jprintf(racerline,
                            raceenv) + "\n");
                        position++;
                    } catch (FormatterException e) {
                        logger.warn(e);
                    }
                }

                racetext += bundle.getString("cwd.racers.footer") + "\n";
                racetext += bundle.getString("cwd.groups.header") + "\n";

                //add groups stats
                position = 1;

                for (Iterator iter = groups.iterator(); iter.hasNext();) {
                    GroupPosition stat = (GroupPosition) iter.next();

                    ReplacerEnvironment raceenv = new ReplacerEnvironment();

                    raceenv.add("group", stat.getGroupname());
                    raceenv.add("position", String.valueOf(position));
                    raceenv.add("bytes", Bytes.formatBytes(stat.getBytes()));
                    raceenv.add("files", Integer.toString(stat.getFiles()));
                    raceenv.add("percent",
                        Integer.toString(
                            (stat.getFiles() * 100) / sfvfile.size()) + "%");
                    raceenv.add("speed",
                        Bytes.formatBytes(stat.getXferspeed()) + "/s");

                    try {
                        racetext += (SimplePrintf.jprintf(groupline,
                            raceenv) + "\n");
                        position++;
                    } catch (FormatterException e) {
                        logger.warn(e);
                    }
                }

                racetext += bundle.getString("cwd.groups.footer") + "\n";

                env.add("totalfiles", Integer.toString(sfvfile.size()));
                env.add("totalbytes", Bytes.formatBytes(sfvfile.getTotalBytes()));
                env.add("totalspeed",
                    Bytes.formatBytes(sfvfile.getXferspeed()) + "/s");
                env.add("totalpercent",
                    Integer.toString(
                        (sfvfile.getStatus().getPresent() * 100) / sfvfile.size()) +
                    "%");

                racetext += bundle.getString("cwd.totals.body") + "\n";
                racetext += bundle.getString("cwd.racestats.footer") + "\n";

                try {
                    raceformat = ReplacerFormat.createFormat(racetext);
                } catch (FormatterException e1) {
                    logger.warn(e1);
                }

                try {
                    if (raceformat == null) {
                        response.addComment("cwd.uploaders");
                    } else {
                        response.addComment(SimplePrintf.jprintf(raceformat, env));
                    }
                } catch (FormatterException e) {
                    response.addComment("cwd.uploaders");
                    logger.warn("", e);
                }
            } catch (RuntimeException ex) {
                logger.error("", ex);
            } catch (IOException e) {
                //Error fetching SFV, ignore
            } catch (NoAvailableSlaveException e) {
                //Error fetching SFV, ignore
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

        try {

        requestedFile = request.getCurrentDirectory().getInodeHandle(fileName); 

        /* TODO reimplement with permissions pre hooks
         * 
         */
        // check permission
        /*if (requestedFile.getUsername().equals(conn.getUserNull().getName())) {
            if (!conn.getGlobalContext().getConfig().checkPathPermission("deleteown", conn.getUserNull(), requestedFile.getParent())) {
                return Reply.RESPONSE_530_ACCESS_DENIED;
            }
        } else if (!conn.getGlobalContext().getConfig().checkPathPermission("delete", conn.getUserNull(), requestedFile.getParent())) {
            return Reply.RESPONSE_530_ACCESS_DENIED;
        }*/
        
        if (requestedFile.isDirectory()) {
			DirectoryHandle victim = (DirectoryHandle) requestedFile;
			if (victim.getInodeHandles().size() != 0) {
				return new CommandResponse(550, requestedFile.getPath()
						+ ": Directory not empty");
			}
		}


        User uploader;

        try {
			uploader = GlobalContext.getGlobalContext().getUserManager().getUserByName(
                    requestedFile.getUsername());
            uploader.updateCredits((long) -(requestedFile.getSize() * GlobalContext
                    .getGlobalContext().getConfig().getCreditCheckRatio(
                            requestedFile.getParent(), uploader)));
            if (!GlobalContext.getGlobalContext().getConfig().checkPathPermission(
                    "nostatsup", uploader, request.getCurrentDirectory())) {
                uploader.updateUploadedBytes(-requestedFile.getSize());
            }
		} catch (UserFileException e) {
			response.addComment("Error removing credits & stats: "
					+ e.getMessage());
		} catch (NoSuchUserException e) {
			response.addComment("User " + requestedFile.getUsername()
					+ " does not exist, cannot remove credits on deletion");
		}

		GlobalContext.getGlobalContext().dispatchFtpEvent(new DirectoryFtpEvent(
				getUserNull(request.getUser()),	"DELE", requestedFile.getParent()));
			requestedFile.delete();
		} catch (FileNotFoundException e) {
			// good! we're done :)
			return new CommandResponse(550, e.getMessage());
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

        try {
            reqFile = request.getCurrentDirectory().getInodeHandle(fileName);
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
        
        
        
        String path = request.getArgument();
        DirectoryHandle fakeDirectory = request.getCurrentDirectory().getNonExistentDirectoryHandle(path);
        String dirName = fakeDirectory.getName();

        //check for NUKED dir
        /*
         * save Teflon's for a few weeks?
        logger.info(conn.getCurrentDirectory().getName());
        logger.info(request.getArgument());
        logger.info("[NUKED]-" + ret.getPath());
        if (conn.getCurrentDirectory().hasFile("[NUKED]-" + ret.getPath())) {
            return new Reply(550,
                    "Requested action not taken. " + request.getArgument() +
                    " is nuked!");
            
        }
        */
        // *************************************
		// begin nuke log check
/*		String toPath;
		if (request.getArgument().substring(0, 1).equals("/")) {
			toPath = request.getArgument();
		} else {
			StringBuffer toPath2 = new StringBuffer(conn.getCurrentDirectory()
					.getPath());
			if (toPath2.length() != 1)
				toPath2.append("/"); // isn't /
			toPath2.append(request.getArgument());
			toPath = toPath2.toString();
		}
		// Try Nuke, then if that doesn't work, try TDPSiteNuke.
		NukeBeans nukeBeans = NukeBeans.getNukeBeans();
		if (nukeBeans != null && nukeBeans.findPath(toPath)) {
			try {
				String reason = nukeBeans.get(toPath).getReason();
				return new Reply(530,
						"Access denied - Directory already nuked for '"
								+ reason + "'");
			} catch (ObjectNotFoundException e) {
				return new Reply(530,
						"Access denied - Directory already nuked, reason unavailable - "
								+ e.getMessage());
			}
		}*/
		// end nuke log check
		// *************************************

        if (!ListUtils.isLegalFileName(dirName)) {
        	return StandardCommandManager.genericResponse("RESPONSE_553_REQUESTED_ACTION_NOT_TAKEN");
        }

        /* TODO: Reimplement with permissions pre hooks
         * 
         */
        //if (!conn.getGlobalContext().getConfig().checkPathPermission("makedir", conn.getUserNull(), conn.getCurrentDirectory())) {
        //    return Reply.RESPONSE_530_ACCESS_DENIED;
        //}

        try {
        	DirectoryHandle newDir = null;
            try {
				newDir = fakeDirectory.getParent().createDirectory(dirName,getUserNull(request.getUser()).getName(),
						getUserNull(request.getUser()).getGroup());
			} catch (FileNotFoundException e) {
				return new CommandResponse(550, "Parent directory does not exist");
			}
  
            GlobalContext.getGlobalContext().dispatchFtpEvent(new DirectoryFtpEvent(
                    getUserNull(request.getUser()), "MKD", newDir));

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
        try {
            _renameFrom = request.getCurrentDirectory().getInodeHandle(request.getArgument());
            /* TODO: reimplement using pre hooks permissions
             * 
             */
            //check permission
			/*if (_renameFrom.getUsername().equals(conn.getUserNull().getName())) {
			    if (!conn.getGlobalContext().getConfig().checkPathPermission("renameown", conn.getUserNull(), _renameFrom.getParent())) {
			        return Reply.RESPONSE_530_ACCESS_DENIED;
			    }
			} else if (!conn.getGlobalContext().getConfig().checkPathPermission("rename", conn.getUserNull(), _renameFrom.getParent())) {
			    return Reply.RESPONSE_530_ACCESS_DENIED;
			}*/
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
        if (_renameFrom == null) {
        	return StandardCommandManager.genericResponse("RESPONSE_503_BAD_SEQUENCE_OF_COMMANDS");
        }
        
		InodeHandle fromInode = _renameFrom;
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
        try {
			toDir = request.getCurrentDirectory().getDirectory(argument);
	        // toDir exists and is a directory, so we're just changing the parent directory and not the name
			newName = fromInode.getName();
        } catch (FileNotFoundException e) {
        	// Directory does not exist, that means they may have specified _renameFrom's new name
        	// as the last part of the argument
        	try {
				toDir = request.getCurrentDirectory().getDirectory(VirtualFileSystem.stripLast(argument));
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
			/* TODO reimplement using pre hooks permissions
			 * 
			 */
			// check permission
			/*if (_renameFrom.getUsername().equals(conn.getUserNull().getName())) {
				if (!conn.getGlobalContext().getConfig().checkPathPermission(
						"renameown", conn.getUserNull(), toInode.getParent())) {
					return Reply.RESPONSE_530_ACCESS_DENIED;
				}
			} else if (!conn.getGlobalContext().getConfig()
					.checkPathPermission("rename", conn.getUserNull(),
							toInode.getParent())) {
				return Reply.RESPONSE_530_ACCESS_DENIED;
			}*/
/*			logger.debug("before rename toInode-" +toInode);
			logger.debug("before rename toInode.getPath()-" + toInode.getPath());
			logger.debug("before rename toInode.getParent()-" + toInode.getParent());
			logger.debug("before rename toInode.getParent().getPath()-" + toInode.getParent().getPath());*/
			fromInode.renameTo(toInode);
		} catch (FileNotFoundException e) {
			logger.info("FileNotFoundException on renameTo()", e);

			return new CommandResponse(500, "FileNotFound - " + e.getMessage());
		} catch (IOException e) {
			logger.info("IOException on renameTo()", e);

			return new CommandResponse(500, "IOException - " + e.getMessage());
		}
/*		logger.debug("after rename toInode-" +toInode);
		logger.debug("after rename toInode.getPath()-" + toInode.getPath());
		logger.debug("after rename toInode.getParent()-" + toInode.getParent());
		logger.debug("after rename toInode.getParent().getPath()-" + toInode.getParent().getPath());*/

		// out.write(FtpResponse.RESPONSE_250_ACTION_OKAY.toString());
		return new CommandResponse(250, request.getCommand() + " command successful.");
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
        } else if ("SITE CHGRP".equals(request.getOriginalCommand())) {
            group = owner;
            owner = null;
        } else if (!"SITE CHOWN".equals(request.getOriginalCommand())) {
        	return StandardCommandManager.genericResponse("RESPONSE_202_COMMAND_NOT_IMPLEMENTED");
        }

        CommandResponse response = new CommandResponse(200);

        while (st.hasMoreTokens()) {
            try {
                InodeHandle file = request.getCurrentDirectory().getInodeHandle(st.nextToken());

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

        try {
            request.getCurrentDirectory().getInodeHandle(targetName); // checks if the inode exists.
            request.getCurrentDirectory().createLink(linkName,
					targetName, getUserNull(request.getUser()).getName(),
					getUserNull(request.getUser()).getGroup()); // create the link
		} catch (FileExistsException e) {
			return StandardCommandManager.genericResponse("RESPONSE_553_REQUESTED_ACTION_NOT_TAKEN_FILE_EXISTS");
		} catch (FileNotFoundException e) {
			return StandardCommandManager.genericResponse("RESPONSE_550_REQUESTED_ACTION_NOT_TAKEN");
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

        try {
			wipeFile = request.getCurrentDirectory().getInodeHandle(arg);

			if (wipeFile.isDirectory() && !recursive) {
				if (((DirectoryHandle) wipeFile).getInodeHandles().size() != 0) {
					return new CommandResponse(200, "Can't wipe, directory not empty");
				}
			}

			// if (conn.getConfig().checkDirLog(conn.getUserNull(), wipeFile)) {
			GlobalContext.getGlobalContext().dispatchFtpEvent(
					new DirectoryFtpEvent(getUserNull(request.getUser()), "WIPE", wipeFile
							.getParent()));

			// }
			wipeFile.delete();
		} catch (FileNotFoundException e) {
			return StandardCommandManager.genericResponse("RESPONSE_550_REQUESTED_ACTION_NOT_TAKEN");
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

        try {
            file = request.getCurrentDirectory().getInodeHandle(request.getArgument());
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

        try {
            myFile = request.getCurrentDirectory().getFile(st.nextToken());
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
