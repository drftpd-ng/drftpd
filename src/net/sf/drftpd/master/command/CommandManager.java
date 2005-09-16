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
package net.sf.drftpd.master.command;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

import net.sf.drftpd.ObjectNotFoundException;
import net.sf.drftpd.master.BaseFtpConnection;

import org.drftpd.commands.CommandHandler;
import org.drftpd.commands.CommandHandlerFactory;
import org.drftpd.commands.ImproperUsageException;
import org.drftpd.commands.Reply;
import org.drftpd.commands.ReplyException;
import org.drftpd.commands.UnhandledCommandException;
import org.drftpd.usermanager.NoSuchUserException;


/**
 * @author mog
 * @version $Id$
 */
public class CommandManager {
    private CommandManagerFactory _factory;

    /**
     * String => CommandHandler
     * Mapping commands to commandhandlers.
     */
    private Map commands = new Hashtable();

    /**
     * Class => CommandHandler
     * Kept so that CommandHandlers can look up each other.
     */
    private Hashtable hnds = new Hashtable();

    public CommandManager(BaseFtpConnection conn,
        CommandManagerFactory initializer) {
        _factory = initializer;

        for (Iterator iter = _factory.getHandlersMap().entrySet().iterator();
                iter.hasNext();) {
            Map.Entry entry = (Map.Entry) iter.next();
            hnds.put(entry.getKey(),
                ((CommandHandlerFactory) entry.getValue()).initialize(conn, this));
        }

        for (Iterator iter = _factory.getCommandsMap().entrySet().iterator();
                iter.hasNext();) {
            Map.Entry entry = (Map.Entry) iter.next();
            commands.put((String) entry.getKey(),
                (CommandHandler) hnds.get((Class) entry.getValue()));
        }
    }

    public Reply execute(BaseFtpConnection conn)
        throws ReplyException {
        String command = conn.getRequest().getCommand();
        CommandHandler handler = (CommandHandler) commands.get(command);

        if (handler == null) {
            throw new UnhandledCommandException("No command handler for " +
                command);
        }
        
        try {
			if (conn.getUser().isDeleted()) {
				conn.stop("You are deleted");
				return new Reply(500, "You are deleted");
			}
		} catch (NoSuchUserException e1) {
			// user hasn't authenticated yet
		}

        try {
            command = command.substring("SITE ".length()).toLowerCase();

            if (!conn.getGlobalContext().getConfig().checkPathPermission(command,
                        conn.getUserNull(), conn.getCurrentDirectory().getPath(), true)) {
                //logger.debug("Blocking access to execute : SITE "+command);
                return Reply.RESPONSE_530_ACCESS_DENIED;
            }
        } catch (java.lang.StringIndexOutOfBoundsException e) {
        }

        try {
			return handler.execute(conn);
		} catch (ImproperUsageException e2) {
			try {
				return new Reply(501, ResourceBundle.getBundle(handler.getClass().getName()).getString(
						"help." + command + ".specific"));
			} catch (MissingResourceException e) {
				return new Reply(501, "Improper usage for the \"SITE " + command + "\" command, bug your siteop to add help");
			}
				
		}
    }

    public CommandHandler getCommandHandler(Class clazz)
        throws ObjectNotFoundException {
        CommandHandler ret = (CommandHandler) hnds.get(clazz);

        if (ret == null) {
            throw new ObjectNotFoundException();
        }
        return ret;
    }

    public List<String> getHandledCommands(Class class1) {
        ArrayList<String> list = new ArrayList<String>();

        for (Iterator iter = commands.entrySet().iterator(); iter.hasNext();) {
            Map.Entry element = (Map.Entry) iter.next();

            if (element.getValue().getClass().equals(class1)) {
                list.add((String) element.getKey());
            }
        }

        return list;
    }

    /**
     * Class => CommandHandler
     */
    public Map getCommandHandlersMap() {
        return Collections.unmodifiableMap(hnds);
    }
}
