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
package org.drftpd.commands.nuke;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.StringTokenizer;
import java.util.Map.Entry;

import org.apache.log4j.Logger;
import org.drftpd.Bytes;
import org.drftpd.GlobalContext;
import org.drftpd.commandmanager.CommandInterface;
import org.drftpd.commandmanager.CommandRequest;
import org.drftpd.commandmanager.CommandResponse;
import org.drftpd.commandmanager.ImproperUsageException;
import org.drftpd.commandmanager.StandardCommandManager;
import org.drftpd.commands.UserManagement;
import org.drftpd.dynamicdata.Key;
import org.drftpd.event.NukeEvent;
import org.drftpd.exceptions.FileExistsException;
import org.drftpd.exceptions.ObjectNotFoundException;
import org.drftpd.usermanager.NoSuchUserException;
import org.drftpd.usermanager.User;
import org.drftpd.usermanager.UserFileException;
import org.drftpd.vfs.DirectoryHandle;
import org.drftpd.vfs.ObjectNotValidException;

/**
 * nukedamount -> amount after multiplier
 * amount -> amount before multiplier
 *
 * @author mog
 * @version $Id$
 */
public class Nuke extends CommandInterface {
    public static final Key<Integer> NUKED = new Key<Integer>(Nuke.class, "nuked");
    public static final Key<Long> NUKEDBYTES = new Key<Long>(Nuke.class, "nukedBytes");
    public static final Key<Long> LASTNUKED = new Key<Long>(Nuke.class, "lastNuked");
    
    private static final Logger logger = Logger.getLogger(Nuke.class);

    /**
     * USAGE: site nuke <directory> <multiplier> <message>
     * Nuke a directory
     *
     * ex. site nuke shit 2 CRAP
     *
     * This will nuke the directory 'shit' and remove x2 credits with the
     * comment 'CRAP'.
     *
     * NOTE: You can enclose the directory in braces if you have spaces in the name
     * ex. site NUKE {My directory name} 1 because_i_dont_like_it
     *
     * Q)  What does the multiplier in 'site nuke' do?
     * A)  Multiplier is a penalty measure. If it is 0, the user doesn't lose any
     *     credits for the stuff being nuked. If it is 1, user only loses the
     *     amount of credits he gained by uploading the files (which is calculated
     *     by multiplying total size of file by his/her ratio). If multiplier is more
     *     than 1, the user loses the credits he/she gained by uploading, PLUS some
     *     extra credits. The formula is this: size * ratio + size * (multiplier - 1).
     *     This way, multiplier of 2 causes user to lose size * ratio + size * 1,
     *     so the additional penalty in this case is the size of nuked files. If the
     *     multiplier is 3, user loses size * ratio + size * 2, etc.
     * @throws ImproperUsageException
     */
    public CommandResponse doSITE_NUKE(CommandRequest request) throws ImproperUsageException {
        if (!request.hasArgument()) {
        	throw new ImproperUsageException();
        }

        StringTokenizer st = new StringTokenizer(request.getArgument());

        if (!st.hasMoreTokens()) {
            throw new ImproperUsageException();
        }

        int multiplier;
        
        DirectoryHandle currentDir = request.getCurrentDirectory();
        DirectoryHandle nukeDir = null;
        String nukeDirName = "";
        User requestUser = request.getSession().getUserNull(request.getUser());

        try {
            nukeDirName = st.nextToken();
            nukeDir = currentDir.getDirectory(nukeDirName, requestUser);
        } catch (FileNotFoundException e) {
            return StandardCommandManager.genericResponse("RESPONSE_550_REQUESTED_ACTION_NOT_TAKEN");
        } catch (ObjectNotValidException e) {
        	return new CommandResponse(550, nukeDirName + " is not a directory");
		}

        String nukeDirPath = nukeDir.getPath();

        if (!st.hasMoreTokens()) {
            throw new ImproperUsageException();
        }

        try {
            multiplier = Integer.parseInt(st.nextToken());
        } catch (NumberFormatException ex) {
            logger.warn(ex, ex);
            return new CommandResponse(501, "Invalid multiplier: " + ex.getMessage());
        }
        
        // aborting transfers on the nuked dir.
        GlobalContext.getGlobalContext().getSlaveManager().cancelTransfersInDirectory(nukeDir);

        String reason = "";

        if (st.hasMoreTokens()) {
            reason = st.nextToken("").trim();
        }
        
        CommandResponse response = new CommandResponse(200, "Nuke suceeded");

        //get nukees with string as key
        Hashtable<String,Long> nukees = new Hashtable<String,Long>();
        
        try {
			NukeUtils.nukeRemoveCredits(nukeDir, nukees);
		} catch (FileNotFoundException e) {
			// how come this happened? the file was just there!
			logger.error(e,e);
		}
		
        // Converting the String Map to a User Map. 
        HashMap<User,Long> nukees2 = new HashMap<User,Long>(nukees.size());

        for (Entry<String,Long> entry : nukees.entrySet()) {
        	String username = entry.getKey();
            User user;

            try {
                user = GlobalContext.getGlobalContext().getUserManager().getUserByName(username);
            } catch (NoSuchUserException e1) {
                response.addComment("Cannot remove credits from " + username +
                    ": " + e1.getMessage());
                logger.warn("", e1);
                user = null;
            } catch (UserFileException e1) {
                response.addComment("Cannot read user data for " + username +
                    ": " + e1.getMessage());
                logger.warn("", e1);
                response.setMessage("NUKE failed");

                return response;
            }

            // nukees contains credits as value
            if (user == null) {
                Long add = nukees2.get(null);

                if (add == null) {
                    add = 0L;
                }

                nukees2.put(null, add.longValue() + entry.getValue());
            } else {
                nukees2.put(user, entry.getValue());
            }
        }

        long nukeDirSize = 0;
        long nukedAmount = 0;

        //update credits, nukedbytes, timesNuked, lastNuked
        for (Entry<User, Long> entry : nukees2.entrySet()) {
        	User nukee = entry.getKey();

            if (nukee == null) {
                continue;
            }

            long size = entry.getValue();
            
            long debt = NukeUtils.calculateNukedAmount(size,
                    nukee.getKeyedMap().getObjectFloat(UserManagement.RATIO), multiplier);

            nukedAmount += debt;
            nukeDirSize += size;
            nukee.updateCredits(-debt);
            nukee.updateUploadedBytes(-size);
            
            nukee.getKeyedMap().incrementLong(NUKEDBYTES, debt);

            nukee.getKeyedMap().incrementInt(NUKED);
            nukee.getKeyedMap().setObject(Nuke.LASTNUKED, Long.valueOf(System.currentTimeMillis()));

            nukee.commit();

            response.addComment(nukee.getName() + " " +
                Bytes.formatBytes(debt));
        }
        
        
        //rename
        String toDirPath = nukeDir.getParent().getPath();
        String toName = "[NUKED]-" + nukeDir.getName();
        String toFullPath = toDirPath+"/"+toName;

        
        try {
            nukeDir.renameToUnchecked(nukeDir.getNonExistentDirectoryHandle(toFullPath)); // rename.
            nukeDir = currentDir.getDirectory(toFullPath, requestUser);
            nukeDir.createDirectoryUnchecked("REASON-" + reason, request.getUser(),requestUser.getGroup());
        } catch (IOException ex) {
            logger.warn(ex, ex);
            CommandResponse r = new CommandResponse(500, "Nuke failed!");
            r.addComment("Could not rename to \"" + toDirPath + "/" + toName + "\": " + ex.getMessage());
            return r;
        } catch (ObjectNotValidException e) {
        	return new CommandResponse(550, toFullPath + " is not a directory");
		}     
        
        NukeData nd = 
			new NukeData(request.getUser(), nukeDirPath, reason, nukees, multiplier, nukedAmount, nukeDirSize);

        NukeEvent nuke = new NukeEvent(request.getSession().getUserNull(request.getUser()), "NUKE", nd);
        
        // adding to the nukelog.
        NukeBeans.getNukeBeans().add(nd);
        
        GlobalContext.getEventService().publishAsync(nuke);

        return response;
    }

    public CommandResponse doSITE_NUKES(CommandRequest request) {
        CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");

        for (NukeData nd : NukeBeans.getNukeBeans().getAll()) {
            response.addComment(nd.toString());
        }

        return response;
    }

    /**
     * USAGE: site unnuke <directory> <message>
     *         Unnuke a directory.
     *
     *         ex. site unnuke shit NOT CRAP
     *
     *         This will unnuke the directory 'shit' with the comment 'NOT CRAP'.
     *
     *         NOTE: You can enclose the directory in braces if you have spaces in the name
     *         ex. site unnuke {My directory name} justcause
     *
     *         You need to configure glftpd to keep nuked files if you want to unnuke.
     *         See the section about glftpd.conf.
     * @throws ImproperUsageException 
     */
    public CommandResponse doSITE_UNNUKE(CommandRequest request) throws ImproperUsageException {
        if (!request.hasArgument()) {
        	throw new ImproperUsageException();
        }
        
        StringTokenizer st = new StringTokenizer(request.getArgument());

        String toName = st.nextToken();
        String toPath;
        
        DirectoryHandle currentDir = request.getCurrentDirectory();

        {
            StringBuffer toPath2 = new StringBuffer(currentDir.getPath());

            if (toPath2.length() != 1) {
                toPath2.append("/"); // isn't /
            }

            toPath2.append(toName);
            toPath = toPath2.toString();
        }

        String toDir = currentDir.getPath();
        String nukeName = "[NUKED]-" + toName;

        String reason;

        if (st.hasMoreTokens()) {
            reason = st.nextToken("");
        } else {
            reason = "";
        }

        DirectoryHandle nukeDir = null;
        User user = request.getSession().getUserNull(request.getUser());

        try {
            nukeDir = currentDir.getDirectory(nukeName, user);
        } catch (FileNotFoundException e) {
            return new CommandResponse(200,  nukeName + " doesn't exist: " + e.getMessage());
        } catch (ObjectNotValidException e) {
        	return new CommandResponse(550, nukeName + " is not a directory");
		}
        
        GlobalContext.getGlobalContext().getSlaveManager().cancelTransfersInDirectory(nukeDir);

        CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
        
        NukeData nukeData;

        try {
            nukeData = NukeBeans.getNukeBeans().get(toPath);
        } catch (ObjectNotFoundException ex) {
            response.addComment(ex.getMessage());
            return response;
        }

        try {
            nukeDir.renameToUnchecked(nukeDir.getNonExistentDirectoryHandle(toDir+"/"+toName));
            nukeDir = currentDir.getDirectory(toDir+"/"+toName, user); //updating reference.
        } catch (FileExistsException e) {
            response.addComment("Error renaming nuke, target dir already exists");
            return response;
        } catch (FileNotFoundException e) {
        	logger.fatal("How come "+nukeDir.getPath()+" was just here and now it isnt?", e);
        	response.addComment(nukeDir.getPath() + " does not exist, how?");
        	return response;
		} catch (ObjectNotValidException e) {
			return new CommandResponse(550, toDir+"/"+toName + " is not a directory");
		}
        
        for (NukedUser nukeeObj : NukeBeans.getNukeeList(nukeData)) {
            String nukeeName = nukeeObj.getUsername();
            User nukee;

            try {
                nukee = GlobalContext.getGlobalContext().getUserManager().getUserByName(nukeeName);
            } catch (NoSuchUserException e) {
            	response.addComment(nukeeName + ": no such user");
                continue;
            } catch (UserFileException e) {
                response.addComment(nukeeName + ": error reading userfile");
                logger.fatal("error reading userfile", e);
                continue;
            }

            long nukedAmount = NukeUtils.calculateNukedAmount(nukeeObj.getAmount(),
                    nukee.getKeyedMap().getObjectFloat(UserManagement.RATIO),
                    nukeData.getMultiplier());

            nukee.updateCredits(nukedAmount);
            nukee.updateUploadedBytes(nukeeObj.getAmount());

            nukee.getKeyedMap().incrementInt(NUKED, -1);

            nukee.commit();

            response.addComment(nukeeName + ": restored " +
                Bytes.formatBytes(nukedAmount));
        }

        try {
            NukeBeans.getNukeBeans().remove(toPath);
        } catch (ObjectNotFoundException e) {
            response.addComment("Error removing nukelog entry, unnuking anyway.");
        }

        try {
            DirectoryHandle reasonDir = nukeDir.getDirectory("REASON-" +
                    nukeData.getReason(), user);

            if (reasonDir.isDirectory()) {
                reasonDir.deleteUnchecked();
            }
        } catch (FileNotFoundException e) {
            logger.debug("Failed to delete 'REASON-" + nukeData.getReason() + "' dir in UNNUKE", e);
        } catch (ObjectNotValidException e) {
        	logger.error(nukeName + " is not a directory, unable to remove 'REASON' dir");
		}
        
        nukeData.setReason(reason);
        NukeEvent nukeEvent = new NukeEvent(request.getSession().getUserNull(request.getUser()), "UNNUKE", nukeData);
        GlobalContext.getEventService().publishAsync(nukeEvent);

        return response;
    }
}
