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
package org.drftpd.sitebot;

import net.sf.drftpd.util.ReplacerUtils;

import org.drftpd.GlobalContext;
import org.drftpd.plugins.SiteBot;
import org.drftpd.slave.SlaveStatus;
import org.tanesha.replacer.ReplacerEnvironment;

import f00f.net.irc.martyr.GenericCommandAutoService;
import f00f.net.irc.martyr.InCommand;
import f00f.net.irc.martyr.commands.MessageCommand;


/**
 * @author zubov
 * @version $Id$
 */
public class Diskfree extends GenericCommandAutoService
    implements IRCPluginInterface {
    private SiteBot _listener;
    private String _trigger;

    public Diskfree(SiteBot listener) {
        super(listener.getIRCConnection());
        _listener = listener;
        _trigger = _listener.getCommandPrefix();
    }

    private GlobalContext getGlobalContext() {
        return _listener.getGlobalContext();
    }

    public String getCommands() {
        return _trigger + "df";
    }

    public String getCommandsHelp() {
    	return _trigger + "df : Show total disk usage for all slaves.";
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

        if (msg.equals(_trigger + "df")) {
            SlaveStatus status = getGlobalContext().getSlaveManager()
					.getAllStatus();
            ReplacerEnvironment env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);

            SiteBot.fillEnvSlaveStatus(env, status, _listener.getSlaveManager());
            _listener.sayChannel(msgc.getDest(),
                ReplacerUtils.jprintf("diskfree", env, SiteBot.class));
        }
    }
}
