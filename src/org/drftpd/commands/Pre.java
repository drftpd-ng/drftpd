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
package org.drftpd.commands;

import net.sf.drftpd.event.DirectoryFtpEvent;
import net.sf.drftpd.master.BaseFtpConnection;
import net.sf.drftpd.master.FtpRequest;
import net.sf.drftpd.master.command.CommandManager;
import net.sf.drftpd.master.command.CommandManagerFactory;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import org.drftpd.Bytes;
import org.drftpd.remotefile.LinkedRemoteFile;
import org.drftpd.remotefile.LinkedRemoteFileInterface;
import org.drftpd.sections.SectionInterface;

import org.drftpd.usermanager.NoSuchUserException;
import org.drftpd.usermanager.User;
import org.drftpd.usermanager.UserFileException;

import java.io.FileNotFoundException;
import java.io.IOException;

import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;

/**
 * @author mog
 *
 * @version $Id$
 */
public class Pre implements CommandHandlerFactory, CommandHandler {
    private static final Logger logger = Logger.getLogger(Pre.class);

    private static void recursiveRemoveOwnership(LinkedRemoteFileInterface dir, long lastModified) {
        dir.setOwner("drftpd");
        dir.setLastModified(lastModified);
        for (Iterator iter = dir.getFiles().iterator(); iter.hasNext();) {
            LinkedRemoteFileInterface file = (LinkedRemoteFileInterface) iter.next();
            file.setOwner("drftpd");
            file.setLastModified(lastModified);
            if (file.isDirectory()) {
                recursiveRemoveOwnership(file, lastModified);
            }
        }
    }

    /**
     * Syntax: SITE PRE <RELEASEDIR> <SECTION>
     */
    public Reply execute(BaseFtpConnection conn)
        throws UnhandledCommandException {
        FtpRequest request = conn.getRequest();

        if (!"SITE PRE".equals(request.getCommand())) {
            throw UnhandledCommandException.create(Pre.class, request);
        }

        if (!request.hasArgument()) {
            return Reply.RESPONSE_501_SYNTAX_ERROR;
        }

        String[] args = request.getArgument().split(" ");

        if (args.length != 2) {
            return Reply.RESPONSE_501_SYNTAX_ERROR;
        }

        SectionInterface section = conn.getGlobalContext().getConnectionManager()
                                       .getGlobalContext().getSectionManager()
                                       .getSection(args[1]);

        if (section.getName().equals("")) {
            return new Reply(200,
                "Invalid section, see SITE SECTIONS for a list of available sections");
        }

        LinkedRemoteFileInterface preDir;

        try {
            preDir = conn.getCurrentDirectory().lookupFile(args[0]);
        } catch (FileNotFoundException e) {
            return Reply.RESPONSE_550_REQUESTED_ACTION_NOT_TAKEN;
        }

        if (!conn.getGlobalContext().getConnectionManager().getGlobalContext()
                     .getConfig().checkPathPermission("pre",
                    conn.getUserNull(), preDir)) {
            return Reply.RESPONSE_530_ACCESS_DENIED;
        }

        Reply response = new Reply(200);

        //AWARD CREDITS
        Hashtable awards = new Hashtable();
        preAwardCredits(conn, preDir, awards);

        for (Iterator iter = awards.entrySet().iterator(); iter.hasNext();) {
            Map.Entry entry = (Map.Entry) iter.next();
            User owner = (User) entry.getKey();

            if (conn.getGlobalContext().getConnectionManager().getGlobalContext()
                        .getConfig().getCreditCheckRatio(preDir, owner) == 0) {
                Long award = (Long) entry.getValue();
                owner.updateCredits(award.longValue());
                response.addComment("Awarded " +
                    Bytes.formatBytes(award.longValue()) + " to " +
                    owner.getName());
            }
        }

        //RENAME
        recursiveRemoveOwnership(preDir, System.currentTimeMillis());

        LinkedRemoteFile toDir;

        try {
            toDir = preDir.renameTo(section.getPath(), preDir.getName());
        } catch (IOException ex) {
            logger.warn("", ex);

            return new Reply(200, ex.getMessage());
        }

        //ANNOUNCE
        conn.getGlobalContext().getConnectionManager().dispatchFtpEvent(new DirectoryFtpEvent(
                conn, "PRE", toDir));

        return Reply.RESPONSE_200_COMMAND_OK;
    }

    public String[] getFeatReplies() {
        return null;
    }

    public CommandHandler initialize(BaseFtpConnection conn,
        CommandManager initializer) {
        return this;
    }

    public void load(CommandManagerFactory initializer) {
    }

    private void preAwardCredits(BaseFtpConnection conn,
        LinkedRemoteFileInterface preDir, Hashtable awards) {
        for (Iterator iter = preDir.getFiles().iterator(); iter.hasNext();) {
            LinkedRemoteFileInterface file = (LinkedRemoteFileInterface) iter.next();
            User owner;

            try {
                owner = conn.getGlobalContext().getConnectionManager()
                            .getGlobalContext().getUserManager().getUserByName(file.getUsername());
            } catch (NoSuchUserException e) {
                logger.log(Level.INFO,
                    "PRE: Cannot award credits to non-existing user", e);

                continue;
            } catch (UserFileException e) {
                logger.log(Level.WARN, "", e);

                continue;
            }

            Long total = (Long) awards.get(owner);

            if (total == null) {
                total = new Long(0);
            }

            total = new Long(total.longValue() +
                    (long) (file.length() * owner.getObjectFloat(
                        UserManagment.RATIO)));
            awards.put(owner, total);
        }
    }

    public void unload() {
    }
}
