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
import java.util.Iterator;

import net.sf.drftpd.Bytes;
import net.sf.drftpd.SlaveUnavailableException;
import net.sf.drftpd.master.ConnectionManager;
import net.sf.drftpd.master.RemoteSlave;
import net.sf.drftpd.slave.SlaveStatus;
import net.sf.drftpd.util.ReplacerUtils;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.tanesha.replacer.ReplacerEnvironment;

import f00f.net.irc.martyr.GenericAutoService;
import f00f.net.irc.martyr.InCommand;
import f00f.net.irc.martyr.State;
import f00f.net.irc.martyr.commands.MessageCommand;

/**
 * @author mog
 * @version $Id: Slaves.java,v 1.4 2004/03/21 06:20:54 zubov Exp $
 */
public class Slaves extends GenericAutoService implements IRCPluginInterface {

	private static final Logger logger = Logger.getLogger(Slaves.class);

	private IRCListener _listener;
	
	public String getCommands() {
		return "!slaves";
	}

	public Slaves(IRCListener listener) {
		super(listener.getIRCConnection());
		_listener = listener;
	}

	private void fillEnvSpace(ReplacerEnvironment env, SlaveStatus status) {
		_listener.fillEnvSpace(env, status);
	}

	private ConnectionManager getConnectionManager() {
		return _listener.getConnectionManager();
	}

	private void say(String string) {
		_listener.say(string);
	}

	protected void updateCommand(InCommand inCommand) {
		if (!(inCommand instanceof MessageCommand)
			|| !((MessageCommand) inCommand).getMessage().equals("!slaves"))
			return;

		for (Iterator iter =
			getConnectionManager().getSlaveManager().getSlaves().iterator();
			iter.hasNext();
			) {
			RemoteSlave rslave = (RemoteSlave) iter.next();
			String statusString;

			ReplacerEnvironment env =
				new ReplacerEnvironment(IRCListener.GLOBAL_ENV);
			env.add("slave", rslave.getName());

			try {
				SlaveStatus status;
				try {
					status = rslave.getSlave().getSlaveStatus();
				} catch (SlaveUnavailableException e1) {
					say(
						ReplacerUtils.jprintf(
							"slaves.offline",
							env,
							Slaves.class));
					continue;
				}

				env.add("xfers", Integer.toString(status.getTransfers()));
				env.add(
					"throughput",
					Bytes.formatBytes(status.getThroughput()) + "/s");

				env.add(
					"xfersup",
					Integer.toString(status.getTransfersReceiving()));
				env.add(
					"throughputup",
					Bytes.formatBytes(status.getThroughputReceiving()) + "/s");

				env.add(
					"xfersdown",
					Integer.toString(status.getTransfersSending()));
				env.add(
					"throughputdown",
					Bytes.formatBytes(status.getThroughputSending()));

				fillEnvSpace(env, status);

				statusString =
					ReplacerUtils.jprintf("slaves", env, Slaves.class);
			} catch (RuntimeException t) {
				logger.log(
					Level.WARN,
					"Caught RuntimeException in !slaves loop",
					t);
				statusString = "RuntimeException";
			} catch (RemoteException e) {
				rslave.handleRemoteException(e);
				statusString = "offline";
			}
			say(statusString);
		}

	}

	protected void updateState(State state) {
	}

}
