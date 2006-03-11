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

package org.drftpd.irc.utils;

import java.io.IOException;
import java.io.LineNumberReader;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.StringTokenizer;
import java.util.TreeMap;

import org.apache.log4j.Logger;
import org.drftpd.sitebot.IRCCommand;

/**
 * Loads all IRCCommand from irccomand.conf and  provides 
 * a method that allow you to get the current method Map.
 * @author fr0w
 */
public class IRCCommands {	
	
	private static final Logger logger = Logger.getLogger(IRCCommands.class);
	
	public static final Class[] methodArgumentClass = new Class[] { String.class, MessageCommand.class };
	
	private TreeMap<String, CommandList> _methodMap;
	
	public IRCCommands(LineNumberReader lr) throws IOException {
		readConf(lr);
	}
	
	private void readConf(LineNumberReader lr) throws IOException {
		// Used to keep a reference of classes that already had been instanciated. 
		HashMap<String, IRCCommand> ircCommands = new HashMap<String, IRCCommand>();

		// new methodMap
		_methodMap = new TreeMap<String, CommandList>();
		
		String line = null;
		while ((line = lr.readLine()) != null) {
			
			// Skip comments and empty lines.
			if (line.startsWith("#") || line.trim().equals("")) {
				continue;
			}
			
			// NEW FORMAT: Trigger Source Method Destination Perms...
			StringTokenizer st = new StringTokenizer(line);
			if (st.countTokens() < 4) {
				logger.error("Line is invalid -- not enough parameters \"" + line + "\"");
				continue;
			}
			
			String trigger = st.nextToken().toLowerCase();
			String source = st.nextToken(); // might contain comma separated values.
			String methodString = st.nextToken();
			String dest = st.nextToken(); // single value
			String permissions = st.nextToken("").trim();
			
			int index = methodString.lastIndexOf(".");
			String className = methodString.substring(0, index);
			methodString = methodString.substring(index + 1);
			Method m = null;
			
			try {
				IRCCommand ircCommand = ircCommands.get(className);
				if (ircCommand == null) {
					ircCommand = (IRCCommand) Class
					.forName(className).getConstructor().newInstance();					
					ircCommands.put(className, ircCommand);
				}
				
				m = ircCommand.getClass().getMethod(methodString, methodArgumentClass);
				
				// Objcet[] = Method, IRCCommand, IRCPermission
				Object[] objArray = new Object[3];
				objArray[IRCCommand.METHOD] = m;
				objArray[IRCCommand.INSTANCE] = ircCommand;
				objArray[IRCCommand.IRCPERMISSION] = new IRCPermission(source, dest, permissions);
				
				CommandList cList = _methodMap.get(trigger);
				if (cList == null) {
					cList = new CommandList();
				}
				
				cList.add(objArray);
				
				_methodMap.put(trigger, cList);
				
			} catch (Exception e) {
				logger.error(
						"Invalid class/method listed in irccommands.conf - "
						+ line, e);
				throw new RuntimeException(e);
			}
		}		
	}
	
	public TreeMap<String, CommandList> getMethodMap() {
		return _methodMap;
	}
}
