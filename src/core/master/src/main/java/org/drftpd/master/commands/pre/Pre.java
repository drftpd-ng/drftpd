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
package org.drftpd.master.commands.pre;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.drftpd.common.dynamicdata.Key;
import org.drftpd.common.extensibility.CommandHook;
import org.drftpd.common.extensibility.HookType;
import org.drftpd.common.util.Bytes;
import org.drftpd.master.GlobalContext;
import org.drftpd.master.commands.*;
import org.drftpd.master.commands.usermanagement.UserManagement;
import org.drftpd.master.config.ConfigInterface;
import org.drftpd.master.sections.SectionInterface;
import org.drftpd.master.usermanager.NoSuchUserException;
import org.drftpd.master.usermanager.User;
import org.drftpd.master.usermanager.UserFileException;
import org.drftpd.master.vfs.DirectoryHandle;
import org.drftpd.master.vfs.InodeHandle;
import org.drftpd.master.vfs.ObjectNotValidException;
import org.drftpd.master.vfs.VirtualFileSystem;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

/**
 * @author scitz0
 * @version $Id$
 */
public class Pre extends CommandInterface {
    public static final Key<DirectoryHandle> PREDIR = new Key<>(Pre.class, "predir");
    private static final Logger logger = LogManager.getLogger(Pre.class);

    private static void recursiveRemoveOwnership(DirectoryHandle dir, long lastModified) {
        try {
            dir.setUsername(GlobalContext.getConfig().getDefaultPreUser());
            dir.setGroup(GlobalContext.getConfig().getDefaultPreGroup());
            dir.setLastModified(lastModified);
            // Make sure the directories appear to have just been created. This helps the autofreespace plugin as well as makes it look OK
            dir.getInode().setCreationTime(lastModified);
            for (InodeHandle file : dir.getInodeHandlesUnchecked()) {
                // Do not fail all pending files if one has issues
                if (file.isDirectory()) {
                    recursiveRemoveOwnership((DirectoryHandle) file, lastModified);
                } else {
                    try {
                        file.setUsername(GlobalContext.getConfig().getDefaultPreUser());
                        file.setGroup(GlobalContext.getConfig().getDefaultPreGroup());
                        file.setLastModified(lastModified);
                    } catch (FileNotFoundException e) {
                        logger.warn("FileNotFoundException on recursiveRemoveOwnership()", e);
                    }
                }
            }
        } catch (FileNotFoundException e) {
            logger.warn("FileNotFoundException on recursiveRemoveOwnership()", e);
        }
    }

    public void initialize(String method, String pluginName, StandardCommandManager cManager) {
        super.initialize(method, pluginName, cManager);
    }

    public CommandResponse doPRE(CommandRequest request) throws ImproperUsageException {

        if (!request.hasArgument()) {
            throw new ImproperUsageException();
        }

        StringTokenizer st = new StringTokenizer(request.getArgument());
        if (st.countTokens() != 2) {
            return StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
        }
        String releaseName = st.nextToken();
        String sectionName = st.nextToken();

        SectionInterface section = GlobalContext.getGlobalContext().getSectionManager().getSection(sectionName);

        if (section.getName().equals("")) {
            return new CommandResponse(500, "Invalid section, see SITE SECTIONS for a list of available sections");
        }

        User user = request.getSession().getUserNull(request.getUser());

        DirectoryHandle preDir = null;

        String path = VirtualFileSystem.fixPath(releaseName);
        if (!(path.startsWith(VirtualFileSystem.separator))) {
            // Not a full path, let's make it one
            if (request.getCurrentDirectory().isRoot()) {
                path = VirtualFileSystem.separator + path;
            } else {
                path = request.getCurrentDirectory().getPath() + VirtualFileSystem.separator + path;
            }
        }

        try {
            preDir = request.getCurrentDirectory().getDirectory(path, user);
        } catch (FileNotFoundException | ObjectNotValidException e) {  // Handled correctly in doPRE based on PREDIR Object = null
            logger.warn("[doPRE] Failed to find predir for [{}]", releaseName, e);
            return StandardCommandManager.genericResponse("RESPONSE_550_REQUESTED_ACTION_NOT_TAKEN");
        }

        if (preDir == null) {
            logger.error("For some reason we were unable to set the preDir based on input [{}]", releaseName);
            return StandardCommandManager.genericResponse("RESPONSE_550_REQUESTED_ACTION_NOT_TAKEN");
        }

        if (!GlobalContext.getConfig().checkPathPermission("pre", user, preDir)) {
            return StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED");
        }

        DirectoryHandle toInode = new DirectoryHandle(section.getCurrentDirectory().getPath() + VirtualFileSystem.separator + preDir.getName());

        if (toInode.exists()) {
            return new CommandResponse(500, "Directory already exist in target section");
        }

        CommandResponse response = new CommandResponse(250, request.getCommand().toUpperCase() + " command successful.");

        //AWARD CREDITS
        HashMap<User, Long> awards = new HashMap<>();
        preAwardCredits(preDir, awards);

        for (Map.Entry<User, Long> entry : awards.entrySet()) {
            User owner = entry.getKey();
            Long award = entry.getValue();
            owner.updateCredits(award);
            response.addComment("Awarded " + Bytes.formatBytes(award) + " to " + owner.getName());
        }

        recursiveRemoveOwnership(preDir, System.currentTimeMillis());

        int files = getFiles(preDir);
        long bytes = 0;
        try {
            bytes = preDir.getSize();
        } catch (FileNotFoundException e) {
            logger.warn("FileNotFoundException while getting size of predir {}: ", preDir.toString(), e);
        }

        try {
            preDir.renameToUnchecked(toInode);
        } catch (FileNotFoundException e) {
            logger.warn("FileNotFoundException on renameTo()", e);
            return new CommandResponse(500, "FileNotFound - " + e.getMessage());
        } catch (IOException e) {
            logger.warn("IOException on renameTo()", e);
            return new CommandResponse(500, "IOException - " + e.getMessage());
        }

        preDir = toInode;

        GlobalContext.getEventService().publishAsync(new PreEvent(preDir, section, Integer.toString(files), Bytes.formatBytes(bytes)));

        // Update PREDIR Object
        response.setObject(PREDIR, preDir);

        return response;
    }

    private void preAwardCredits(DirectoryHandle preDir, HashMap<User, Long> awards) {
        try {
            for (InodeHandle file : preDir.getInodeHandlesUnchecked()) {
                if (file.isFile()) {
                    User owner;

                    try {
                        owner = GlobalContext.getGlobalContext().getUserManager().getUserByNameUnchecked(file.getUsername());
                    } catch (NoSuchUserException e) {
                        logger.warn("PRE: Cannot award credits to non-existing user", e);
                        continue;
                    } catch (UserFileException | FileNotFoundException e) {
                        logger.warn("", e);
                        continue;
                    }

                    Long total = awards.get(owner);
                    if (total == null) {
                        total = 0L;
                    }
                    total = (total + (long) (file.getSize() * owner.getKeyedMap().getObjectFloat(UserManagement.RATIO)));
                    awards.put(owner, total);
                }

                if (file.isDirectory()) {
                    preAwardCredits((DirectoryHandle) file, awards);
                }
            }
        } catch (FileNotFoundException e) {
            logger.warn("FileNotFoundException on preAwardCredits()", e);
        }
    }

    private int getFiles(DirectoryHandle preDir) {
        int files = 0;
        try {
            files = preDir.getFilesUnchecked().size();
            for (DirectoryHandle dir : preDir.getDirectoriesUnchecked()) {
                files += getFiles(dir);
            }
        } catch (FileNotFoundException e) {
            logger.warn("FileNotFoundException ", e);
        }
        return files;
    }

}
