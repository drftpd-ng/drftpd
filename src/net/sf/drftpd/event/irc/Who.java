package net.sf.drftpd.event.irc;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

import net.sf.drftpd.Bytes;
import net.sf.drftpd.master.BaseFtpConnection;
import net.sf.drftpd.master.ConnectionManager;
import net.sf.drftpd.master.config.FtpConfig;
import net.sf.drftpd.master.usermanager.NoSuchUserException;
import net.sf.drftpd.master.usermanager.User;
import net.sf.drftpd.slave.Transfer;
import net.sf.drftpd.util.ReplacerUtils;

import org.apache.log4j.Logger;
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
 * @version $Id: Who.java,v 1.2 2004/02/09 21:44:46 mog Exp $
 */
public class Who extends GenericAutoService {
	private static final Logger logger = Logger.getLogger(Who.class);

	private IRCListener _listener;

	public Who(IRCListener listener) {
		super(listener.getIRCConnection());
		_listener = listener;
	}

	private FtpConfig getConfig() {
		return _listener.getConfig();
	}

	private ConnectionManager getConnectionManager() {
		return _listener.getConnectionManager();
	}

	private void say(String string) {
		_listener.say(string);
	}

	protected void updateCommand(InCommand inCommand) {
		if (!(inCommand instanceof MessageCommand))
			return;
		MessageCommand msgc = (MessageCommand) inCommand;

		String cmd = msgc.getMessage();
		boolean up, dn, idle;
		if (cmd.equals("!who")) {
			up = dn = idle = true;
		} else if (
			cmd.equals("!leechers")
				|| cmd.equals("!uploaders")
				|| cmd.equals("!idlers")) {
			dn = cmd.equals("!leechers");
			up = cmd.equals("!uploaders");
			idle = cmd.equals("!idlers");
		} else {
			return;
		}

		try {
			ReplacerFormat formatup =
				ReplacerUtils.finalFormat(Who.class, "who.up");
			ReplacerFormat formatdown =
				ReplacerUtils.finalFormat(Who.class, "who.down");
			ReplacerFormat formatidle =
				ReplacerUtils.finalFormat(Who.class, "who.idle");

			ReplacerEnvironment env =
				new ReplacerEnvironment(IRCListener.GLOBAL_ENV);
			ArrayList conns =
				new ArrayList(getConnectionManager().getConnections());
			int i = 0;
			for (Iterator iter = conns.iterator(); iter.hasNext();) {
				BaseFtpConnection conn = (BaseFtpConnection) iter.next();
				User user;
				try {
					user = conn.getUser();
				} catch (NoSuchUserException e) {
					continue;
				}
				if (getConfig()
					.checkHideInWho(user, conn.getCurrentDirectory())) {
					continue;
				}
				env.add(
					"idle",
					(System.currentTimeMillis() - conn.getLastActive()) / 1000
						+ "s");
				env.add("targetuser", user.getUsername());

				if (!conn.getDataConnectionHandler().isTransfering()) {
					if (idle) {
						say(SimplePrintf.jprintf(formatidle, env));
					}
				} else {
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
						if (up) {
							say(SimplePrintf.jprintf(formatup, env));
							i++;
						}

					} else if (
						conn.getTransferDirection()
							== Transfer.TRANSFER_SENDING_DOWNLOAD) {
						if (dn) {
							say(SimplePrintf.jprintf(formatdown, env));
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
