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
package net.sf.drftpd.master.command;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

import net.sf.drftpd.FatalException;
import net.sf.drftpd.FileExistsException;
import net.sf.drftpd.master.BaseFtpConnection;

import org.drftpd.commands.CommandHandlerFactory;

/**
 * @author mog
 * 
 * Istantiates the CommandManager instances that holds per-connection
 * CommandHandlers.
 * @version $Id$
 */
public class CommandManagerFactory {
	/**
	 * Class => CommandHandlerFactory
	 */
	private Hashtable<Class, CommandHandlerFactory> _hnds;

	/**
	 * String=> Class
	 */
	private Hashtable<String, Class> _cmds;

	public CommandManagerFactory() {

		// Login login = new Login();
		// handlers = new Hashtable();
		// handlers.put("USER", login);
		// handlers.put("PASS", login);
		// handlers = new ArrayList();
		// handlers.add(new Login());
		// handlers.add(new Dir());
		// handlers.add(new List());
		// handlers.add(new DataConnectionHandler());
		// handlers.add(new Search());
		// handlers.add(new UserManagement());
		// _conn = conn;
		// login.init(conn);
		// Hashtable handlers = new Hashtable();
		load();
	}

	public void reload() {
		unload();
		load();
	}

	private void unload() {
		for (Iterator iter = _hnds.values().iterator(); iter.hasNext();) {
			Object o = iter.next();

			try {
				CommandHandlerFactory c = ((CommandHandlerFactory) o);
				c.unload();
			} catch (ClassCastException e) {
				throw e;
			}
		}
	}

	private void load() {
		Hashtable<String, Class> cmds = new Hashtable<String, Class>();
		Hashtable<Class, CommandHandlerFactory> hnds = new Hashtable<Class, CommandHandlerFactory>();
		Properties props = new Properties();
        FileInputStream stream = null;
        try {
        	stream = new FileInputStream("conf/commandhandlers.conf"); 
            props.load(stream);
		} catch (IOException e) {
			throw new FatalException("Error loading commandhandlers.conf", e);
		} finally {
	    	if(stream != null) {
	    		try {
					stream.close();
				} catch (IOException e) {
				}
	    	}
	    }


		// URLClassLoader classLoader;
		// try {
		// classLoader =
		// URLClassLoader.newInstance(
		// new URL[] {
		// new URL("file:classes/"),
		// new URL("file:lib/log4j-1.2.8.jar")});
		// } catch (MalformedURLException e1) {
		// throw new RuntimeException(e1);
		// }
		for (Iterator iter = props.entrySet().iterator(); iter.hasNext();) {
			try {
				Map.Entry entry = (Map.Entry) iter.next();

				Class hndclass = Class.forName((String) entry.getValue());

				// Class hndclass =
				// Class.forName(
				// (String) entry.getValue(),
				// false,
				// classLoader);
				CommandHandlerFactory hndinstance = hnds.get(hndclass);

				if (hndinstance == null) {
					hndinstance = (CommandHandlerFactory) hndclass
							.newInstance();
					hnds.put(hndclass, hndinstance);
				}

				String cmd = (String) entry.getKey();

				if (cmds.containsKey(cmd)) {
					throw new FileExistsException(cmd + " is already mapped");
				}

				cmds.put(cmd, hndclass);
			} catch (Exception e) {
				throw new FatalException(e);
			}
		}

		_cmds = cmds;
		_hnds = hnds;
	}


	public CommandManager initialize(BaseFtpConnection conn) {
		CommandManager mgr = new CommandManager(conn, this);
		return mgr;
	}

	/**
	 * Class => CommandHandler
	 */
	public Hashtable getHandlersMap() {
		return _hnds;
	}

	/**
	 * String=> Class
	 */
	public Hashtable getCommandsMap() {
		return _cmds;
	}
}
