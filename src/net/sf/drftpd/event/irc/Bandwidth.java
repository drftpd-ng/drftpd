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

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Iterator;

import net.sf.drftpd.Bytes;
import net.sf.drftpd.master.BaseFtpConnection;
import net.sf.drftpd.master.ConnectionManager;
import net.sf.drftpd.master.usermanager.NoSuchUserException;
import net.sf.drftpd.master.usermanager.User;
import net.sf.drftpd.slave.SlaveStatus;
import net.sf.drftpd.slave.Transfer;
import net.sf.drftpd.util.ReplacerUtils;
import net.sf.drftpd.util.Time;

import org.apache.log4j.Logger;

import org.tanesha.replacer.ReplacerEnvironment;

import f00f.net.irc.martyr.GenericCommandAutoService;
import f00f.net.irc.martyr.InCommand;
import f00f.net.irc.martyr.commands.MessageCommand;
 
/**
 * @author flowman
 * @version $Id: Bandwidth.java,v 1.2 2004/03/21 06:20:54 zubov Exp $
 */

public class Bandwidth extends GenericCommandAutoService implements IRCPluginInterface {
	
	private IRCListener _listener;
	
	private static final Logger logger = Logger.getLogger(Bandwidth.class);

	private ConnectionManager getConnectionManager() {
		return _listener.getConnectionManager();
	}

	private void say(String string) {
		_listener.say(string);
	}

	private void fillEnvSpace(ReplacerEnvironment env, SlaveStatus status) {
		_listener.fillEnvSpace(env, status);
	}

	public Bandwidth(IRCListener listener) {
		super(listener.getIRCConnection());
		_listener = listener;
	}
	
	protected void updateCommand(InCommand command) {
		try {
			if (command instanceof MessageCommand) {
				MessageCommand msgc = (MessageCommand) command;
				String msg = msgc.getMessage();
				try {
					if (msg.startsWith("!bw")) {
						SlaveStatus status = getConnectionManager().getSlaveManager().getAllStatus();
	
						ReplacerEnvironment env =
							new ReplacerEnvironment(IRCListener.GLOBAL_ENV);
	
						fillEnvSpace(env, status);
	
						say(ReplacerUtils.jprintf("bw", env, IRCListener.class));
					} else if (msg.startsWith("!speed")) {
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
						ReplacerEnvironment env = new ReplacerEnvironment(IRCListener.GLOBAL_ENV);
						env.add("user", username);
						String status =
							ReplacerUtils.jprintf("speed.pre", env, IRCListener.class);

						String separator =
							ReplacerUtils.jprintf("speed.separator", env, IRCListener.class);

						boolean first = true;

						ArrayList conns =
							new ArrayList(getConnectionManager().getConnections());
						for (Iterator iter = conns.iterator(); iter.hasNext();) {
							BaseFtpConnection conn = (BaseFtpConnection) iter.next();
							try {
								User connUser = conn.getUser();
								if (!first) {
									status = status + separator;
								}
								if (connUser.getUsername().equals(username)) {

									env.add(
										"idle",
										Time.formatTime(
											System.currentTimeMillis() - conn.getLastActive()));

									if (getConnectionManager()
										.getConfig()
										.checkHideInWho(connUser, conn.getCurrentDirectory()))
										continue;
									first = false;
									if (!conn.isExecuting()) {
										status
											+= ReplacerUtils.jprintf(
												"speed.idle",
												env,
												IRCListener.class);

									} else if (
										conn.getDataConnectionHandler().isTransfering()) {
										try {
											env.add(
												"speed",
												Bytes.formatBytes(
													conn
														.getDataConnectionHandler()
														.getTransfer()
														.getXferSpeed())
													+ "/s");
										} catch (RemoteException e2) {
											logger.warn("", e2);
										}
										env.add(
											"file",
											conn
												.getDataConnectionHandler()
												.getTransferFile()
												.getName());
										env.add(
											"slave",
											conn
												.getDataConnectionHandler()
												.getTranferSlave()
												.getName());

										if (conn.getTransferDirection()
											== Transfer.TRANSFER_RECEIVING_UPLOAD) {
											status
												+= ReplacerUtils.jprintf(
													"speed.up",
													env,
													IRCListener.class);

										} else if (
											conn.getTransferDirection()
												== Transfer.TRANSFER_SENDING_DOWNLOAD) {
											status
												+= ReplacerUtils.jprintf(
													"speed.down",
													env,
													IRCListener.class);
										}
									}
								}
							} catch (NoSuchUserException e) {
								//just continue.. we aren't interested in connections without logged-in users
							}
						} // for
						status += ReplacerUtils.jprintf("speed.post", env, IRCListener.class);
						if (first) {
							status =
								ReplacerUtils.jprintf("speed.error", env, IRCListener.class);
						}
						say(status);	
					}
				} catch (Exception e) {
					logger.debug("", e);
				}
			}
		}catch (Exception e) {
			logger.debug("",e);
		}
	}
	public String getCommands() {
		return "!bw !speed";
	}
}