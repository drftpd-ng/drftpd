package net.sf.drftpd.master.command.plugins;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import net.sf.drftpd.Bytes;
import net.sf.drftpd.ObjectExistsException;
import net.sf.drftpd.ObjectNotFoundException;
import net.sf.drftpd.event.NukeEvent;
import net.sf.drftpd.master.BaseFtpConnection;
import net.sf.drftpd.master.FtpReply;
import net.sf.drftpd.master.command.CommandHandler;
import net.sf.drftpd.master.command.CommandManager;
import net.sf.drftpd.master.command.CommandManagerFactory;
import net.sf.drftpd.master.command.UnhandledCommandException;
import net.sf.drftpd.master.queues.NukeLog;
import net.sf.drftpd.master.usermanager.AbstractUser;
import net.sf.drftpd.master.usermanager.NoSuchUserException;
import net.sf.drftpd.master.usermanager.User;
import net.sf.drftpd.master.usermanager.UserFileException;
import net.sf.drftpd.remotefile.LinkedRemoteFile;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.input.SAXBuilder;

/**
 * @author mog
 *
 * To change the template for this generated type comment go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
public class Nuke implements CommandHandler {
	public Nuke() {
	}
	public void unload() {
		_nukelog = null;
	}
	private NukeLog _nukelog;

	private Logger logger = Logger.getLogger(Nuke.class);

	public FtpReply execute(BaseFtpConnection conn)
		throws UnhandledCommandException {
		if (_nukelog == null) {
			return new FtpReply(500, "You must reconnect to use NUKE");
		}
		String cmd = conn.getRequest().getCommand();
		if ("SITE NUKE".equals(cmd))
			return doSITE_NUKE(conn);
		if ("SITE NUKES".equals(cmd))
			return doSITE_NUKES(conn);
		if ("SITE UNNUKE".equals(cmd))
			return doSITE_UNNUKE(conn);
		throw UnhandledCommandException.create(Nuke.class, conn.getRequest());
	}

	private static void nukeRemoveCredits(
		LinkedRemoteFile nukeDir,
		Hashtable nukees) {
		for (Iterator iter = nukeDir.getFiles().iterator(); iter.hasNext();) {
			LinkedRemoteFile file = (LinkedRemoteFile) iter.next();
			if (file.isDirectory()) {
				nukeRemoveCredits(file, nukees);
			}
			if (file.isFile()) {
				String owner = file.getUsername();
				Long total = (Long) nukees.get(owner);
				if (total == null)
					total = new Long(0);
				total = new Long(total.longValue() + file.length());
				nukees.put(owner, total);
			}
		}
	}
	/**
	 * USAGE: site nuke <directory> <multiplier> <message>
	 * Nuke a directory
	 *
	 * ex. site nuke shit 2 CRAP
	 *
	 * This will nuke the directory 'shit' and remove x2 credits with the
	 * comment 'CRAP'.
	 *
	 * NOTE: You can enclose the directory in braces if you have spaces in the name
	 * ex. site NUKE {My directory name} 1 because_i_dont_like_it
	 * 
	 * Q)  What does the multiplier in 'site nuke' do?
	 * A)  Multiplier is a penalty measure. If it is 0, the user doesn't lose any
	 *     credits for the stuff being nuked. If it is 1, user only loses the
	 *     amount of credits he gained by uploading the files (which is calculated
	 *     by multiplying total size of file by his/her ratio). If multiplier is more
	 *     than 1, the user loses the credits he/she gained by uploading, PLUS some
	 *     extra credits. The formula is this: size * ratio + size * (multiplier - 1).
	 *     This way, multiplier of 2 causes user to lose size * ratio + size * 1,
	 *     so the additional penalty in this case is the size of nuked files. If the
	 *     multiplier is 3, user loses size * ratio + size * 2, etc.
	 */
	//TODO remove upload statistics
	public FtpReply doSITE_NUKE(BaseFtpConnection conn) {
		if (!conn.getUserNull().isNuker()) {
			return FtpReply.RESPONSE_530_ACCESS_DENIED;
		}

		StringTokenizer st =
			new StringTokenizer(conn.getRequest().getArgument(), " ");

		if (!st.hasMoreTokens()) {
			return FtpReply.RESPONSE_501_SYNTAX_ERROR;
		}

		int multiplier;
		LinkedRemoteFile nukeDir;
		String nukeDirName;
		try {
			nukeDirName = st.nextToken();
			nukeDir = conn.getCurrentDirectory().getFile(nukeDirName);
		} catch (FileNotFoundException e) {
			FtpReply response = new FtpReply(550, e.getMessage());
			return response;
		}
		if (!nukeDir.isDirectory()) {
			FtpReply response =
				new FtpReply(550, nukeDirName + ": not a directory");
			return response;
		}
		String nukeDirPath = nukeDir.getPath();

		if (!st.hasMoreTokens()) {
			return FtpReply.RESPONSE_501_SYNTAX_ERROR;
		}

		try {
			multiplier = Integer.parseInt(st.nextToken());
		} catch (NumberFormatException ex) {
			logger.warn("", ex);
			return new FtpReply(501, "Invalid multiplier: " + ex.getMessage());
		}

		String reason;
		if (st.hasMoreTokens()) {
			reason = st.nextToken("").trim();
		} else {
			reason = "";
		}
		//get nukees with string as key
		Hashtable nukees = new Hashtable();
		nukeRemoveCredits(nukeDir, nukees);

		FtpReply response = new FtpReply(200, "NUKE suceeded");

		//get nukees User with user as key
		HashMap nukees2 = new HashMap(nukees.size());
		for (Iterator iter = nukees.keySet().iterator(); iter.hasNext();) {

			String username = (String) iter.next();
			User user;
			try {
				user = conn.getUserManager().getUserByName(username);
			} catch (NoSuchUserException e1) {
				response.addComment(
					"Cannot remove credits from "
						+ username
						+ ": "
						+ e1.getMessage());
				logger.warn("", e1);
				user = null;
			} catch (UserFileException e1) {
				response.addComment(
					"Cannot read user data for "
						+ username
						+ ": "
						+ e1.getMessage());
				logger.warn("", e1);
				response.setMessage("NUKE failed");
				return response;
			}
			// nukees contains credits as value
			if (user == null) {
				Long add = (Long) nukees2.get(null);
				if (add == null) {
					add = new Long(0);
				}
				nukees2.put(
					user,
					new Long(
						add.longValue()
							+ ((Long) nukees.get(username)).longValue()));
			} else {
				nukees2.put(user, nukees.get(username));
			}
		}
		//rename
		String toDir;
		String toName = "[NUKED]-" + nukeDir.getName();
		try {
			toDir = nukeDir.getParentFile().getPath();
		} catch (FileNotFoundException ex) {
			logger.fatal("", ex);
			return FtpReply.RESPONSE_553_REQUESTED_ACTION_NOT_TAKEN;
		}
		try {
			nukeDir.renameTo(toDir, toName);
		} catch (IOException ex) {
			logger.warn("", ex);
			response.addComment(
				" cannot rename to \""
					+ toDir
					+ "/"
					+ toName
					+ "\": "
					+ ex.getMessage());
			response.setCode(500);
			response.setMessage("NUKE failed");
			return response;
		}

		long nukeDirSize = 0;
		long nukedAmount = 0;

		//update credits, nukedbytes, timesNuked, lastNuked
		for (Iterator iter = nukees2.keySet().iterator(); iter.hasNext();) {
			AbstractUser nukee = (AbstractUser) iter.next();
			if (nukee == null)
				continue;
			Long size = (Long) nukees2.get(nukee);
			Long debt =
				new Long(
					(long) (size.longValue() * nukee.getRatio()
						+ size.longValue() * (multiplier - 1)));
			nukedAmount += debt.longValue();
			nukeDirSize += size.longValue();
			nukee.updateCredits(-debt.longValue());
			nukee.updateNukedBytes(debt.longValue());
			nukee.updateTimesNuked(1);
			nukee.setLastNuked(System.currentTimeMillis());
			try {
				nukee.commit();
			} catch (UserFileException e1) {
				response.addComment(
					"Error writing userfile: " + e1.getMessage());
				logger.log(Level.WARN, "Error writing userfile", e1);
			}
			response.addComment(
				nukee.getUsername()
					+ " -"
					+ Bytes.formatBytes(debt.longValue()));
		}
		NukeEvent nuke =
			new NukeEvent(
				conn.getUserNull(),
				"NUKE",
				nukeDirPath,
				nukeDirSize,
				nukedAmount,
				multiplier,
				reason,
				nukees);
		getNukeLog().add(nuke);
		conn.getConnectionManager().dispatchFtpEvent(nuke);
		return response;
	}
	public FtpReply doSITE_NUKES(BaseFtpConnection conn) {
		FtpReply response = (FtpReply) FtpReply.RESPONSE_200_COMMAND_OK.clone();
		for (Iterator iter = getNukeLog().getAll().iterator();
			iter.hasNext();
			) {
			response.addComment(iter.next());
		}
		return response;
	}

	/**
	 * USAGE: site unnuke <directory> <message>
	 * 	Unnuke a directory.
	 * 
	 * 	ex. site unnuke shit NOT CRAP
	 * 	
	 * 	This will unnuke the directory 'shit' with the comment 'NOT CRAP'.
	 * 
	 *         NOTE: You can enclose the directory in braces if you have spaces in the name
	 *         ex. site unnuke {My directory name} justcause
	 * 
	 * 	You need to configure glftpd to keep nuked files if you want to unnuke.
	 * 	See the section about glftpd.conf.
	 */
	public FtpReply doSITE_UNNUKE(BaseFtpConnection conn) {
		conn.resetState();

		StringTokenizer st =
			new StringTokenizer(conn.getRequest().getArgument());
		if (!st.hasMoreTokens()) {
			return FtpReply.RESPONSE_501_SYNTAX_ERROR;
		}

		String toName = st.nextToken();
		String toPath = conn.getCurrentDirectory().getPath() + "/" + toName;
		String toDir = conn.getCurrentDirectory().getPath();
		String nukeName = "[NUKED]-" + toName;

		String reason;
		if (st.hasMoreTokens()) {
			reason = st.nextToken("");
		} else {
			reason = "";
		}

		LinkedRemoteFile nukeDir;
		try {
			nukeDir = conn.getCurrentDirectory().getFile(nukeName);
		} catch (FileNotFoundException e2) {
			return new FtpReply(
				200,
				nukeName + " doesn't exist: " + e2.getMessage());
		}

		FtpReply response = (FtpReply) FtpReply.RESPONSE_200_COMMAND_OK.clone();
		NukeEvent nuke;
		try {
			nuke = getNukeLog().get(toPath);
		} catch (ObjectNotFoundException ex) {
			response.addComment(ex.getMessage());
			return response;
		}

		//Map nukees2 = new Hashtable();
		for (Iterator iter = nuke.getNukees().entrySet().iterator();
			iter.hasNext();
			) {
			Map.Entry entry = (Map.Entry) iter.next();
			String nukeeName = (String) entry.getKey();
			Long amount = (Long) entry.getValue();
			User nukee;
			try {
				nukee = conn.getUserManager().getUserByName(nukeeName);
			} catch (NoSuchUserException e) {
				response.addComment(nukeeName + ": no such user");
				continue;
			} catch (UserFileException e) {
				response.addComment(nukeeName + ": error reading userfile");
				logger.log(Level.FATAL, "error reading userfile", e);
				continue;
			}

			nukee.updateCredits(amount.longValue());
			nukee.updateTimesNuked(1);
			try {
				nukee.commit();
			} catch (UserFileException e3) {
				logger.log(
					Level.FATAL,
					"Eroror saveing userfile for " + nukee.getUsername(),
					e3);
				response.addComment(
					"Error saving userfile for " + nukee.getUsername());
			}

			response.addComment(nukeeName + ": restored " + Bytes.formatBytes(amount.longValue()) + "bytes");
		}
		try {
			getNukeLog().remove(toPath);
		} catch (ObjectNotFoundException e) {
			response.addComment("Error removing nukelog entry");
		}
		try {
			nukeDir.renameTo(toDir, toName);
		} catch (ObjectExistsException e1) {
			response.addComment(
				"Error renaming nuke, target dir already exists");
		} catch (IOException e1) {
			response.addComment("Error: " + e1.getMessage());
			logger.log(
				Level.FATAL,
				"Illegaltargetexception: means parent doesn't exist",
				e1);
		}
		nuke.setCommand("UNNUKE");
		nuke.setReason(reason);
		conn.getConnectionManager().dispatchFtpEvent(nuke);
		return response;
	}

	/**
	 * 
	 */
	private NukeLog getNukeLog() {
		return _nukelog;
	}

//	private static final ArrayList handledCommands = new ArrayList();
//	static {
//		handledCommands.add("SITE NUKE");
//		handledCommands.add("SITE NUKES");
//		handledCommands.add("SITE UNNUKE");
//	}
	public CommandHandler initialize(
		BaseFtpConnection conn,
		CommandManager initializer) {
		return this;
	}

	public String[] getFeatReplies() {
		return null;
	}


	public void load(CommandManagerFactory initializer) {
		_nukelog = new NukeLog();
		try {
			Document doc =
				new SAXBuilder().build(new FileReader("nukelog.xml"));
			List nukes = doc.getRootElement().getChildren();
			for (Iterator iter = nukes.iterator(); iter.hasNext();) {
				Element nukeElement = (Element) iter.next();

				User user =
					initializer.getConnectionManager().getUserManager().getUserByName(nukeElement.getChildText("user"));
				String directory = nukeElement.getChildText("path");
				long time = Long.parseLong(nukeElement.getChildText("time"));
				int multiplier =
					Integer.parseInt(nukeElement.getChildText("multiplier"));
				String reason = nukeElement.getChildText("reason");

				long size = Long.parseLong(nukeElement.getChildText("size"));
				long nukedAmount =
					Long.parseLong(nukeElement.getChildText("nukedAmount"));

				Map nukees = new Hashtable();
				List nukeesElement =
					nukeElement.getChild("nukees").getChildren("nukee");
				for (Iterator iterator = nukeesElement.iterator();
					iterator.hasNext();
					) {
					Element nukeeElement = (Element) iterator.next();
					String nukeeUsername =
						nukeeElement.getChildText("username");
					Long nukeeAmount =
						new Long(nukeeElement.getChildText("amount"));

					nukees.put(nukeeUsername, nukeeAmount);
				}
				_nukelog.add(
					new NukeEvent(
						user,
						"NUKE",
						directory,
						time,
						size,
						nukedAmount,
						multiplier,
						reason,
						nukees));
			}
		} catch (FileNotFoundException ex) {
			logger.log(
				Level.DEBUG,
				"nukelog.xml not found, will create it after first nuke.");
		} catch (Exception ex) {
			logger.log(
				Level.INFO,
				"Error loading nukelog from nukelog.xml",
				ex);
		}
	}

}
