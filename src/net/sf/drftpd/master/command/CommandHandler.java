/*
 * Created on 2003-okt-16
 *
 * To change the template for this generated file go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
package net.sf.drftpd.master.command;

import net.sf.drftpd.master.BaseFtpConnection;
import net.sf.drftpd.master.FtpReply;

/**
 * @author mog
 *
 * To change the template for this generated type comment go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
public interface CommandHandler {
	public abstract FtpReply execute(BaseFtpConnection conn)
		throws UnhandledCommandException;
	public abstract CommandHandler initialize(BaseFtpConnection conn, CommandManager initializer);
	public String[] getFeatReplies();
	public void load(CommandManagerFactory initializer);
	public void unload();
}
