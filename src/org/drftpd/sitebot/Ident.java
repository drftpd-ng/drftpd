/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 *
 * DrFTPD is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * DrFTPD; if not, write to the Free Software Foundation, Inc., 59 Temple Place,
 * Suite 330, Boston, MA 02111-1307 USA
 */
package org.drftpd.sitebot;

import java.util.ArrayList;
import java.util.StringTokenizer;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.drftpd.GlobalContext;
import org.drftpd.commands.UserManagement;
import org.drftpd.plugins.SiteBot;
import org.drftpd.usermanager.NoSuchUserException;
import org.drftpd.usermanager.User;
import org.drftpd.usermanager.UserFileException;
import org.tanesha.replacer.ReplacerEnvironment;

import f00f.net.irc.martyr.commands.MessageCommand;

/**
 * @author Teflon
 */
public class Ident extends IRCCommand {
    private static final Logger logger = Logger.getLogger(Ident.class);

	public Ident(GlobalContext gctx) {
		super(gctx);
	}

	public ArrayList<String> doIdent(String args, MessageCommand msgc) {
	    ArrayList<String> out = new ArrayList<String>();
	    out.add("");
		ReplacerEnvironment env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);
		
		StringTokenizer st = new StringTokenizer(args);
		if (st.countTokens() < 2)
		    return out;
		
		String username = st.nextToken();
		String password = st.nextToken();

        User user;
        try {
            user = getGlobalContext().getUserManager().getUserByName(username);
        } catch (NoSuchUserException e) {
            logger.log(Level.WARN, username + " " + e.getMessage(), e);
            return out;
        } catch (UserFileException e) {
            logger.log(Level.WARN, "", e);
            return out;
        }

        if (user.checkPassword(password)) {
         	String ident = msgc.getSource().getNick() + "!" 
							+ msgc.getSource().getUser() + "@" 
							+ msgc.getSource().getHost();
        	user.getKeyedMap().setObject(UserManagement.IRCIDENT,ident);
        	try {
				user.commit();
	           	logger.info("Set IRC ident to '"+ident+"' for "+user.getName());
            	out.add("Set IRC ident to '"+ident+"' for "+user.getName());
			} catch (UserFileException e1) {
				logger.warn("Error saving userfile for "+user.getName(),e1);
				out.add("Error saving userfile for "+user.getName());
			}
         }

        return out;
	}
	
}
