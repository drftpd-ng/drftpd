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
package net.sf.drftpd.event.irc;

import f00f.net.irc.martyr.GenericCommandAutoService;
import f00f.net.irc.martyr.InCommand;
import f00f.net.irc.martyr.commands.MessageCommand;

import net.sf.drftpd.util.ReplacerUtils;

import org.drftpd.master.ConnectionManager;
import org.drftpd.plugins.SiteBot;
import org.drftpd.slave.SlaveStatus;

import org.tanesha.replacer.ReplacerEnvironment;


/**
 * @author zubov
 * @version $Id$
 */
public class Diskfree extends GenericCommandAutoService
    implements IRCPluginInterface {
    private SiteBot _listener;

    public Diskfree(SiteBot listener) {
        super(listener.getIRCConnection());
        _listener = listener;
    }

    private ConnectionManager getConnectionManager() {
        return _listener.getConnectionManager();
    }

    public String getCommands() {
        return "!df";
    }

    protected void updateCommand(InCommand command) {
        if (!(command instanceof MessageCommand)) {
            return;
        }

        MessageCommand msgc = (MessageCommand) command;
        String msg = msgc.getMessage();

        if (msgc.isPrivateToUs(_listener.getIRCConnection().getClientState())) {
            return;
        }

        if (msg.equals("!df")) {
            SlaveStatus status = getConnectionManager().getGlobalContext()
                                     .getSlaveManager().getAllStatus();
            ReplacerEnvironment env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);

            SiteBot.fillEnvSlaveStatus(env, status, _listener.getSlaveManager());
            _listener.sayChannel(msgc.getDest(),
                ReplacerUtils.jprintf("diskfree", env, SiteBot.class));
        }
    }
}
