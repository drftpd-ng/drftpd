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

import net.sf.drftpd.ObjectNotFoundException;
import net.sf.drftpd.master.BaseFtpConnection;
import net.sf.drftpd.util.ReplacerUtils;

import org.apache.log4j.Logger;
import org.drftpd.Bytes;
import org.drftpd.GlobalContext;
import org.drftpd.Time;
import org.drftpd.plugins.SiteBot;
import org.drftpd.slave.SlaveStatus;
import org.drftpd.slave.Transfer;
import org.drftpd.usermanager.NoSuchUserException;
import org.drftpd.usermanager.User;
import org.drftpd.usermanager.UserFileException;
import org.tanesha.replacer.ReplacerEnvironment;

import f00f.net.irc.martyr.commands.MessageCommand;


/**
 * @author flowman
 * @version $Id$
 */
public class Bandwidth extends IRCCommand {
    private static final Logger logger = Logger.getLogger(Bandwidth.class);

    public Bandwidth(GlobalContext gctx) {
		super(gctx);
    }

	public ArrayList<String> doBandwidth(String args, MessageCommand msgc) {
	    ArrayList<String> out = new ArrayList<String>();
		ReplacerEnvironment env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);
		SlaveStatus status = getGlobalContext().getSlaveManager().getAllStatus();
		SiteBot.fillEnvSlaveStatus(env, status, getGlobalContext().getSlaveManager());
		out.add(ReplacerUtils.jprintf("bw", env, Bandwidth.class));
		return out;
	}
	
	public ArrayList<String> doSpeed(String args, MessageCommand msgc) {
	    ArrayList<String> out = new ArrayList<String>();
		ReplacerEnvironment env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);
		String username = args;
        User user = null;

        env.add("user", username);
        try {
			user = getGlobalContext().getUserManager().getUserByName(username);
		} catch (NoSuchUserException e1) {
			out.add(ReplacerUtils.jprintf("speed.usernotfound", env, Bandwidth.class));
			return out;
		} catch (UserFileException e1) {
		    out.add(ReplacerUtils.jprintf("speed.usererror", env, Bandwidth.class));
			return out;
		}

        out.add(ReplacerUtils.jprintf("speed.pre", env,Bandwidth.class));

        ArrayList<BaseFtpConnection> conns = new ArrayList<BaseFtpConnection>(
				getGlobalContext().getConnectionManager().getConnections());

        boolean found = false;
        for(BaseFtpConnection conn : conns) {
            try {
                User connUser = conn.getUser();
                if (connUser.equals(user)) {
                    found = true;
                    env.add("idle",
                        Time.formatTime(System.currentTimeMillis() -
                            conn.getLastActive()));

                    if (getGlobalContext().getConfig().checkPathPermission("hideinwho", connUser, conn.getCurrentDirectory())) {
                        continue;
                    }
                    synchronized (conn.getDataConnectionHandler()) {
						if (!conn.isExecuting()) {
							out.add(ReplacerUtils.jprintf("speed.idle", env,
									Bandwidth.class));
						} else if (conn.getDataConnectionHandler()
								.isTransfering()) {
							try {
								env.add("speed", Bytes.formatBytes(conn
										.getDataConnectionHandler()
										.getTransfer().getXferSpeed())
										+ "/s");
							} catch (ObjectNotFoundException e) {
								logger.debug("This is a bug, please report it",
										e);
							}
							env.add("file", conn.getDataConnectionHandler()
									.getTransferFile().getName());
							env.add("slave", conn.getDataConnectionHandler()
									.getTranferSlave().getName());

							if (conn.getTransferDirection() == Transfer.TRANSFER_RECEIVING_UPLOAD) {
								out.add(ReplacerUtils.jprintf("speed.up", env,
										Bandwidth.class));
							} else if (conn.getTransferDirection() == Transfer.TRANSFER_SENDING_DOWNLOAD) {
								out.add(ReplacerUtils.jprintf("speed.down",
										env, Bandwidth.class));
							}
						}
					}
                }
            } catch (NoSuchUserException e) {
                //just continue.. we aren't interested in connections without logged-in users
            }
        } // for

        if (!found) {
        	out.clear();
            out.add(ReplacerUtils.jprintf("speed.error", env, Bandwidth.class));
            return out;
        }
        out.add(ReplacerUtils.jprintf("speed.post", env, Bandwidth.class));

		return out;
	}
}
