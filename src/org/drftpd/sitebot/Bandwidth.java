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

        String status = ReplacerUtils.jprintf("speed.pre", env,
                Bandwidth.class);

        String separator = ReplacerUtils.jprintf("speed.separator", env,
                Bandwidth.class);

        boolean first = true;

        ArrayList<BaseFtpConnection> conns = new ArrayList<BaseFtpConnection>(
				getGlobalContext().getConnectionManager().getConnections());

        for(BaseFtpConnection conn : conns) {

            try {
                User connUser = conn.getUser();

                if (!first) {
                    status = status + separator;
                }

                if (connUser.equals(user)) {
                    env.add("idle",
                        Time.formatTime(System.currentTimeMillis() -
                            conn.getLastActive()));

                    if (getGlobalContext().getConfig().checkPathPermission("hideinwho", connUser, conn.getCurrentDirectory())) {
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

        out.add(status);
		return out;
	}
}
