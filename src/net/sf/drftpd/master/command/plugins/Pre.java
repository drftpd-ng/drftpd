/*
 * Created on 2003-okt-16
 *
 * To change the template for this generated file go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
package net.sf.drftpd.master.command.plugins;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;

import net.sf.drftpd.Bytes;
import net.sf.drftpd.event.DirectoryFtpEvent;
import net.sf.drftpd.master.BaseFtpConnection;
import net.sf.drftpd.master.FtpReply;
import net.sf.drftpd.master.FtpRequest;
import net.sf.drftpd.master.command.CommandHandler;
import net.sf.drftpd.master.command.CommandManager;
import net.sf.drftpd.master.command.CommandManagerFactory;
import net.sf.drftpd.master.command.UnhandledCommandException;
import net.sf.drftpd.master.usermanager.NoSuchUserException;
import net.sf.drftpd.master.usermanager.User;
import net.sf.drftpd.master.usermanager.UserFileException;
import net.sf.drftpd.remotefile.LinkedRemoteFile;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

/**
 * @author mog
 *
 * To change the template for this generated type comment go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
public class Pre implements CommandHandler {
	public void unload() {}
	public void load(CommandManagerFactory initializer) {}

	private Logger logger = Logger.getLogger(Pre.class);

	private void preAwardCredits(BaseFtpConnection conn, LinkedRemoteFile preDir, Hashtable awards) {
		for (Iterator iter = preDir.getFiles().iterator(); iter.hasNext();) {
			LinkedRemoteFile file = (LinkedRemoteFile) iter.next();
			User owner;
			try {
				owner = conn.getUserManager().getUserByName(file.getUsername());
			} catch (NoSuchUserException e) {
				logger.log(
					Level.INFO,
					"PRE: Cannot award credits to non-existing user",
					e);
				continue;
			} catch (UserFileException e) {
				logger.log(Level.WARN, "", e);
				continue;
			}
			Long total = (Long) awards.get(owner);
			if (total == null)
				total = new Long(0);
			total =
				new Long(
					total.longValue()
						+ (long) (file.length() * owner.getRatio()));
			awards.put(owner, total);
		}
	}

	/**
	 * Syntax: SITE PRE <RELEASEDIR> [SECTION]
	 * @param request
	 * @param out
	 */
	public FtpReply execute(BaseFtpConnection conn)
		throws UnhandledCommandException {
		FtpRequest request = conn.getRequest();
		if(!"SITE PRE".equals(request.getCommand())) throw UnhandledCommandException.create(Pre.class, request);
		if (!request.hasArgument()) {
			return FtpReply.RESPONSE_501_SYNTAX_ERROR;
		}
		String args[] = request.getArgument().split(" ");
		if (args.length != 2) {
			return FtpReply.RESPONSE_501_SYNTAX_ERROR;
		}
		LinkedRemoteFile section;
		try {
			section = conn.getCurrentDirectory().getRoot().lookupFile(args[1]);
		} catch (FileNotFoundException ex) {
return new FtpReply(200, "Release dir not found: " + ex.getMessage());
		}

		LinkedRemoteFile preDir;
		try {
			preDir = conn.getCurrentDirectory().lookupFile(args[0]);
		} catch (FileNotFoundException e) {
			return FtpReply.RESPONSE_550_REQUESTED_ACTION_NOT_TAKEN;
		}
		if (!conn.getConfig().checkPre(conn.getUserNull(), preDir)) {
			return FtpReply.RESPONSE_530_ACCESS_DENIED;
		}

		FtpReply response = new FtpReply(200);

		if (preDir.hasOfflineSlaves()) {
			response.setMessage(
				"Sorry, release has offline files. You don't want to PRE an incomplete release, do you? :(");
			response.setCode(550);
			return response;
		}
		//AWARD CREDITS
		Hashtable awards = new Hashtable();
		preAwardCredits(conn, preDir, awards);
		for (Iterator iter = awards.entrySet().iterator(); iter.hasNext();) {
			Map.Entry entry = (Map.Entry) iter.next();
			User owner = (User) entry.getKey();
			Long award = (Long) entry.getValue();
			response.addComment(
				"Awarded "
					+ Bytes.formatBytes(award.longValue())
					+ " to "
					+ owner.getUsername());
			owner.updateCredits(award.longValue());
		}

		//RENAME
		try {
			preDir.renameTo(section.getPath(), preDir.getName());
		} catch (IOException ex) {
			logger.warn("", ex);
			return new FtpReply(200, ex.getMessage());
		}

		//ANNOUNCE
		logger.debug("preDir after rename: " + preDir);
		conn.getConnectionManager().dispatchFtpEvent(new DirectoryFtpEvent(conn.getUserNull(), "PRE", preDir));

		return FtpReply.RESPONSE_200_COMMAND_OK;
	}

	public CommandHandler initialize(BaseFtpConnection conn, CommandManager initializer) {
		return this;
	}

	public String[] getFeatReplies() {
		return null;
	}

}
