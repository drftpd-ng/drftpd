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

import net.sf.drftpd.master.BaseFtpConnection;
import net.sf.drftpd.master.command.CommandManager;
import net.sf.drftpd.master.command.CommandManagerFactory;

import org.drftpd.commands.CommandHandler;
import org.drftpd.commands.CommandHandlerFactory;
import org.drftpd.commands.Reply;
import org.drftpd.commands.UnhandledCommandException;


/**
 * returns 200 Command OK on all commands
 *
 * @author mog
 * @version $Id$
 */
public class Dummy implements CommandHandlerFactory {
    public Dummy() {
        super();
    }

    public CommandHandler initialize(BaseFtpConnection conn,
        CommandManager initializer) {
        //_cmdmgr = initializer;
        return new DummyHandler(initializer);
    }

    public void load(CommandManagerFactory initializer) {
    }

    public void unload() {
    }

    static class DummyHandler extends CommandHandler {
        private CommandManager _cmdmgr;

        /**
         * @param initializer
         */
        public DummyHandler(CommandManager initializer) {
            _cmdmgr = initializer;
        }

        public Reply execute(BaseFtpConnection conn)
            throws UnhandledCommandException {
        	if(conn.getRequest().getCommand().equals("SITE FOO"))
        		return new Reply(200, "BAR!");

            return Reply.RESPONSE_200_COMMAND_OK;
        }

        public String[] getFeatReplies() {
            return (String[]) _cmdmgr.getHandledCommands(getClass()).toArray(new String[0]);
        }
    }
}
