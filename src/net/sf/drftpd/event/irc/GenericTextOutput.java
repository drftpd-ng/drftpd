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

package net.sf.drftpd.event.irc;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Iterator;

import net.sf.drftpd.master.command.plugins.Textoutput;

import org.drftpd.plugins.SiteBot;

import f00f.net.irc.martyr.GenericCommandAutoService;
import f00f.net.irc.martyr.InCommand;
import f00f.net.irc.martyr.commands.MessageCommand;
/**
 * @author zubov
 * @version $Id: GenericTextOutput.java,v 1.2 2004/07/09 17:08:36 zubov Exp $
 */
public class GenericTextOutput extends GenericCommandAutoService
		implements
			IRCPluginInterface {
	private HashMap _commands;
	public GenericTextOutput(SiteBot ircListener) {
		super(ircListener.getIRCConnection());
		reload();
	}
	public String getCommands() {
		String toReturn = new String();
		for (Iterator iter = _commands.keySet().iterator(); iter.hasNext();) {
			toReturn = toReturn + (String) iter.next() + " ";
		}
		return toReturn.trim();
	}
	private void reload() {
		_commands = new HashMap();
		BufferedReader in;
		try {
			in = new BufferedReader(new InputStreamReader(new FileInputStream(
					"conf/generictextoutput.conf")));
		} catch (FileNotFoundException e) {
			throw new RuntimeException(
					"conf/generictextoutput.conf could not be opened", e);
		}
		String line;
		try {
			while ((line = in.readLine()) != null) {
				if (line.startsWith("#"))
					continue;
				String[] args = line.split(" ");
				if (args.length != 2)
					continue;
				_commands.put(args[0], args[1]);
			}
		} catch (IOException e1) {
			throw new RuntimeException(e1);
		}
	}
	protected void updateCommand(InCommand command) {
		if (command instanceof MessageCommand) {
			MessageCommand msgc = (MessageCommand) command;
			String msg = msgc.getMessage();
			for (Iterator iter = _commands.keySet().iterator(); iter.hasNext();) {
				String trigger = (String) iter.next();
				if (msg.startsWith(trigger)) {
					if (!msgc.isPrivateToUs(getConnection().getClientState())) {
						Textoutput.sendTextToIRC(getConnection(), msgc
								.getDest(), (String) _commands.get(trigger));
					} else {
						return; // already matched a trigger
					}
				}
			}
		}
	}
}