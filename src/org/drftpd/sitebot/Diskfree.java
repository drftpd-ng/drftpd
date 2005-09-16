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
package org.drftpd.sitebot;

import java.util.ArrayList;

import net.sf.drftpd.util.ReplacerUtils;

import org.drftpd.plugins.SiteBot;
import org.drftpd.slave.SlaveStatus;
import org.tanesha.replacer.ReplacerEnvironment;

import f00f.net.irc.martyr.commands.MessageCommand;


/**
 * @author zubov
 * @version $Id$
 */
public class Diskfree extends IRCCommand {

    public Diskfree() {
		super();
    }
    
	public ArrayList<String> doDiskfree(String args, MessageCommand msgc) {
	    ArrayList<String> out = new ArrayList<String>();
		ReplacerEnvironment env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);
		SlaveStatus status = getGlobalContext().getSlaveManager().getAllStatus();
		SiteBot.fillEnvSlaveStatus(env, status, getGlobalContext().getSlaveManager());
		out.add(ReplacerUtils.jprintf("diskfree", env, SiteBot.class));
		return out;
	}
}
