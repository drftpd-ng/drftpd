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

import java.util.StringTokenizer;


import org.drftpd.commandmanager.CommandHandler;
import org.drftpd.commandmanager.CommandHandlerFactory;
import org.drftpd.commandmanager.CommandManager;
import org.drftpd.commandmanager.CommandManagerFactory;
import org.drftpd.commandmanager.ImproperUsageException;
import org.drftpd.commandmanager.Reply;
import org.drftpd.commandmanager.UnhandledCommandException;
import org.drftpd.event.InviteEvent;
import org.drftpd.master.BaseFtpConnection;


/**
 * @author mog
 *
 * @version $Id$
 */
public class Invite implements CommandHandler, CommandHandlerFactory {
    public Invite() {
    }

    public Reply execute(BaseFtpConnection conn)
        throws UnhandledCommandException, ImproperUsageException {
        String cmd = conn.getRequest().getCommand();

        if (!"SITE INVITE".equals(cmd)) {
            throw UnhandledCommandException.create(Invite.class,
                conn.getRequest());
        }

        if (!conn.getRequest().hasArgument()) {
        	throw new ImproperUsageException();
        }

        StringTokenizer st = new StringTokenizer(conn.getRequest().getArgument());
        if (st.countTokens() != 1 )
        	return Reply.RESPONSE_501_SYNTAX_ERROR;

        String user = st.nextToken(); 
        
        InviteEvent invite = new InviteEvent(cmd, user, conn.getUserNull());
        conn.getGlobalContext().dispatchFtpEvent(invite);

        return new Reply(200, "Inviting " + user);
    }

//    public String getHelp(String cmd) {
//        ResourceBundle bundle = ResourceBundle.getBundle(Invite.class.getName());
//        if ("".equals(cmd))
//            return bundle.getString("help.general")+"\n";
//        else if ("invite".equals(cmd))
//            return bundle.getString("help.invite")+"\n";
//        else
//            return "";
//    }
    
    public String[] getFeatReplies() {
        return null;
    }

    public CommandHandler initialize(BaseFtpConnection conn,
        CommandManager initializer) {
        return this;
    }

    public void load(CommandManagerFactory initializer) {
    }

    public void unload() {
    }
}
