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

import f00f.net.irc.martyr.GenericAutoService;
import f00f.net.irc.martyr.InCommand;
import f00f.net.irc.martyr.State;
import f00f.net.irc.martyr.commands.MessageCommand;

import net.sf.drftpd.Bytes;
import net.sf.drftpd.master.BaseFtpConnection;
import net.sf.drftpd.master.ConnectionManager;
import net.sf.drftpd.master.config.FtpConfig;
import net.sf.drftpd.util.ReplacerUtils;

import org.apache.log4j.Logger;

import org.drftpd.plugins.SiteBot;

import org.drftpd.slave.RemoteTransfer;

import org.drftpd.usermanager.NoSuchUserException;
import org.drftpd.usermanager.User;

import org.tanesha.replacer.FormatterException;
import org.tanesha.replacer.ReplacerEnvironment;
import org.tanesha.replacer.ReplacerFormat;
import org.tanesha.replacer.SimplePrintf;

import java.util.ArrayList;
import java.util.Iterator;


/**
 * @author mog
 * @version $Id: Who.java,v 1.10 2004/11/03 16:46:38 mog Exp $
 */
public class Who extends GenericAutoService implements IRCPluginInterface {
    private static final Logger logger = Logger.getLogger(Who.class);
    private SiteBot _listener;

    public Who(SiteBot listener) {
        super(listener.getIRCConnection());
        _listener = listener;
    }

    public String getCommands() {
        return "!who";
    }

    private FtpConfig getConfig() {
        return _listener.getConfig();
    }

    private ConnectionManager getConnectionManager() {
        return _listener.getConnectionManager();
    }

    protected void updateCommand(InCommand inCommand) {
        if (!(inCommand instanceof MessageCommand)) {
            return;
        }

        MessageCommand msgc = (MessageCommand) inCommand;

        if (msgc.isPrivateToUs(_listener.getIRCConnection().getClientState())) {
            return;
        }

        String cmd = msgc.getMessage();

        boolean up;
        boolean dn;
        boolean idle;

        if (cmd.equals("!who")) {
            up = dn = idle = true;
        } else if (cmd.equals("!leechers") || cmd.equals("!uploaders") ||
                cmd.equals("!idlers")) {
            dn = cmd.equals("!leechers");
            up = cmd.equals("!uploaders");
            idle = cmd.equals("!idlers");
        } else {
            return;
        }

        try {
            ReplacerFormat formatup = ReplacerUtils.finalFormat(Who.class,
                    "who.up");
            ReplacerFormat formatdown = ReplacerUtils.finalFormat(Who.class,
                    "who.down");
            ReplacerFormat formatidle = ReplacerUtils.finalFormat(Who.class,
                    "who.idle");

            ReplacerEnvironment env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);
            ArrayList conns = new ArrayList(getConnectionManager()
                                                .getConnections());
            int i = 0;

            for (Iterator iter = conns.iterator(); iter.hasNext();) {
                BaseFtpConnection conn = (BaseFtpConnection) iter.next();
                User user;

                try {
                    user = conn.getUser();
                } catch (NoSuchUserException e) {
                    continue;
                }

                if (getConfig().checkHideInWho(user, conn.getCurrentDirectory())) {
                    continue;
                }

                env.add("idle",
                    ((System.currentTimeMillis() - conn.getLastActive()) / 1000) +
                    "s");
                env.add("targetuser", user.getUsername());

                if (!conn.getDataConnectionHandler().isTransfering()) {
                    if (idle) {
                        _listener.sayChannel(msgc.getDest(),
                            SimplePrintf.jprintf(formatidle, env));
                    }
                } else {
                    env.add("speed",
                        Bytes.formatBytes(conn.getDataConnectionHandler()
                                              .getTransfer().getXferSpeed()) +
                        "/s");

                    env.add("file",
                        conn.getDataConnectionHandler().getTransferFile()
                            .getName());
                    env.add("slave",
                        conn.getDataConnectionHandler().getTranferSlave()
                            .getName());

                    if (conn.getTransferDirection() == RemoteTransfer.TRANSFER_RECEIVING_UPLOAD) {
                        if (up) {
                            _listener.sayChannel(msgc.getDest(),
                                SimplePrintf.jprintf(formatup, env));
                            i++;
                        }
                    } else if (conn.getTransferDirection() == RemoteTransfer.TRANSFER_SENDING_DOWNLOAD) {
                        if (dn) {
                            _listener.sayChannel(msgc.getDest(),
                                SimplePrintf.jprintf(formatdown, env));
                            i++;
                        }
                    }
                }
            }
        } catch (FormatterException e) {
            logger.warn("", e);
        }
    }

    protected void updateState(State state) {
    }
}
