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

import net.sf.drftpd.ObjectNotFoundException;
import net.sf.drftpd.SlaveUnavailableException;
import net.sf.drftpd.util.ReplacerUtils;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.drftpd.GlobalContext;
import org.drftpd.master.RemoteSlave;
import org.drftpd.plugins.SiteBot;
import org.drftpd.slave.SlaveStatus;
import org.tanesha.replacer.ReplacerEnvironment;

import f00f.net.irc.martyr.GenericAutoService;
import f00f.net.irc.martyr.InCommand;
import f00f.net.irc.martyr.State;
import f00f.net.irc.martyr.commands.MessageCommand;


/**
 * @author mog
 * @version $Id$
 */
public class Slaves extends GenericAutoService implements IRCPluginInterface {
    private static final int LEN2 = "!slave ".length();
    private static final Logger logger = Logger.getLogger(Slaves.class);
    private SiteBot _listener;
    private String _trigger;

    public Slaves(SiteBot listener) {
        super(listener.getIRCConnection());
        _listener = listener;
        _trigger = _listener.getCommandPrefix();
    }

    public String getCommands() {
        return _trigger + "slaves " + _trigger + "slave";
    }

    public String getCommandsHelp() {
        return _trigger + "slave <name> : Show transfer stats and disk free for a specific slave.\n" + 
        		_trigger + "slaves : Show transfer stats and disk free for all slaves.";
    }

    private GlobalContext getGlobalContext() {
        return _listener.getGlobalContext();
    }

    private String makeStatusString(RemoteSlave rslave) {
        ReplacerEnvironment env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);
        env.add("slave", rslave.getName());

        try {
            SlaveStatus status;

            try {
                status = rslave.getSlaveStatusAvailable();
            } catch (SlaveUnavailableException e1) {
                return ReplacerUtils.jprintf("slaves.offline", env, Slaves.class);
            }

            SiteBot.fillEnvSlaveStatus(env, status, _listener.getSlaveManager());

            return ReplacerUtils.jprintf("slaves", env, Slaves.class);
        } catch (RuntimeException t) {
            logger.log(Level.WARN, "Caught RuntimeException in !slaves loop", t);

            return ReplacerUtils.jprintf("slaves.offline", env, Slaves.class);
        }
    }

    protected void updateCommand(InCommand inCommand) {
        if (!(inCommand instanceof MessageCommand)) {
            return;
        }

        MessageCommand msgc = (MessageCommand) inCommand;

        if (!msgc.getMessage().startsWith(_trigger + "slave") ||
                msgc.isPrivateToUs(_listener.getIRCConnection().getClientState())) {
            return;
        }

        String chan = msgc.getDest();

        if (msgc.getMessage().startsWith(_trigger + "slave ")) {
            String slaveName = msgc.getMessage().substring(LEN2);

            try {
                RemoteSlave rslave = getGlobalContext()
                                         .getSlaveManager().getRemoteSlave(slaveName);
                _listener.sayChannel(chan, makeStatusString(rslave));
            } catch (ObjectNotFoundException e) {
                ReplacerEnvironment env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);
                env.add("slave", slaveName);
                _listener.sayChannel(chan,
                    ReplacerUtils.jprintf("slaves.notfound", env, Slaves.class));
            }
        } else if (msgc.getMessage().equals(_trigger + "slaves")) {
        	for(RemoteSlave rslave : getGlobalContext().getSlaveManager().getSlaves()) {
                _listener.sayChannel(chan, makeStatusString(rslave));
            }
        }
    }

    protected void updateState(State state) {
    }
}
