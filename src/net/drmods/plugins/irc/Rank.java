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
package net.drmods.plugins.irc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.StringTokenizer;

import net.sf.drftpd.util.ReplacerUtils;
import net.sf.drftpd.util.UserComparator;

import org.apache.log4j.Logger;
import org.drftpd.Bytes;
import org.drftpd.GlobalContext;
import org.drftpd.commands.TransferStatistics;
import org.drftpd.plugins.SiteBot;
import org.drftpd.plugins.Trial;
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
public class Rank extends IRCCommand {
    private static final Logger logger = Logger.getLogger(Approve.class);

    public Rank(GlobalContext gctx) {
		super(gctx);
    }

	public ArrayList<String> doRank(String args, MessageCommand msgc) {
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
	    } else {
	        try {
                user = getGlobalContext().getUserManager().getUserByName(args);
            } catch (Exception e) {
                env.add("user", args);
                out.add(ReplacerUtils.jprintf("rank.error", env, Rank.class));
                return out;
            } 
	    }

        String exemptgroups = ReplacerUtils.jprintf("top.exempt", env, Rank.class);
        StringTokenizer st = new StringTokenizer(exemptgroups);
        while (st.hasMoreTokens()) {
            if (user.isMemberOf(st.nextToken())) {
                env.add("eusr", user.getName());
                env.add("egrp", user.getGroup());
                out.add(ReplacerUtils.jprintf("rank.exempt", env, Rank.class));
                return out;
            }
        }

        Collection<User> users;
        try {
            users = getGlobalContext().getUserManager().getAllUsers();
        } catch (UserFileException e) {
            out.add("Error processing userfiles");
            return out;
        }
        String type = "MONTHUP";

        boolean found = false;
        String exempt[] = exemptgroups.split(" ");
        ArrayList<User> filteredusers = new ArrayList<User>();
        for (User fuser : users) {
            found = false;
            for (int i = 0; i < exempt.length; i++) {
                if (!fuser.isMemberOf(exempt[i]))
                    found = true;
            }
            if (found)
                filteredusers.add(fuser);
        }
        
        Collections.sort(filteredusers, new UserComparator(type));
        
        int pos = filteredusers.indexOf(user);
        env.add("user", user.getName());
        env.add("group", user.getGroup());
        env.add("mnup", Bytes.formatBytes(user.getUploadedBytesMonth()));
        env.add("upfilesmonth", "" + user.getUploadedFilesMonth());
        env.add("mnrateup", TransferStatistics.getUpRate(user,Trial.PERIOD_MONTHLY));
        if (pos == 0) {
            out.add(ReplacerUtils.jprintf("rank.ontop", env, Rank.class));
        } else {
            User prevUser = filteredusers.get(pos-1);
            env.add("pos", ""+(pos+1));
            env.add("toup", Bytes.formatBytes(
                    	prevUser.getUploadedBytesMonth() 
                    		- user.getUploadedBytesMonth()));
            env.add("puser", prevUser.getName());
            env.add("pgroup", prevUser.getGroup());
            out.add(ReplacerUtils.jprintf("rank.losing", env, Rank.class));            
        }
        return out;
	}
}
