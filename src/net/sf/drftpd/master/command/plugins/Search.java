package net.sf.drftpd.master.command.plugins;

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;

import net.sf.drftpd.master.BaseFtpConnection;
import net.sf.drftpd.master.FtpReply;
import net.sf.drftpd.master.FtpRequest;
import net.sf.drftpd.master.command.CommandHandler;
import net.sf.drftpd.master.command.CommandManager;
import net.sf.drftpd.master.command.CommandManagerFactory;
import net.sf.drftpd.remotefile.LinkedRemoteFile;

/**
 * @author mog
 * @version $Id: Search.java,v 1.6 2004/01/13 01:40:56 mog Exp $
 */
public class Search implements CommandHandler {
	public void unload() {}
	public void load(CommandManagerFactory initializer) {}

	private static void findFile(
		BaseFtpConnection conn,
		FtpReply response,
		LinkedRemoteFile dir,
		Collection searchstrings,
		boolean files,
		boolean dirs) {
		//TODO optimize me, checking using regexp for all dirs is possibly slow 
		if (conn.getConfig().checkPrivPath(conn.getUserNull(), dir))
			return;
		for (Iterator iter = dir.getFiles().iterator(); iter.hasNext();) {
			LinkedRemoteFile file = (LinkedRemoteFile) iter.next();
			if (file.isDirectory()) {
				findFile(conn, response, file, searchstrings, files, dirs);
			}
			if (dirs && file.isDirectory() || files && file.isFile()) {
				for (Iterator iterator = searchstrings.iterator();
					iterator.hasNext();
					) {
					if(response.size() >= 100) return;
					String searchstring = (String) iterator.next();
					if (file.getName().toLowerCase().indexOf(searchstring)
						!= -1) {
						response.addComment(file.getPath());
						if(response.size() >= 100) {
							response.addComment("<snip>");
							return;
						} 
						break;
					}
				}
			}
		}
	}

	public FtpReply execute(BaseFtpConnection conn) {
		FtpRequest request = conn.getRequest();
		if (!request.hasArgument()) {
			return FtpReply.RESPONSE_501_SYNTAX_ERROR;
		}
		String args[] = request.getArgument().split(" ");
		if (args.length == 0) {
			return FtpReply.RESPONSE_501_SYNTAX_ERROR;
		}
		Collection searchstrings = Arrays.asList(args);
		FtpReply response = (FtpReply) FtpReply.RESPONSE_200_COMMAND_OK.clone();
		findFile(
			conn,
			response,
			conn.getCurrentDirectory(),
			searchstrings,
			"SITE DUPE".equals(request.getCommand()),
			"SITE SEARCH".equals(request.getCommand()));
		return response;
	}

	public CommandHandler initialize(BaseFtpConnection conn, CommandManager initializer) {
		return this;
	}

	public String[] getFeatReplies() {
		return null;
	}

}
