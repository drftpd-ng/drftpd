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
package org.drftpd.master.commands.nuke;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.drftpd.common.util.Bytes;
import org.drftpd.master.GlobalContext;
import org.drftpd.master.commands.*;
import org.drftpd.master.commands.nuke.metadata.NukeData;
import org.drftpd.master.commands.usermanagement.UserManagement;
import org.drftpd.master.network.BaseFtpConnection;
import org.drftpd.master.network.Session;
import org.drftpd.master.sections.SectionInterface;
import org.drftpd.master.usermanager.NoSuchUserException;
import org.drftpd.master.usermanager.User;
import org.drftpd.master.usermanager.UserFileException;
import org.drftpd.master.vfs.DirectoryHandle;
import org.drftpd.master.vfs.ObjectNotValidException;
import org.drftpd.master.vfs.VirtualFileSystem;
import org.drftpd.slave.exceptions.ObjectNotFoundException;

import java.io.FileNotFoundException;
import java.util.*;

/**
 * nukedamount -> amount after multiplier
 * amount -> amount before multiplier
 *
 * @author mog
 * @version $Id$
 */
public class Nuke extends CommandInterface {

    private static final Logger logger = LogManager.getLogger(Nuke.class);
    private ResourceBundle _bundle;

    public void initialize(String method, String pluginName, StandardCommandManager cManager) {
        super.initialize(method, pluginName, cManager);
        _bundle = cManager.getResourceBundle();

    }

    /**
     * USAGE: site nuke <directory> <multiplier> <message>
     * Nuke a directory
     * <p>
     * ex. site nuke shit 2 CRAP
     * <p>
     * This will nuke the directory 'shit' and remove x2 credits with the
     * comment 'CRAP'.
     * <p>
     * NOTE: You can enclose the directory in braces if you have spaces in the name
     * ex. site NUKE {My directory name} 1 because_i_dont_like_it
     * <p>
     * Q)  What does the multiplier in 'site nuke' do?
     * A)  Multiplier is a penalty measure. If it is 0, the user doesn't lose any
     * credits for the stuff being nuked. If it is 1, user only loses the
     * amount of credits he gained by uploading the files (which is calculated
     * by multiplying total size of file by his/her ratio). If multiplier is more
     * than 1, the user loses the credits he/she gained by uploading, PLUS some
     * extra credits. The formula is this: size * ratio + size * (multiplier - 1).
     * This way, multiplier of 2 causes user to lose size * ratio + size * 1,
     * so the additional penalty in this case is the size of nuked files. If the
     * multiplier is 3, user loses size * ratio + size * 2, etc.
     *
     * @throws ImproperUsageException
     */
    public CommandResponse doNUKE(CommandRequest request) throws ImproperUsageException {
        if (!request.hasArgument()) {
            throw new ImproperUsageException();
        }

        StringTokenizer st = new StringTokenizer(request.getArgument());

        if (!st.hasMoreTokens()) {
            throw new ImproperUsageException();
        }

        Session session = request.getSession();

        int multiplier;

        DirectoryHandle currentDir = request.getCurrentDirectory();
        String nukeDirName = st.nextToken();

        User requestUser = session.getUserNull(request.getUser());

        String nukeDirPath = VirtualFileSystem.fixPath(nukeDirName);

        if (!(nukeDirPath.startsWith(VirtualFileSystem.separator))) {
            // Not a full path, let's make it one
            if (request.getCurrentDirectory().isRoot()) {
                boolean searchIndex = request.getProperties().getProperty("search", "true").
                        equalsIgnoreCase("true");
                if (searchIndex) {
                    // Get dirs from index system
                    ArrayList<DirectoryHandle> dirsToNuke;
                    try {
                        dirsToNuke = NukeUtils.findNukeDirs(currentDir, requestUser, nukeDirPath, "doNUKE");
                    } catch (FileNotFoundException e) {
                        logger.warn(e);
                        return new CommandResponse(550, e.getMessage());
                    }

                    Map<String, Object> env = new HashMap<>();

                    if (dirsToNuke.isEmpty()) {
                        env.put("searchstr", nukeDirPath);
                        return new CommandResponse(550, session.jprintf(_bundle, "nuke.search.empty",
                                env, requestUser));
                    } else if (dirsToNuke.size() == 1) {
                        nukeDirPath = dirsToNuke.get(0).getPath();
                    } else {
                        CommandResponse response = new CommandResponse(200);

                        for (DirectoryHandle nukeDir : dirsToNuke) {
                            try {
                                env.put("name", nukeDir.getName());
                                env.put("path", nukeDir.getPath());
                                env.put("owner", nukeDir.getUsername());
                                env.put("group", nukeDir.getGroup());
                                env.put("size", Bytes.formatBytes(nukeDir.getSize()));
                                response.addComment(session.jprintf(_bundle, "nuke.search.item", env, requestUser));
                            } catch (FileNotFoundException e) {
                                logger.warn("Dir deleted after index search?, skip and continue: {}", nukeDir.getPath());
                            }
                        }

                        response.addComment(session.jprintf(_bundle, "nuke.search.end", env, requestUser));

                        // Return matching dirs and let user decide what to nuke
                        return response;
                    }
                } else {
                    nukeDirPath = VirtualFileSystem.separator + nukeDirPath;
                }
            } else {
                nukeDirPath = request.getCurrentDirectory().getPath() + VirtualFileSystem.separator + nukeDirPath;
            }
        }

        DirectoryHandle nukeDir;

        try {
            nukeDir = request.getCurrentDirectory().getDirectory(nukeDirPath, requestUser);
        } catch (FileNotFoundException e) {
            return StandardCommandManager.genericResponse("RESPONSE_550_REQUESTED_ACTION_NOT_TAKEN");
        } catch (ObjectNotValidException e) {
            return new CommandResponse(550, nukeDirPath + " is not a directory");
        }

        nukeDirName = nukeDir.getName();

        if (!st.hasMoreTokens()) {
            throw new ImproperUsageException();
        }

        try {
            multiplier = Integer.parseInt(st.nextToken());
        } catch (NumberFormatException ex) {
            logger.warn(ex, ex);
            return new CommandResponse(501, "Invalid multiplier: " + ex.getMessage());
        }

        String reason = "";

        if (st.hasMoreTokens()) {
            reason = st.nextToken("").trim();
        }

        NukeData nd;
        try {
            nd = NukeUtils.nuke(nukeDir, multiplier, reason, requestUser);
        } catch (NukeException e) {
            return new CommandResponse(500, "Nuke failed: " + e.getMessage());
        }

        CommandResponse response = new CommandResponse(200, "Nuke succeeded");

        GlobalContext.getEventService().publishAsync(new NukeEvent(requestUser, "NUKE", nd));

        Map<String, Object> env = new HashMap<>();

        SectionInterface section = GlobalContext.getGlobalContext().getSectionManager().lookup(nukeDir);
        env.put("section", section.getName());
        env.put("sectioncolor", section.getColor());
        env.put("dir", nukeDirName);
        env.put("path", nukeDirPath);
        env.put("relpath", nukeDirPath.replaceAll("/.*?" + section + "/", ""));
        env.put("multiplier", multiplier);
        env.put("nukedamount", Bytes.formatBytes(nd.getAmount()));
        env.put("reason", reason);
        env.put("size", Bytes.formatBytes(nd.getSize()));

        if (session instanceof BaseFtpConnection) {
            response.addComment(session.jprintf(_bundle, "nuke", env, requestUser));
            for (NukedUser nukeeObj : NukeBeans.getNukeeList(nd)) {
                Map<String, Object> nukeeenv = new HashMap<>();
                User nukee;
                try {
                    nukee = GlobalContext.getGlobalContext().getUserManager().getUserByName(nukeeObj.getUsername());
                } catch (NoSuchUserException e1) {
                    // Unable to get user, does not exist.. skip announce for this user
                    continue;
                } catch (UserFileException e1) {
                    // Error in user file.. skip announce for this user
                    continue;
                }

                long debt = NukeUtils.calculateNukedAmount(nukeeObj.getAmount(),
                        nukee.getKeyedMap().getObjectFloat(UserManagement.RATIO), multiplier);
                nukeeenv.put("nukedamount", Bytes.formatBytes(debt));
                response.addComment(session.jprintf(_bundle, "nuke.nukees", nukeeenv, nukee));
            }
        }

        return response;
    }

    public CommandResponse doNUKES(CommandRequest request) {
        CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");

        Map<String, Object> env = new HashMap<>();

        SectionInterface section = GlobalContext.getGlobalContext().getSectionManager().getSection(request.getArgument());

        if (request.hasArgument() && section.getName().equalsIgnoreCase("")) {
            return new CommandResponse(501, "Invalid section!");
        }

        if (NukeBeans.getNukeBeans().getAll().isEmpty()) {
            response.addComment(request.getSession().jprintf(_bundle, "nukes.empty", env, request.getUser()));
        }

        for (NukeData nd : NukeBeans.getNukeBeans().getAll()) {
            if (nd.getPath().startsWith(request.getArgument(), 1)) {
                env.put("path", nd.getPath());
                env.put("multiplier", nd.getMultiplier());
                env.put("usersnuked", nd.getNukees().size());
                env.put("size", nd.getSize());
                env.put("reason", nd.getReason());
                env.put("amount", nd.getAmount());
                env.put("nuker", nd.getUser());
                response.addComment(request.getSession().jprintf(_bundle, "nukes", env, request.getUser()));
            }
        }

        if (response.getComment().isEmpty()) {
            env.put("section", section.getName());
            env.put("sectioncolor", section.getColor());
            response.addComment(request.getSession().jprintf(_bundle, "nukes.empty.section", env, request.getUser()));
        }

        return response;
    }

    /**
     * USAGE: site unnuke <directory> <message>
     * Unnuke a directory.
     * <p>
     * ex. site unnuke shit NOT CRAP
     * <p>
     * This will unnuke the directory 'shit' with the comment 'NOT CRAP'.
     * <p>
     * NOTE: You can enclose the directory in braces if you have spaces in the name
     * ex. site unnuke {My directory name} justcause
     * <p>
     * You need to configure glftpd to keep nuked files if you want to unnuke.
     * See the section about glftpd.conf.
     *
     * @throws ImproperUsageException
     */
    public CommandResponse doUNNUKE(CommandRequest request) throws ImproperUsageException {
        if (!request.hasArgument()) {
            throw new ImproperUsageException();
        }

        StringTokenizer st = new StringTokenizer(request.getArgument());

        Session session = request.getSession();

        DirectoryHandle currentDir = request.getCurrentDirectory();
        User user = session.getUserNull(request.getUser());
        String path = VirtualFileSystem.fixPath(st.nextToken());
        String toName;
        String toDir;
        String nukeName;

        if (!path.startsWith(VirtualFileSystem.separator)) {
            // Not a full path, let's make it one and append nuke prefix if needed.
            if (path.startsWith(NukeUtils._nukePrefix)) {
                nukeName = path;
                toName = path.substring(NukeUtils._nukePrefix.length());
            } else {
                toName = path;
                nukeName = NukeUtils._nukePrefix + path;
            }
            if (request.getCurrentDirectory().isRoot()) {
                boolean searchIndex = request.getProperties().getProperty("search", "true").
                        equalsIgnoreCase("true");
                if (searchIndex) {
                    // Get dirs from index system
                    ArrayList<DirectoryHandle> dirsToUnNuke;
                    try {
                        dirsToUnNuke = NukeUtils.findNukeDirs(currentDir, user, nukeName, "doUNNUKE");
                    } catch (FileNotFoundException e) {
                        logger.warn(e);
                        return new CommandResponse(550, e.getMessage());
                    }

                    Map<String, Object> env = new HashMap<>();

                    if (dirsToUnNuke.isEmpty()) {
                        env.put("searchstr", nukeName);
                        return new CommandResponse(550, session.jprintf(_bundle, "unnuke.search.empty", env, user));
                    } else if (dirsToUnNuke.size() == 1) {
                        toDir = dirsToUnNuke.get(0).getParent().getPath() + VirtualFileSystem.separator;
                    } else {
                        CommandResponse response = new CommandResponse(200);

                        for (DirectoryHandle nukeDir : dirsToUnNuke) {
                            try {
                                env.put("name", nukeDir.getName());
                                env.put("path", nukeDir.getPath());
                                env.put("owner", nukeDir.getUsername());
                                env.put("group", nukeDir.getGroup());
                                env.put("size", Bytes.formatBytes(nukeDir.getSize()));
                                response.addComment(session.jprintf(_bundle, "unnuke.search.item", env, user));
                            } catch (FileNotFoundException e) {
                                logger.warn("Dir deleted after index search?, skip and continue: {}", nukeDir.getPath());
                            }
                        }

                        response.addComment(session.jprintf(_bundle, "unnuke.search.end", env, user));

                        // Return matching dirs and let user decide what to unnuke
                        return response;
                    }
                } else {
                    toDir = VirtualFileSystem.separator;
                }
            } else {
                toDir = currentDir.getPath() + VirtualFileSystem.separator;
            }
        } else {
            // Full path to Nuked dir provided, append nuke prefix if needed.
            toDir = VirtualFileSystem.stripLast(path) + VirtualFileSystem.separator;
            toName = VirtualFileSystem.getLast(path);
            if (toName.startsWith(NukeUtils._nukePrefix)) {
                nukeName = toName;
                toName = toName.substring(NukeUtils._nukePrefix.length());
            } else {
                nukeName = NukeUtils._nukePrefix + toName;
            }
        }

        String reason;

        if (st.hasMoreTokens()) {
            reason = st.nextToken("").trim();
        } else {
            reason = "";
        }

        DirectoryHandle nukeDir;

        try {
            nukeDir = currentDir.getDirectory(toDir + nukeName, user);
        } catch (FileNotFoundException e) {
            // Maybe dir was deleted/wiped, lets remove it from nukelog.
            try {
                NukeBeans.getNukeBeans().remove(toDir + toName);
            } catch (ObjectNotFoundException ex) {
                return new CommandResponse(500, toDir + nukeName + " doesnt exist and no nukelog for this path was found.");
            }
            return new CommandResponse(200, toDir + nukeName + " doesnt exist, removed nuke from nukelog.");
        } catch (ObjectNotValidException e) {
            return new CommandResponse(550, toDir + nukeName + " is not a directory");
        }

        NukeData nd;
        try {
            nd = NukeUtils.unnuke(nukeDir, reason);
        } catch (NukeException e) {
            return new CommandResponse(500, "Unnuke failed: " + e.getMessage());
        }

        CommandResponse response = new CommandResponse(200, "Unnuke succeeded");

        NukeEvent nukeEvent = new NukeEvent(session.getUserNull(request.getUser()), "UNNUKE", nd);
        GlobalContext.getEventService().publishAsync(nukeEvent);

        Map<String, Object> env = new HashMap<>();

        SectionInterface section = GlobalContext.getGlobalContext().getSectionManager().lookup(nukeDir);
        env.put("section", section.getName());
        env.put("sectioncolor", section.getColor());
        env.put("dir", nukeDir.getName());
        env.put("path", nukeDir.getPath());
        env.put("relpath", nukeDir.getPath().replaceAll("/" + section + "/", ""));
        env.put("multiplier", nd.getMultiplier());
        env.put("nukedamount", Bytes.formatBytes(nd.getAmount()));
        env.put("reason", reason);
        env.put("size", Bytes.formatBytes(nd.getSize()));

        if (session instanceof BaseFtpConnection) {
            response.addComment(session.jprintf(_bundle, "unnuke", env, user));
            for (NukedUser nukeeObj : NukeBeans.getNukeeList(nd)) {
                Map<String, Object> nukeeenv = new HashMap<>();
                User nukee;
                try {
                    nukee = GlobalContext.getGlobalContext().getUserManager().getUserByName(nukeeObj.getUsername());
                } catch (NoSuchUserException e1) {
                    // Unable to get user, does not exist.. skip announce for this user
                    continue;
                } catch (UserFileException e1) {
                    // Error in user file.. skip announce for this user
                    continue;
                }

                long debt = NukeUtils.calculateNukedAmount(nukeeObj.getAmount(),
                        nukee.getKeyedMap().getObjectFloat(UserManagement.RATIO), nd.getMultiplier());
                nukeeenv.put("nukedamount", Bytes.formatBytes(debt));
                response.addComment(session.jprintf(_bundle, "unnuke.nukees", nukeeenv, nukee));
            }
        }

        return response;
    }

    public CommandResponse doNUKESCLEAN(CommandRequest request) {
        CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");

        if (NukeBeans.getNukeBeans().getAll().isEmpty()) {
            response.addComment("Nukelog empty.");
            return response;
        }

        int deleted = 0;
        for (Iterator<Map.Entry<String, NukeData>> it =
             NukeBeans.getNukeBeans().getNukes().entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, NukeData> nukeEntry = it.next();
            // Construct new path with nuke prefix
            String newPath = NukeUtils.getPathWithNukePrefix(VirtualFileSystem.fixPath(nukeEntry.getKey()));
            try {
                request.getCurrentDirectory().getDirectoryUnchecked(newPath);
            } catch (FileNotFoundException e) {
                // Dir was deleted/wiped, lets remove it from nukelog.
                it.remove();
                deleted++;
            } catch (ObjectNotValidException e) {
                return new CommandResponse(550, newPath + " is not a directory");
            }
        }

        if (deleted == 0) {
            response.addComment("No entries to delete from nukelog.");
        } else {
            response.addComment("Removed " + deleted + " invalid entries from the nukelog.");
        }

        return response;
    }
}
