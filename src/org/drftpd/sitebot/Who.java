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

import java.util.ArrayList;
import java.util.Iterator;

import net.sf.drftpd.master.BaseFtpConnection;
import net.sf.drftpd.util.ReplacerUtils;

import org.apache.log4j.Logger;
import org.drftpd.Bytes;
import org.drftpd.GlobalContext;
import org.drftpd.plugins.SiteBot;
import org.drftpd.slave.Transfer;
import org.drftpd.usermanager.NoSuchUserException;
import org.drftpd.usermanager.User;
import org.tanesha.replacer.FormatterException;
import org.tanesha.replacer.ReplacerEnvironment;
import org.tanesha.replacer.ReplacerFormat;
import org.tanesha.replacer.SimplePrintf;

import f00f.net.irc.martyr.GenericAutoService;
import f00f.net.irc.martyr.InCommand;
import f00f.net.irc.martyr.State;
import f00f.net.irc.martyr.commands.MessageCommand;


/**
 * @author mog
 * @version $Id$
 */
public class Who extends GenericAutoService implements IRCPluginInterface {
    private static final Logger logger = Logger.getLogger(Who.class);
    private SiteBot _listener;
    private String _trigger;

    public Who(SiteBot listener) {
        super(listener.getIRCConnection());
        _listener = listener;
        _trigger = _listener.getCommandPrefix();
    }

    public String getCommands() {
        return _trigger + "who";
    }

    public String getCommandsHelp(User user) {
        String help = "";
        if (_listener.getIRCConfig().checkIrcPermission(_listener.getCommandPrefix() + "who", user))
            help += _listener.getCommandPrefix() + "who : Show who is currently connected to the ftp server.\n";
        return help;
    }

    private GlobalContext getGlobalContext() {
        return _listener.getGlobalContext();
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

        if (cmd.equals(_trigger + "who")) {
            up = dn = idle = true;
        } else if (cmd.equals(_trigger + "leechers") || cmd.equals(_trigger + "uploaders") ||
                cmd.equals(_trigger + "idlers")) {
            dn = cmd.equals(_trigger + "leechers");
            up = cmd.equals(_trigger + "uploaders");
            idle = cmd.equals(_trigger + "idlers");
        } else {
            return;
        }

        ReplacerEnvironment env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);
		env.add("botnick",_listener.getIRCConnection().getClientState().getNick().getNick());
		env.add("ircnick",msgc.getSource().getNick());	
		try {
            if (!_listener.getIRCConfig().checkIrcPermission(
                    cmd,msgc.getSource())) {
            	_listener.sayChannel(msgc.getDest(), 
            			ReplacerUtils.jprintf("ident.denymsg", env, SiteBot.class));
            	return;				
            }
        } catch (NoSuchUserException e) {
			_listener.sayChannel(msgc.getDest(), 
					ReplacerUtils.jprintf("ident.noident", env, SiteBot.class));
			return;
        }

        try {
            ReplacerFormat formatup = ReplacerUtils.finalFormat(Who.class,
                    "who.up");
            ReplacerFormat formatdown = ReplacerUtils.finalFormat(Who.class,
                    "who.down");
            ReplacerFormat formatidle = ReplacerUtils.finalFormat(Who.class,
                    "who.idle");

            ArrayList<BaseFtpConnection> conns = new ArrayList<BaseFtpConnection>(getGlobalContext().getConnectionManager()
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

                if (getGlobalContext().getConfig().checkPathPermission(
						"hideinwho", user, conn.getCurrentDirectory())) {
					continue;
				}

                env.add("idle",
                    ((System.currentTimeMillis() - conn.getLastActive()) / 1000) +
                    "s");
                env.add("targetuser", user.getName());

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

                    if (conn.getTransferDirection() == Transfer.TRANSFER_RECEIVING_UPLOAD) {
                        if (up) {
                            _listener.sayChannel(msgc.getDest(),
                                SimplePrintf.jprintf(formatup, env));
                            i++;
                        }
                    } else if (conn.getTransferDirection() == Transfer.TRANSFER_SENDING_DOWNLOAD) {
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
