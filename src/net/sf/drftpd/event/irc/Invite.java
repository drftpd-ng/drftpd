/*
 * Created on 2003-okt-27
 *
 * To change the template for this generated file go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
package net.sf.drftpd.event.irc;

import java.io.IOException;

import net.sf.drftpd.event.InviteEvent;
import net.sf.drftpd.master.ConnectionManager;
import net.sf.drftpd.master.usermanager.NoSuchUserException;
import net.sf.drftpd.master.usermanager.User;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import f00f.net.irc.martyr.GenericCommandAutoService;
import f00f.net.irc.martyr.InCommand;
import f00f.net.irc.martyr.commands.MessageCommand;

/**
 * @author mog
 *
 * To change the template for this generated type comment go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
public class Invite extends GenericCommandAutoService {

	private Logger logger = Logger.getLogger(Invite.class);

	private ConnectionManager _cm;

	/**
	 * @param connection
	 */
	public Invite(IRCListener ircListener) {
		super(ircListener.getIRCConnection());
		_cm = ircListener.getConnectionManager();
	}

	/* (non-Javadoc)
	 * @see f00f.net.irc.martyr.GenericCommandAutoService#updateCommand(f00f.net.irc.martyr.InCommand)
	 */
	protected void updateCommand(InCommand command) {
		if(command instanceof MessageCommand) {
			MessageCommand msgc = (MessageCommand)command;
			String msg = msgc.getMessage();
			if (msg.startsWith("!invite ")
				&& msgc.isPrivateToUs(this.getConnection().getClientState())) {
				String args[] = msg.split(" ");
				User user;
				try {
					user = _cm.getUserManager().getUserByName(args[1]);
				} catch (NoSuchUserException e) {
					logger.log(
						Level.WARN,
						args[1] + " " + e.getMessage(),
						e);
					return;
				} catch (IOException e) {
					logger.log(Level.WARN, "", e);
					return;
				}
				if (user.checkPassword(args[2])) {
					logger.info(
						"Invited "
							+ msgc.getSourceString()
							+ " as user "
							+ user.getUsername());
					//_conn.sendCommand(
					//	new InviteCommand(msgc.getSource(), _channelName));
					getConnectionManager().dispatchFtpEvent(
						new InviteEvent(
							"INVITE",
							msgc.getSource().getNick()));
				} else {
					logger.log(
						Level.WARN,
						msgc.getSourceString()
							+ " attempted invite with bad password: "
							+ msgc);
				}
			}
		}
	}

	private ConnectionManager getConnectionManager() {
		return _cm;
	}
}
