/*
 * Created on 2003-okt-16
 *
 * To change the template for this generated file go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
package net.sf.drftpd.master.command.plugins;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;

import net.sf.drftpd.master.BaseFtpConnection;
import net.sf.drftpd.master.FtpReply;
import net.sf.drftpd.master.command.CommandHandler;
import net.sf.drftpd.master.command.CommandManager;
import net.sf.drftpd.master.command.UnhandledCommandException;
import net.sf.drftpd.remotefile.LinkedRemoteFile;

/**
 * @author mog
 *
 * To change the template for this generated type comment go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
public class SiteList implements CommandHandler {

	public FtpReply execute(BaseFtpConnection conn)
		throws UnhandledCommandException {
		conn.resetState();
		FtpReply response = (FtpReply) FtpReply.RESPONSE_200_COMMAND_OK.clone();
		//Map files = currentDirectory.getMap();
		ArrayList files = new ArrayList(conn.getCurrentDirectory().getFiles());
		Collections.sort(files);
		for (Iterator iter = files.iterator(); iter.hasNext();) {
			LinkedRemoteFile file = (LinkedRemoteFile) iter.next();
			//if (!key.equals(file.getName()))
			//	response.addComment(
			//		"WARN: " + key + " not equals to " + file.getName());
			//response.addComment(key);
			response.addComment(file.toString());
		}
		return response;
	}

	/* (non-Javadoc)
	 * @see net.sf.drftpd.master.command.CommandHandler#initialize(net.sf.drftpd.master.BaseFtpConnection)
	 */
	public CommandHandler initialize(BaseFtpConnection conn, CommandManager initializer) {
		return this;
	}

	public String[] getFeatReplies() {
		return null;
	}

}
