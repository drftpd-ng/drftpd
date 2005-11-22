/*
*
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
package net.drmods.plugins.irc;

import java.util.ArrayList;
import java.util.Iterator;

import net.sf.drftpd.util.ReplacerUtils;

import org.apache.log4j.Logger;
import org.drftpd.Bytes;
import org.drftpd.GlobalContext;
import org.drftpd.plugins.SiteBot;
import org.drftpd.sitebot.IRCCommand;
import org.drftpd.usermanager.User;
import org.drftpd.usermanager.UserFileException;
import org.tanesha.replacer.ReplacerEnvironment;

import f00f.net.irc.martyr.commands.MessageCommand;
import f00f.net.irc.martyr.util.FullNick;

/**
 * @author Teflon
 * @version $Id$
 */
public class Credits extends IRCCommand {
	private static final Logger logger = Logger.getLogger(Credits.class);

	public Credits(GlobalContext gctx) {
		super(gctx);
	}

	public ArrayList<String> doCredits(String args, MessageCommand msgc) {
	    ArrayList<String> out = new ArrayList<String>();
        ReplacerEnvironment env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);
		env.add("ircnick", msgc.getSource().getNick());
		
		FullNick fn = msgc.getSource();
		String ident = fn.getNick() + "!" + fn.getUser() + "@" + fn.getHost();
		User user;	
	    if (args.equals("")) {
	     	try {
	     	    user = getGlobalContext().getUserManager().getUserByIdent(ident);
	     	} catch (Exception e) {
	     	    logger.warn("Could not identify " + ident);
	     	    out.add(ReplacerUtils.jprintf("ident.noident", env, SiteBot.class));
	     	    return out;
	     	}
	    } else if (args.equals("*")) {
	        showAllUserCredits(out);
	        return out;
	    } else {
	        try {
                user = getGlobalContext().getUserManager().getUserByName(args);
            } catch (Exception e) {
                env.add("user", args);
                out.add(ReplacerUtils.jprintf("credits.error", env, Credits.class));
                return out;
            } 
	    }
		env.add("user", user.getName());
		env.add("credits",Bytes.formatBytes(user.getCredits()));
		out.add(ReplacerUtils.jprintf("credits.user", env, Credits.class));			
	    return out;            
	}
	
	protected void showAllUserCredits(ArrayList<String> out) {
		long totalcredz = 0;
		ReplacerEnvironment env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);
		try {
			ArrayList<User> users = new ArrayList<User>(getGlobalContext().getUserManager().getAllUsers());
			for (Iterator iter = users.iterator(); iter.hasNext();) {
				User user = (User) iter.next();
				totalcredz += user.getCredits();
			}
			env.add("usercount",Integer.toString(users.size()));
			env.add("totalcredits",Bytes.formatBytes(totalcredz));
			out.add(ReplacerUtils.jprintf("credits.total", env, Credits.class));			
		} catch (UserFileException e) {
			logger.warn(e);
		}
	}

}
