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
package net.sf.drftpd.master.command.plugins;

import net.sf.drftpd.event.InviteEvent;
import net.sf.drftpd.master.BaseFtpConnection;
import net.sf.drftpd.master.FtpReply;
import net.sf.drftpd.master.command.CommandManager;
import net.sf.drftpd.master.command.CommandManagerFactory;

import org.drftpd.commands.CommandHandler;
import org.drftpd.commands.CommandHandlerFactory;
import org.drftpd.commands.UnhandledCommandException;


/**
 * @author mog
 *
 * @version $Id: Invite.java,v 1.11 2004/08/03 20:13:57 zubov Exp $
 */
public class Invite implements CommandHandlerFactory, CommandHandler {
    public Invite() {
    }

    public FtpReply execute(BaseFtpConnection conn)
        throws UnhandledCommandException {
        String cmd = conn.getRequest().getCommand();

        if (!"SITE INVITE".equals(cmd)) {
            throw UnhandledCommandException.create(Invite.class,
                conn.getRequest());
        }

        if (!conn.getRequest().hasArgument()) {
            return new FtpReply(501, conn.jprintf(Invite.class, "invite.usage"));
        }

        String user = conn.getRequest().getArgument();
        InviteEvent invite = new InviteEvent(cmd, user);
        conn.getConnectionManager().dispatchFtpEvent(invite);

        return new FtpReply(200, "Inviting " + user);
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

    public void unload() {
    }
}
