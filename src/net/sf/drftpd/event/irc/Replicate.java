package net.sf.drftpd.event.irc;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.StringTokenizer;

import net.sf.drftpd.ObjectNotFoundException;
import net.sf.drftpd.master.ConnectionManager;
import net.sf.drftpd.master.RemoteSlave;
import net.sf.drftpd.master.TransferThread;
import net.sf.drftpd.remotefile.LinkedRemoteFile;
import f00f.net.irc.martyr.GenericCommandAutoService;
import f00f.net.irc.martyr.InCommand;
import f00f.net.irc.martyr.clientstate.ClientState;
import f00f.net.irc.martyr.commands.MessageCommand;

/**
 * @author mog
 *
 * To change the template for this generated type comment go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
public class Replicate extends GenericCommandAutoService {

	private ConnectionManager _cm;
	private IRCListener _irc;
	public Replicate(IRCListener ircListener) {
		super(ircListener.getIRCConnection());
		_irc = ircListener;
		_cm = ircListener.getConnectionManager();
	}

	protected void updateCommand(InCommand command) {
		if (!(command instanceof MessageCommand))
			return;
		MessageCommand mc = (MessageCommand) command;
		if (!mc.getMessage().startsWith("!replic "))
			return;

		StringTokenizer st =
			new StringTokenizer(mc.getMessage().substring("!replic ".length()));
		RemoteSlave destrslave;
		try {
			destrslave =
				getConnectionManager().getSlaveManager().getSlave(
					st.nextToken());
		} catch (ObjectNotFoundException e) {
			getConnection().sendCommand(
				new MessageCommand(
					reply(mc, getConnection().getClientState()),
					e.getMessage()));
			return;
		}

		while (st.hasMoreTokens()) {
			LinkedRemoteFile srcfile;
			try {
				srcfile = getConnectionManager()
											.getSlaveManager()
											.getRoot()
											.lookupFile(st.nextToken());
				getConnection().sendCommand(
					new MessageCommand(
						reply(mc, getConnection().getClientState()),
						"replicating "+srcfile.getPath()+" to "+destrslave.getName()));
				try {
					new TransferThread(srcfile, destrslave).transfer();
				} catch (IOException e2) {
					e2.printStackTrace();
				}
				getConnection().sendCommand(
					new MessageCommand(
						reply(mc, getConnection().getClientState()),
						"done replicating "+srcfile.getPath()+" to "+destrslave.getName()));
			} catch (FileNotFoundException e1) {
				getConnection().sendCommand(
					new MessageCommand(
						reply(mc, getConnection().getClientState()),
							e1.getMessage()));
				continue;
			}
		}
	}
	
	private static String reply(MessageCommand mc, ClientState state) {
		if (mc.isPrivateToUs(state)) {
			return mc.getSource().getNick();
		} else {
			return mc.getDest();
		}
	}
	private ConnectionManager getConnectionManager() {
		return _cm;
	}
}
