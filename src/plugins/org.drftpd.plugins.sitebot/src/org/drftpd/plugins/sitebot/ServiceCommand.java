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
package org.drftpd.plugins.sitebot;

import java.util.ArrayList;
import java.util.StringTokenizer;

import org.drftpd.dynamicdata.Key;
import org.drftpd.master.Session;
import org.drftpd.usermanager.User;
import org.tanesha.replacer.ReplacerEnvironment;

/**
 * @author djb61
 * @version $Id$
 */
@SuppressWarnings("serial")
public class ServiceCommand extends Session {

	public static final Key IDENT = new Key(ServiceCommand.class, "ident",
			String.class);

	public static final Key IRCUSER = new Key(ServiceCommand.class,"ircuser",
			UserDetails.class);

	private SiteBot _bot;

	private ArrayList<OutputWriter> _outputs;

	public ServiceCommand(SiteBot bot, ArrayList<OutputWriter> outputs, UserDetails runningUser, String ident) {
		_bot = bot;
		_outputs = outputs;
		setObject(IRCUSER,runningUser);
		setObject(IDENT,ident);
	}

	public void printOutput(Object o) {
		sendOutput(o);
	}

	public void printOutput(int code, Object o) {
		sendOutput(o);
	}

	private void sendOutput(Object o) {
		StringTokenizer st = new StringTokenizer(o.toString(),"\n");
		while (st.hasMoreTokens()) {
			String token = st.nextToken();
			for (OutputWriter output : _outputs) {
				output.sendMessage(token);
			}
		}
	}

	public String getIdent() {
		return (String) getObject(IDENT, null);
	}

	public UserDetails getIrcUser() {
		return (UserDetails) getObject(IRCUSER, null);
	}

	public SiteBot getBot() {
		return _bot;
	}

	public boolean isSecure() {
		return _bot.getConfig().getBlowfishEnabled();
	}

	@Override
	public ReplacerEnvironment getReplacerEnvironment(
			ReplacerEnvironment env, User user) {
		env = super.getReplacerEnvironment(env, user);
		return ReplacerEnvironment.chain(env, SiteBot.GLOBAL_ENV);
	}
}
