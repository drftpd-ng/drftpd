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
import java.util.StringTokenizer;

import net.sf.drftpd.event.InviteEvent;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.drftpd.GlobalContext;
import org.drftpd.commands.UserManagement;
import org.drftpd.usermanager.NoSuchUserException;
import org.drftpd.usermanager.User;
import org.drftpd.usermanager.UserFileException;

import f00f.net.irc.martyr.commands.MessageCommand;

/**
 * @author mog
 * @version $Id$
 */
public class Invite extends IRCCommand {
    private static final Logger logger = Logger.getLogger(Invite.class);

    public Invite(GlobalContext gctx) {
		super(gctx);
    }

	public ArrayList<String> doInvite(String args, MessageCommand msgc) {
	    ArrayList<String> out = new ArrayList<String>();

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
        boolean success = user.checkPassword(password);
        getGlobalContext().dispatchFtpEvent(
                new InviteEvent(success ? "INVITE" : "BINVITE", msgc.getSource().getNick(), user));

       	String ident = msgc.getSource().getNick() + "!" 
						+ msgc.getSource().getUser() + "@" 
						+ msgc.getSource().getHost();
       	    		
		if (success) {
		    logger.info("Invited \"" + ident + "\" as user " + user.getName());
		    user.getKeyedMap().setObject(UserManagement.IRCIDENT,ident);
			try {
                user.commit();
            } catch (UserFileException e1) {
                logger.warn("Error saving userfile", e1);
            }
		} else {
		    logger.log(Level.WARN,
		        msgc.getSourceString() +
		        " attempted invite with bad password: " + msgc);
		}
		
		return out;
	}
	
}
