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

import net.sf.drftpd.master.BaseFtpConnection;
import net.sf.drftpd.util.ReplacerUtils;
import net.sf.drftpd.util.Time;

import org.apache.log4j.Logger;

import org.drftpd.Bytes;

import org.drftpd.master.ConnectionManager;

import org.drftpd.plugins.SiteBot;

import org.drftpd.slave.SlaveStatus;
import org.drftpd.slave.Transfer;

import org.drftpd.usermanager.NoSuchUserException;
import org.drftpd.usermanager.User;

import org.tanesha.replacer.ReplacerEnvironment;

import java.util.ArrayList;
import java.util.Iterator;


/**
 * @author flowman
 * @version $Id$
 */
public class Bandwidth extends GenericCommandAutoService
    implements IRCPluginInterface {
    private static final Logger logger = Logger.getLogger(Bandwidth.class);
    private SiteBot _listener;

    public Bandwidth(SiteBot listener) {
        super(listener.getIRCConnection());
        _listener = listener;
    }

    public String getCommands() {
        return "!bw !speed";
    }

    private ConnectionManager getConnectionManager() {
        return _listener.getConnectionManager();
    }

    protected void updateCommand(InCommand command) {
        if (!(command instanceof MessageCommand)) {
            return;
        }

        MessageCommand msgc = (MessageCommand) command;

        if (msgc.isPrivateToUs(_listener.getIRCConnection().getClientState())) {
            return;
        }

        String msg = msgc.getMessage();

        if (msg.startsWith("!bw")) {
            SlaveStatus status = getConnectionManager().getGlobalContext()
                                     .getSlaveManager().getAllStatus();

            ReplacerEnvironment env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);

            SiteBot.fillEnvSlaveStatus(env, status, _listener.getSlaveManager());

            _listener.sayChannel(msgc.getDest(),
                ReplacerUtils.jprintf("bw", env, Bandwidth.class));
        } else if (msg.startsWith("!speed ")) {
            String username;

            try {
                username = msgc.getMessage().substring("!speed ".length());
            } catch (ArrayIndexOutOfBoundsException e) {
                logger.warn("", e);

                return;
            } catch (StringIndexOutOfBoundsException e) {
                logger.warn("", e);

                return;
            }

            ReplacerEnvironment env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);
            env.add("user", username);

            String status = ReplacerUtils.jprintf("speed.pre", env,
                    Bandwidth.class);

            String separator = ReplacerUtils.jprintf("speed.separator", env,
                    Bandwidth.class);

            boolean first = true;

            ArrayList conns = new ArrayList(getConnectionManager()
                                                .getConnections());

            for (Iterator iter = conns.iterator(); iter.hasNext();) {
                BaseFtpConnection conn = (BaseFtpConnection) iter.next();

                try {
                    User connUser = conn.getUser();

                    if (!first) {
                        status = status + separator;
                    }

                    if (connUser.getUsername().equals(username)) {
                        env.add("idle",
                            Time.formatTime(System.currentTimeMillis() -
                                conn.getLastActive()));

                        if (getConnectionManager().getGlobalContext().getConfig()
                                    .checkHideInWho(connUser,
                                    conn.getCurrentDirectory())) {
                            continue;
                        }

                        first = false;

                        if (!conn.isExecuting()) {
                            status += ReplacerUtils.jprintf("speed.idle", env,
                                Bandwidth.class);
                        } else if (conn.getDataConnectionHandler()
                                           .isTransfering()) {
                            env.add("speed",
                                Bytes.formatBytes(conn.getDataConnectionHandler()
                                                      .getTransfer()
                                                      .getXferSpeed()) + "/s");

                            env.add("file",
                                conn.getDataConnectionHandler().getTransferFile()
                                    .getName());
                            env.add("slave",
                                conn.getDataConnectionHandler().getTranferSlave()
                                    .getName());

                            if (conn.getTransferDirection() == Transfer.TRANSFER_RECEIVING_UPLOAD) {
                                status += ReplacerUtils.jprintf("speed.up",
                                    env, Bandwidth.class);
                            } else if (conn.getTransferDirection() == Transfer.TRANSFER_SENDING_DOWNLOAD) {
                                status += ReplacerUtils.jprintf("speed.down",
                                    env, Bandwidth.class);
                            }
                        }
                    }
                } catch (NoSuchUserException e) {
                    //just continue.. we aren't interested in connections without logged-in users
                }
            } // for

            status += ReplacerUtils.jprintf("speed.post", env, Bandwidth.class);

            if (first) {
                status = ReplacerUtils.jprintf("speed.error", env,
                        Bandwidth.class);
            }

            _listener.sayChannel(msgc.getDest(), status);
        }
    }
}
