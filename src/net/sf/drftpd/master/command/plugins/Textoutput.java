/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 * 
 * DrFTPD is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 * 
 * DrFTPD is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with DrFTPD; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package net.sf.drftpd.master.command.plugins;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;

import org.tanesha.replacer.FormatterException;
import org.tanesha.replacer.ReplacerEnvironment;
import org.tanesha.replacer.SimplePrintf;

import f00f.net.irc.martyr.IRCConnection;
import f00f.net.irc.martyr.commands.MessageCommand;

import net.sf.drftpd.event.irc.IRCListener;
import net.sf.drftpd.master.BaseFtpConnection;
import net.sf.drftpd.master.FtpReply;
import net.sf.drftpd.master.command.CommandHandler;
import net.sf.drftpd.master.command.CommandManager;
import net.sf.drftpd.master.command.CommandManagerFactory;
import net.sf.drftpd.master.command.UnhandledCommandException;

/**
 * @author mog
 * @version $Id: Textoutput.java,v 1.8 2004/03/01 22:31:38 zubov Exp $
 */
public class Textoutput implements CommandHandler {

	public static void addTextToResponse(FtpReply reply, String file)
		throws FileNotFoundException, IOException {
		reply.addComment(
			new BufferedReader(
				new InputStreamReader(
					new FileInputStream("text/" + file + ".txt"),
					"ISO-8859-1")));
	}

	public static void sendTextToIRC(
		IRCConnection conn,
		String destination,
		String file) {
		BufferedReader fileReader = null;
		try {
			fileReader =
				new BufferedReader(
					new InputStreamReader(
						new FileInputStream("text/" + file + ".txt")));
			while (fileReader.ready()) {
				String line = fileReader.readLine();
				ReplacerEnvironment env = new ReplacerEnvironment(IRCListener.GLOBAL_ENV);
				try {
					conn.sendCommand(new MessageCommand(destination,SimplePrintf.jprintf(line,env)));
				} catch (FormatterException e1) {
					conn.sendCommand(new MessageCommand(destination,"Error in formatting of line - " + line));
				}
			}
		} catch (IOException e) {
			conn.sendCommand(
				new MessageCommand(
					destination,
					"IOException opening text/" + file + ".txt"));
			return;
		}
	}

	public FtpReply execute(BaseFtpConnection conn)
		throws UnhandledCommandException {
		conn.resetState();

		if (conn.getRequest().hasArgument()) {
			return FtpReply.RESPONSE_501_SYNTAX_ERROR;
		}

		try {
			FtpReply reply = new FtpReply(200);
			addTextToResponse(
				reply,
				conn
					.getRequest()
					.getCommand()
					.substring("SITE ".length())
					.toLowerCase());
			return reply;
		} catch (IOException e) {
			return new FtpReply(200, "IO Error: " + e.getMessage());
		}
	}

	public String[] getFeatReplies() {
		return null;
	}
	public CommandHandler initialize(
		BaseFtpConnection conn,
		CommandManager initializer) {
		return this;
	}
	public void load(CommandManagerFactory initializer) {
	}
	public void unload() {
	}
}
