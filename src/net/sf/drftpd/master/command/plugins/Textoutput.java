package net.sf.drftpd.master.command.plugins;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;

import net.sf.drftpd.master.BaseFtpConnection;
import net.sf.drftpd.master.FtpReply;
import net.sf.drftpd.master.command.CommandHandler;
import net.sf.drftpd.master.command.CommandManager;
import net.sf.drftpd.master.command.CommandManagerFactory;
import net.sf.drftpd.master.command.UnhandledCommandException;

/**
 * @author mog
 *
 * @version $Id: Textoutput.java,v 1.4 2003/12/23 13:38:20 mog Exp $
 */
public class Textoutput implements CommandHandler {
	public void unload() {}
	public void load(CommandManagerFactory initializer) {}

	public FtpReply execute(BaseFtpConnection conn)
		throws UnhandledCommandException {
		conn.resetState();

		if (conn.getRequest().hasArgument()) {
			return FtpReply.RESPONSE_501_SYNTAX_ERROR;
		}

		try {
			FtpReply reply = new FtpReply(200);
			addTextToResponse(reply, conn
			.getRequest()
			.getCommand()
			.substring("SITE ".length())
			.toLowerCase());
			return reply;
//			return new FtpReply(200).addComment(
//				new BufferedReader(
//					new FileReader(
//						"ftp-data/text/"
//							+ conn
//								.getRequest()
//								.getCommand()
//								.substring("SITE ".length())
//								.toLowerCase()
//							+ ".txt")));
		} catch (IOException e) {
			return new FtpReply(200, "IO Error: " + e.getMessage());
		}
	}
	public static void addTextToResponse(FtpReply reply, String file) throws FileNotFoundException, IOException {
		reply.addComment(
		new BufferedReader(
		new InputStreamReader(
				new FileInputStream(
					"ftp-data/text/"
						+ file
						+ ".txt"), "ISO-8859-1")));
//		reply.addComment(
//			new BufferedReader(
//				new FileReader(
//					"ftp-data/text/"
//						+ file
//						+ ".txt")));
	}
	public CommandHandler initialize(BaseFtpConnection conn, CommandManager initializer) {
		return this;
	}

	public String[] getFeatReplies() {
		return null;
	}

}
