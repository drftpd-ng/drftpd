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
package org.drftpd.archive.master.commands;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.drftpd.archive.master.Archive;
import org.drftpd.archive.master.DuplicateArchiveException;
import org.drftpd.archive.master.archivetypes.ArchiveHandler;
import org.drftpd.archive.master.archivetypes.ArchiveType;
import org.drftpd.common.extensibility.PluginInterface;
import org.drftpd.master.GlobalContext;
import org.drftpd.master.commands.*;
import org.drftpd.master.network.Session;
import org.drftpd.master.sections.SectionInterface;
import org.drftpd.master.usermanager.User;
import org.drftpd.master.vfs.DirectoryHandle;
import org.drftpd.master.vfs.ObjectNotValidException;
import org.drftpd.slave.exceptions.ObjectNotFoundException;

import java.io.FileNotFoundException;
import java.util.*;

/*
 * @author zubov
 * @version $Id$
 */
public class ArchiveCommandHandler extends CommandInterface {

    private static final Logger logger = LogManager.getLogger(ArchiveCommandHandler.class.getName());

    private ResourceBundle _bundle;

    public ArchiveCommandHandler() {
        super();
    }

    public void initialize(String method, String pluginName, StandardCommandManager cManager) {
        super.initialize(method, pluginName, cManager);
        _bundle = cManager.getResourceBundle();
    }

    private Archive getArchive() throws ObjectNotFoundException {
        Archive archive;
        List<PluginInterface> pluginList = GlobalContext.getGlobalContext().getPlugins();
        for (PluginInterface pi : pluginList) {
            if (pi instanceof Archive) {
                archive = (Archive) pi;
                return archive;
            }
        }
        throw new ObjectNotFoundException("Archive is not loaded");
    }

    public CommandResponse doARCHIVE(CommandRequest request) throws ImproperUsageException {

        // Fail fast if we did not receive any arguments
        if (!request.hasArgument()) {
            throw new ImproperUsageException();
        }

        CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
        Map<String, Object> env = new HashMap<>();

        StringTokenizer args = new StringTokenizer(request.getArgument());

        // We need at least 2 argument (dirname)
        if (args.countTokens() < 2) {
            throw new ImproperUsageException();
        }

        // Get the current session
        Session session = request.getSession();

        // Get the user requesting the archive
        User currentUser = session.getUserNull(request.getUser());

        String dirname = args.nextToken();
        String archiveTypeName = args.nextToken();
        env.put("dirname", dirname);
        env.put("archivetypename", archiveTypeName);

        // Extra configuration properties
        Properties props = new Properties();

        while (args.hasMoreTokens()) {
            addConfig(props, args.nextToken());
        }

        // Get our archive Manager instance
        Archive archiveManager;

        try {
            archiveManager = getArchive();
        } catch (ObjectNotFoundException e) {
            response.addComment(request.getSession().jprintf(_bundle, env, "loadarchive"));

            return response;
        }

        // Get the requested Directory Handle
        DirectoryHandle dirHandle;

        try {
            dirHandle = request.getCurrentDirectory().getDirectory(dirname, currentUser);
        } catch (FileNotFoundException e) {
            response.addComment(request.getSession().jprintf(_bundle, env, "baddir"));

            return response;
        } catch (ObjectNotValidException e) {
            response.addComment("Archive only works on Directories");

            return response;
        }

        if (dirHandle == null) {
            response.addComment("Something went wrong as our directory handle is still empty");

            return response;
        }

        // Get the section for our directory handle
        SectionInterface section = GlobalContext.getGlobalContext().getSectionManager().lookup(dirHandle);

        // Get the requested archive Type
        ArchiveType archiveType = archiveManager.getArchiveType(0, archiveTypeName, section, props);

        if (archiveType == null) {
            logger.error("User requested ArchiveType {}, which does not exist", archiveTypeName);
            response.addComment(request.getSession().jprintf(_bundle, env, "incompatible"));
            return response;
        }

        try {
            // Ensure we are not already archiving this request
            archiveManager.checkPathForArchiveStatus(dirHandle.getPath());
        } catch (DuplicateArchiveException e) {
            env.put("exception", e.getMessage());
            response.addComment(request.getSession().jprintf(_bundle, env, "fail"));
            return response;
        }

        // Configure the archiveType with our directory handle
        archiveType.setDirectory(dirHandle);

        // Submit the ArchiveType request for execution
        archiveManager.executeArchiveType(archiveType);

        response.addComment(request.getSession().jprintf(_bundle, env, "success"));

        return response;
    }

    private void addConfig(Properties props, String string) {
        if (string.indexOf('=') == -1) {
            throw new IllegalArgumentException(string + " does not contain an = and is therefore not a property");
        }

        String[] data = string.split("=");

        if (data.length != 2) {
            throw new IllegalArgumentException(string + " is therefore not a property because it has no definite key");
        }

        if (props.containsKey(data[0])) {
            throw new IllegalArgumentException(string + " is already contained in the Properties");
        }

        props.put("0." + data[0].toLowerCase(), data[1]);
    }

    public CommandResponse doLISTARCHIVETYPES(CommandRequest request) {
        CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
        int x = 1;
        Map<String, Object> env = new HashMap<>();
        Archive archive;

        try {
            archive = getArchive();
        } catch (ObjectNotFoundException e) {
            response.addComment(request.getSession().jprintf(_bundle, env, "loadarchive"));
            return response;
        }

        for (Iterator<String> iter = archive.getTypesMap().keySet().iterator(); iter.hasNext(); x++) {
            String type = iter.next();
            response.addComment(x + ": " + type);
        }

        return response;
    }

    public CommandResponse doLISTQUEUE(CommandRequest request) {

        CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");

        Map<String, Object> env = new HashMap<>();

        Archive archive;

        try {
            archive = getArchive();
        } catch (ObjectNotFoundException e) {
            response.addComment(request.getSession().jprintf(_bundle, env, "loadarchive"));
            return response;
        }

        int activeJobs = 0;
        int totalJobs = 0;
        for (ArchiveHandler ah : archive.getArchiveHandlers()) {
            if(ah.getJobs().size() != 0) {
                activeJobs++;
            }
            totalJobs++;
        }
        env.put("activejobs", activeJobs);
        env.put("totaljobs", totalJobs);
        response.addComment(request.getSession().jprintf(_bundle, env, "archivequeuestats"));

        return response;
    }

    public String[] getFeatReplies() {
        return null;
    }

}
