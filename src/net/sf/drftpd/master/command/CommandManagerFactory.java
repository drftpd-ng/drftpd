package net.sf.drftpd.master.command;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

import net.sf.drftpd.FatalException;
import net.sf.drftpd.ObjectExistsException;
import net.sf.drftpd.ObjectNotFoundException;
import net.sf.drftpd.master.BaseFtpConnection;

import org.apache.log4j.Logger;

/**
 * @author mog
 *
 * Istantiates the CommandManager instances that holds per-connection CommandHandlers.
 */
public class CommandManagerFactory {
	//TODO reload me
	private Logger logger = Logger.getLogger(CommandManagerFactory.class);
	/**
	 * Class => CommandHandler
	 */
	private Hashtable hnds = new Hashtable();
	/**
	 * String=> Class
	 */
	private Hashtable cmds = new Hashtable();

	public CommandManagerFactory() {
		//		Login login = new Login();
		//		handlers = new Hashtable();
		//		handlers.put("USER", login);
		//		handlers.put("PASS", login);
//		handlers = new ArrayList();
//		handlers.add(new Login());
//		handlers.add(new Dir());
//		handlers.add(new List());
//		handlers.add(new DataConnectionHandler());
//		handlers.add(new Search());
//		handlers.add(new UserManagment());
		//_conn = conn;
		//login.init(conn);
		//Hashtable handlers = new Hashtable();
		
		Properties props = new Properties();
		try {
			props.load(new FileInputStream("commandhandlers.conf"));
		} catch (IOException e) {
			throw new FatalException("Error loading commandhandlers.conf", e);
		}
		
		for (Iterator iter = props.entrySet().iterator(); iter.hasNext();) {
			try {
			Map.Entry entry = (Map.Entry) iter.next();
			Class hndclass = Class.forName((String)entry.getValue());
			CommandHandler hndinstance = (CommandHandler)hnds.get(hndclass);
			if(hndinstance == null) {
				hndinstance = (CommandHandler)hndclass.newInstance();
				hnds.put(hndclass, hndinstance);
			}
			String cmd = (String)entry.getKey();
			if(cmds.containsKey(cmd)) throw new ObjectExistsException(cmd+" is already mapped");
			cmds.put(cmd, hndclass);
			} catch(Exception e) {
				throw new FatalException("", e);
			}
		}
	}

	public CommandManager initialize(BaseFtpConnection conn) {
		CommandManager mgr = new CommandManager(conn, this);
		return mgr;
	}
	
	/**
	 * Class => CommandHandler
	 */
	public Hashtable getHandlersMap() {
		return hnds;
	}
	
	/**
	 * String=> Class
	 */
	public Hashtable getCommandsMap() {
		return cmds;
	}
	public CommandHandler getHandler(Class clazz) throws ObjectNotFoundException {
		CommandHandler ret = (CommandHandler)hnds.get(clazz);
		if(ret == null) throw new ObjectNotFoundException();
		return ret;
//		for (Iterator iter = hnds.iterator(); iter.hasNext();) {
//			CommandHandler handler = (CommandHandler) iter.next();
//			if (handler.getClass().equals(clazz))
//				return handler;
//		}
//		throw new ObjectNotFoundException();
	}
}
