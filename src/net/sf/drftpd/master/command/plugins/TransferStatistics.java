package net.sf.drftpd.master.command.plugins;

import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.StringTokenizer;

import net.sf.drftpd.Bytes;
import net.sf.drftpd.master.BaseFtpConnection;
import net.sf.drftpd.master.FtpReply;
import net.sf.drftpd.master.FtpRequest;
import net.sf.drftpd.master.command.CommandHandler;
import net.sf.drftpd.master.command.CommandManager;
import net.sf.drftpd.master.command.CommandManagerFactory;
import net.sf.drftpd.master.command.UnhandledCommandException;
import net.sf.drftpd.master.config.FtpConfig;
import net.sf.drftpd.master.config.Permission;
import net.sf.drftpd.master.usermanager.NoSuchUserException;
import net.sf.drftpd.master.usermanager.User;
import net.sf.drftpd.master.usermanager.UserFileException;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

public class TransferStatistics implements CommandHandler {

	private static Logger logger = Logger.getLogger(TransferStatistics.class);
	public FtpReply execute(BaseFtpConnection conn)
		throws UnhandledCommandException {
		FtpRequest request = conn.getRequest();
		if (request.getCommand().equals("SITE STATS"))
			return doSITE_STATS(conn);
		List users;
		try {
			users = conn.getUserManager().getAllUsers();
		} catch (UserFileException e) {
			logger.warn("", e);
			return new FtpReply(200, "IO error: " + e.getMessage());
		}
		int count = 10; // default # of users to list
		request = conn.getRequest();
		if (request.hasArgument()) {
			StringTokenizer st = new StringTokenizer(request.getArgument());

			try {
				count = Integer.parseInt(st.nextToken());
			} catch (NumberFormatException ex) {
				st = new StringTokenizer(request.getArgument());
			}

			Permission perm = null;
			perm = new Permission(FtpConfig.makeUsers(st));
			for (Iterator iter = users.iterator(); iter.hasNext();) {
				User user = (User) iter.next();
				if (!perm.check(user))
					iter.remove();
			}
		}
		final String command = request.getCommand();
		Collections.sort(users, new UserComparator(request));

		FtpReply response = new FtpReply(200);
		int i = 0;
		for (Iterator iter = users.iterator(); iter.hasNext();) {
			if (++i > count)
				break;
			User user = (User) iter.next();
			response.addComment(
				user.getUsername()
					+ " "
					+ Bytes.formatBytes(getStats(command, user)));
			//			// AL MONTH WK DAY
			//			String period =
			//				command.substring("SITE ".length(), command.length() - 2);
			//			// UP DN
			//			String updn = command.substring(command.length() - 2);
			//			if (updn.equals("UP")) {
			//				if (period.equals("AL"))
			//					response.addComment(
			//						user.getUsername()
			//							+ " "
			//							+ Bytes.formatBytes(user.getUploadedBytes()));
			//				if (period.equals("DAY"))
			//					response.addComment(
			//						user.getUsername()
			//							+ " "
			//							+ Bytes.formatBytes(user.getUploadedBytesDay()));
			//				if (period.equals("WK"))
			//					response.addComment(
			//						user.getUsername()
			//							+ " "
			//							+ Bytes.formatBytes(user.getUploadedBytesWeek()));
			//				if (period.equals("MONTH"))
			//					response.addComment(
			//						user.getUsername()
			//							+ " "
			//							+ Bytes.formatBytes(user.getUploadedBytesMonth()));
			//			} else if (updn.equals("DN")) {
			//				if (period.equals("AL"))
			//					response.addComment(
			//						user.getUsername()
			//							+ " "
			//							+ Bytes.formatBytes(user.getDownloadedBytes()));
			//				if (period.equals("DAY"))
			//					response.addComment(
			//						user.getUsername()
			//							+ " "
			//							+ Bytes.formatBytes(user.getDownloadedBytesDay()));
			//				if (period.equals("WK"))
			//					response.addComment(
			//						user.getUsername()
			//							+ " "
			//							+ Bytes.formatBytes(user.getDownloadedBytesWeek()));
			//				if (period.equals("MONTH"))
			//					response.addComment(
			//						user.getUsername()
			//							+ " "
			//							+ Bytes.formatBytes(user.getDownloadedBytesMonth()));
		}
		return response;
	}

	/**
	 * USAGE: site stats [<user>]
	 *	Display a user's upload/download statistics.
	 */
	public FtpReply doSITE_STATS(BaseFtpConnection conn) {
		FtpRequest request = conn.getRequest();

		if (!request.hasArgument()) {
			return FtpReply.RESPONSE_501_SYNTAX_ERROR;
		}

		User user;
		if (!request.hasArgument()) {
			user = conn.getUserNull();
		} else {
			try {
				user =
					conn.getUserManager().getUserByName(request.getArgument());
			} catch (NoSuchUserException e) {
				return new FtpReply(200, "No such user: " + e.getMessage());
			} catch (UserFileException e) {
				logger.log(Level.WARN, "", e);
				return new FtpReply(200, e.getMessage());
			}
		}

		if (conn.getUserNull().isGroupAdmin()
			&& !conn.getUserNull().getGroupName().equals(user.getGroupName())) {
			return FtpReply.RESPONSE_530_ACCESS_DENIED;
		} else if (
			!conn.getUserNull().isAdmin()
				&& !user.equals(conn.getUserNull())) {
			return FtpReply.RESPONSE_530_ACCESS_DENIED;
		}

		FtpReply response = new FtpReply(200);
		response.addComment("bytes up, files up, bytes down, files down");
		response.addComment(
			"total: "
				+ Bytes.formatBytes(user.getUploadedBytes())
				+ " "
				+ user.getUploadedFiles()
				+ "f "
				+ Bytes.formatBytes(user.getDownloadedBytes())
				+ " "
				+ user.getDownloadedFiles()
				+ "f ");
		response.addComment(
			"month: "
				+ Bytes.formatBytes(user.getUploadedBytesMonth())
				+ " "
				+ user.getUploadedFilesMonth()
				+ "f "
				+ Bytes.formatBytes(user.getDownloadedBytesMonth())
				+ " "
				+ user.getDownloadedFilesMonth()
				+ "f ");
		response.addComment(
			"week: "
				+ Bytes.formatBytes(user.getUploadedBytesWeek())
				+ " "
				+ user.getUploadedFilesWeek()
				+ "f "
				+ Bytes.formatBytes(user.getDownloadedBytesWeek())
				+ "b "
				+ user.getDownloadedFilesWeek()
				+ "f ");
		response.addComment(
			"day: "
				+ Bytes.formatBytes(user.getUploadedBytesDay())
				+ "b "
				+ user.getUploadedFilesDay()
				+ "f "
				+ Bytes.formatBytes(user.getDownloadedBytesDay())
				+ "b "
				+ user.getDownloadedFilesDay()
				+ "f ");
		return response;
	}

	static long getStats(String command, User user) {
		// AL MONTH WK DAY
		String period =
			command.substring("SITE ".length(), command.length() - 2);
		// UP DN
		String updn = command.substring(command.length() - 2);
		System.out.println("updn = " + updn);
		System.out.println("period = " + period);
		if (updn.equals("UP")) {
			if (period.equals("AL"))
				return user.getUploadedBytes();
			if (period.equals("DAY"))
				return user.getUploadedBytesDay();
			if (period.equals("WK"))
				return user.getUploadedBytesWeek();
			if (period.equals("MONTH"))
				return user.getUploadedBytesMonth();
		} else if (updn.equals("DN")) {
			if (period.equals("AL"))
				return user.getDownloadedBytes();
			if (period.equals("DAY"))
				return user.getDownloadedBytesDay();
			if (period.equals("WK"))
				return user.getDownloadedBytesWeek();
			if (period.equals("MONTH"))
				return user.getDownloadedBytesMonth();
		}
		throw new RuntimeException(
			UnhandledCommandException.create(
				TransferStatistics.class,
				command));
	}
	public CommandHandler initialize(
		BaseFtpConnection conn,
		CommandManager initializer) {
		return this;
	}

	public String[] getFeatReplies() {
		return null;
	}

	public void unload() {
	}
	public void load(CommandManagerFactory initializer) {
	}

}
class UserComparator implements Comparator {
	private FtpRequest _request;
	private String _command;
	public UserComparator(FtpRequest request) {
		_request = request;
		_command = _request.getCommand();
	}

	public int compare(Object o1, Object o2) {
		User u1 = (User) o1;
		User u2 = (User) o2;

		long thisVal = TransferStatistics.getStats(_command, u1);
		long anotherVal = TransferStatistics.getStats(_command, u2);
		return (thisVal > anotherVal ? -1 : (thisVal == anotherVal ? 0 : 1));
	}

}