/*
 * Created on 2004-jan-04
 *
 * To change the template for this generated file go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
package net.sf.drftpd.event.listeners;

import java.util.Calendar;
import java.util.Iterator;

import net.sf.drftpd.Bytes;
import net.sf.drftpd.event.irc.IRCListener;
import net.sf.drftpd.master.BaseFtpConnection;
import net.sf.drftpd.master.usermanager.NoSuchUserException;
import net.sf.drftpd.master.usermanager.User;
import net.sf.drftpd.master.usermanager.UserFileException;

import org.apache.log4j.Logger;
import org.tanesha.replacer.ReplacerEnvironment;

import f00f.net.irc.martyr.GenericCommandAutoService;
import f00f.net.irc.martyr.InCommand;
import f00f.net.irc.martyr.commands.MessageCommand;

class TrialSiteBot extends GenericCommandAutoService {
	private static final Logger logger = Logger.getLogger(TrialSiteBot.class);
	private final Trial _trial;

	private IRCListener _irc;

	private Trial _parent;

	protected TrialSiteBot(Trial trial, IRCListener irc, Trial parent) {
		super(irc.getIRCConnection());
		this._trial = trial;
		_irc = irc;
		_parent = parent;
	}

	protected String jprintf(
		String key,
		User user,
		Limit limit,
		long bytesleft,
		boolean unique) {

		ReplacerEnvironment env = new ReplacerEnvironment();
		env.add("user", user.getUsername());
		if (limit != null) {
			env.add("period", Trial.getPeriodName(limit.getPeriod()));
			env.add("name", limit.getName());
			if (unique) {
				env.add(
					"bonusexpires",
					Trial.getCalendarForEndOfBonus(user, limit.getPeriod()).getTime());
				env.add(
					"uniqueexpires",
					Trial.getCalendarForEndOfFirstPeriod(
						user,
						limit.getPeriod()).getTime());
			}
			env.add(
				"expires",
				Trial.getCalendarForEndOfPeriod(limit.getPeriod()).getTime());
		}
		env.add("bytesleft", Bytes.formatBytes(bytesleft));
		env.add("bytespassed", Bytes.formatBytes(-bytesleft));

		return BaseFtpConnection.jprintf(
			TrialSiteBot.class.getName(),
			key,
			env,
			user);
	}
	protected void updateCommand(InCommand command) {
		try {
			if (!(command instanceof MessageCommand))
				return;
			MessageCommand msgc = (MessageCommand) command;
			String msg = msgc.getMessage();
			if (!msg.startsWith("!passed ")) {
				return;
			}
			String username = msg.substring("!passed ".length());
			User user;
			try {
				user =
					_parent
						.getConnectionManager()
						.getUserManager()
						.getUserByName(
						username);
			} catch (NoSuchUserException e) {
				_irc.say("[passed] No such user: " + username);
				logger.info("", e);
				return;
			} catch (UserFileException e) {
				logger.warn("", e);
				return;
			}
			int i = 0;
			for (Iterator iter = _parent.getLimits().iterator();
				iter.hasNext();
				) {
				Limit limit = (Limit) iter.next();
				if (limit.getPerm().check(user)) {
					i++;

					Calendar endofbonus =
						Trial.getCalendarForEndOfBonus(user, limit.getPeriod());
					if (System.currentTimeMillis()
						<= endofbonus.getTimeInMillis()) {
						//in bonus or unique period
						Calendar endoffirstperiod =
							Trial.getCalendarForEndOfFirstPeriod(
								user,
								limit.getPeriod());
						long bytesleft =
							limit.getBytes() - user.getUploadedBytes();

						if (System.currentTimeMillis()
							<= endoffirstperiod.getTimeInMillis()) {
							//in unique period
							_irc.say(
								jprintf(
									"trialunique",
									user,
									limit,
									bytesleft,
									true));
						} else {
							//in bonus
							_irc.say(
								jprintf(
									"passedunique",
									user,
									limit,
									bytesleft,
									true));
						}
					} else {
						//plain trial
						long bytesleft =
							limit.getBytes()
								- Trial.getUploadedBytesForPeriod(
									user,
									limit.getPeriod());

						if (bytesleft <= 0) {
							_irc.say(
								jprintf(
									"passed",
									user,
									limit,
									bytesleft,
									false));
						} else {
							_irc.say(
								jprintf(
									"trial",
									user,
									limit,
									bytesleft,
									false));
						}

					}
				}
			}
			if (i == 0) {
				_irc.say(jprintf("exempt", user, null, 0, false));
			}
			//_irc.say()
		} catch (RuntimeException e) {
			logger.error("", e);
		}

	}
}