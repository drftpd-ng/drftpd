package net.sf.drftpd.master.command;

import net.sf.drftpd.master.BaseFtpConnection;
import net.sf.drftpd.master.FtpReply;

/**
 * @author mog
 *
 * @version $Id: CommandHandler.java,v 1.3 2003/12/23 13:38:19 mog Exp $
 */
public interface CommandHandler {
	public abstract FtpReply execute(BaseFtpConnection conn)
		throws UnhandledCommandException;
	public abstract CommandHandler initialize(BaseFtpConnection conn, CommandManager initializer);
	public String[] getFeatReplies();
	public void load(CommandManagerFactory initializer);
	public void unload();
}
