/*
 * Created on 2004-mar-31
 *
 * To change the template for this generated file go to
 * Window - Preferences - Java - Code Generation - Code and Comments
 */
package org.drftpd.plugins;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import net.sf.drftpd.event.irc.IRCPluginInterface;

import org.apache.log4j.Logger;

import f00f.net.irc.martyr.GenericAutoService;
import f00f.net.irc.martyr.InCommand;
import f00f.net.irc.martyr.State;
import f00f.net.irc.martyr.commands.RawCommand;

/**
 * @author mog
 * @version $Id: OnConnect.java,v 1.1 2004/04/17 02:24:38 mog Exp $
 */
public class OnConnect extends GenericAutoService implements IRCPluginInterface {

	private static final Logger logger = Logger.getLogger(OnConnect.class);

	public OnConnect(SiteBot listener) throws IOException {
		super(listener.getIRCConnection());
		if(!new File("conf/irc-onconnect.conf").canRead()) throw new IOException("conf/irc-onconnect.conf unreadable");
	}

	protected void updateState(State state) {
		if(state == State.REGISTERED) {
			try {
				BufferedReader in = new BufferedReader(new FileReader("conf/irc-onconnect.conf"));
				String line;
				while((line = in.readLine()) != null) {
					line.replaceAll("\\$me", getConnection().getClientState().getNick().getNick());
					getConnection().sendCommand(new RawCommand(line));
				}
			} catch (IOException e) {
				logger.warn("", e);
			}
		}
	}

	protected void updateCommand(InCommand command) {
	}

	public String getCommands() {
		return null;
	}
}
