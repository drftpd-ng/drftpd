package net.sf.drftpd.master.command;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.sf.drftpd.ObjectNotFoundException;
import net.sf.drftpd.master.BaseFtpConnection;
import net.sf.drftpd.master.FtpReply;

import org.apache.log4j.Logger;

/**
 * @author mog
 * @version $Id: CommandManager.java,v 1.5 2004/02/03 11:00:11 mog Exp $
 */
public class CommandManager {
	//TODO reload me

	private static final Logger logger = Logger.getLogger(CommandManager.class);

	private CommandManagerFactory _factory;
	/**
	 * String => CommandHandler
	 */
	private Map commands = new Hashtable();
	/**
	 * Class => CommandHandler
	 */
	private Hashtable hnds = new Hashtable();

	public CommandManager(
		BaseFtpConnection conn,
		CommandManagerFactory initializer) {
		_factory = initializer;
		for (Iterator iter = _factory.getHandlersMap().entrySet().iterator();
			iter.hasNext();
			) {
			Map.Entry entry = (Map.Entry) iter.next();
			hnds.put(
				entry.getKey(),
				((CommandHandler) entry.getValue()).initialize(conn, this));
		}
		for (Iterator iter = _factory.getCommandsMap().entrySet().iterator();
			iter.hasNext();
			) {
			Map.Entry entry = (Map.Entry) iter.next();
			commands.put(
				(String) entry.getKey(),
				(CommandHandler) hnds.get((Class) entry.getValue()));
		}
	}

	public FtpReply execute(BaseFtpConnection conn)
		throws UnhandledCommandException {
		String command = conn.getRequest().getCommand();
		CommandHandler handler = (CommandHandler) commands.get(command);
		if (handler == null) {
			throw new UnhandledCommandException(
				"No command handler for " + command);
		}
		return handler.execute(conn);
	}

	public CommandHandler getCommandHandler(Class clazz)
		throws ObjectNotFoundException {
		CommandHandler ret = (CommandHandler) hnds.get(clazz);
		if (ret == null)
			throw new ObjectNotFoundException();
		return ret;
	}

	public List getHandledCommands(Class class1) {
		ArrayList list = new ArrayList();
		for (Iterator iter = commands.entrySet().iterator(); iter.hasNext();) {
			Map.Entry element = (Map.Entry) iter.next();
			if (element.getValue().getClass().equals(class1)) {
				list.add((String) element.getKey());
			}
		}
		return list;
	}

	/**
	 * Class => CommandHandler
	 */
	public Map getCommandHandlersMap() {
		return hnds;
	}
}
