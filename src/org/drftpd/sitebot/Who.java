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

import f00f.net.irc.martyr.commands.MessageCommand;


/**
 * @author mog
 * @version $Id$
 */
public class Who extends IRCCommand {
    private static final Logger logger = Logger.getLogger(Who.class);

    public Who(GlobalContext gctx) {
    	super(gctx);
    }
    
    private ArrayList<String> getData(boolean idle, boolean up, boolean down) {
    	ArrayList<String> out = new ArrayList<String>();
        ReplacerEnvironment env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);
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
                
                synchronized (conn.getDataConnectionHandler()) {
					if (!conn.getDataConnectionHandler().isTransfering()
							&& idle) {
						out.add(SimplePrintf.jprintf(formatidle, env));
					} else {
						env.add("speed", Bytes.formatBytes(conn
								.getDataConnectionHandler().getTransfer()
								.getXferSpeed())
								+ "/s");

						env.add("file", conn.getDataConnectionHandler()
								.getTransferFile().getName());
						env.add("slave", conn.getDataConnectionHandler()
								.getTranferSlave().getName());

						if (conn.getTransferDirection() == Transfer.TRANSFER_RECEIVING_UPLOAD
								&& up) {
							out.add(SimplePrintf.jprintf(formatup, env));
						} else if (conn.getTransferDirection() == Transfer.TRANSFER_SENDING_DOWNLOAD
								&& down) {
							out.add(SimplePrintf.jprintf(formatdown, env));
						}
					}
				}
                
            }
        } catch (FormatterException e) {
            logger.warn("", e);
        }
        if (out.isEmpty()) {
        	out.add(ReplacerUtils.jprintf("who.none", env, Who.class));
        }
        return out;
    }

    public ArrayList<String> doWho(String cmd, MessageCommand msgc) {
    	return getData(true,true,true);
    }
    
    public ArrayList<String> doIdlers(String cmd, MessageCommand msgc) {
    	return getData(true, false, false);
    }
    
    public ArrayList<String> doLeechers(String cmd, MessageCommand msgc) {
    	return getData(false, false, true);
    }
    
    public ArrayList<String> doUploaders(String cmd, MessageCommand msgc) {
    	return getData(false, true, false);
    }
}
