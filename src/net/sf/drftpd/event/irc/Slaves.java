package net.sf.drftpd.event.irc;

import java.rmi.RemoteException;
import java.util.Iterator;

import net.sf.drftpd.Bytes;
import net.sf.drftpd.NoAvailableSlaveException;
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
 * @version $Id: Slaves.java,v 1.1 2004/01/20 06:59:00 mog Exp $
 */
public class Slaves extends GenericAutoService {

	private static final Logger logger = Logger.getLogger(Slaves.class);

	private IRCListener _listener;

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
				} catch (NoAvailableSlaveException e1) {
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
