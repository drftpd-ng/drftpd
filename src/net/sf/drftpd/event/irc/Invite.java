package net.sf.drftpd.event.irc;

import net.sf.drftpd.event.InviteEvent;
import net.sf.drftpd.master.ConnectionManager;
import net.sf.drftpd.master.usermanager.NoSuchUserException;
import net.sf.drftpd.master.usermanager.User;
import net.sf.drftpd.master.usermanager.UserFileException;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import f00f.net.irc.martyr.GenericCommandAutoService;
import f00f.net.irc.martyr.InCommand;
import f00f.net.irc.martyr.commands.MessageCommand;

/**
 * @author mog
 *
 * @version $Id: Invite.java,v 1.5 2003/12/23 13:38:18 mog Exp $
 */
public class Invite extends GenericCommandAutoService {

	private Logger logger = Logger.getLogger(Invite.class);

	private ConnectionManager _cm;

	public Invite(IRCListener ircListener) {
		super(ircListener.getIRCConnection());
		_cm = ircListener.getConnectionManager();
	}

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
				} catch (UserFileException e) {
					logger.log(Level.WARN, "", e);
					return;
				}
				if (user.checkPassword(args[2])) {
					logger.info(
						"Invited \""
							+ msgc.getSourceString()
							+ "\" as user "
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
