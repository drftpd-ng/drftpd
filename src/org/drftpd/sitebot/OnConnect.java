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

import org.drftpd.plugins.SiteBot;

import f00f.net.irc.martyr.GenericAutoService;
import f00f.net.irc.martyr.IRCConnection;
import f00f.net.irc.martyr.InCommand;
import f00f.net.irc.martyr.State;
import f00f.net.irc.martyr.commands.JoinCommand;
import f00f.net.irc.martyr.commands.RawCommand;
import f00f.net.irc.martyr.commands.WelcomeCommand;
import f00f.net.irc.martyr.errors.ChannelBannedError;
import f00f.net.irc.martyr.errors.ChannelInviteOnlyError;
import f00f.net.irc.martyr.errors.ChannelLimitError;
import f00f.net.irc.martyr.errors.ChannelWrongKeyError;



/**
 * @author mog
 * @version $Id$
 */
public class OnConnect extends GenericAutoService implements IRCPluginInterface {
    private State _state;

    public OnConnect(SiteBot sitebot) {
        super(sitebot.getIRCConnection());

        IRCConnection connection = sitebot.getIRCConnection();

        updateState(connection.getState());
        _state = connection.getState();
    }

    protected void updateState(State state) {
    }

    protected void updateCommand(InCommand command) {
        if ((command.getState() != _state) &&
                (command.getState() == State.REGISTERED) &&
                command instanceof WelcomeCommand) {
            onConnect((WelcomeCommand) command);
        }

        _state = command.getState();

        if (command instanceof JoinCommand) {
            onJoin((JoinCommand) command);
        } else if (command instanceof ChannelInviteOnlyError) {
            needInvite((ChannelInviteOnlyError) command);
        } else if (command instanceof ChannelWrongKeyError) {
            needKey((ChannelWrongKeyError) command);
        } else if (command instanceof ChannelBannedError) {
            needUnban((ChannelBannedError) command);
        } else if (command instanceof ChannelLimitError) {
            needLimit((ChannelLimitError) command);
        }

        //TODO need-op?
    }

    private void onJoin(JoinCommand command) {
    }

    private void needLimit(ChannelLimitError error) {
    }

    private void needUnban(ChannelBannedError error) {
    }

    private void needKey(ChannelWrongKeyError error) {
    }

    private void needInvite(ChannelInviteOnlyError error) {
    }

    private void onConnect(WelcomeCommand command) {
        getConnection().sendCommand(new RawCommand("MODE " + command.getNick() +
                " :+i"));
    }

    public String getCommands() {
        return null;
    }
}
