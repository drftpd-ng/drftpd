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

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Properties;
import java.util.StringTokenizer;

import net.sf.drftpd.util.ReplacerUtils;
import net.sf.drftpd.util.UserComparator;

import org.apache.log4j.Logger;
import org.drftpd.Bytes;
import org.drftpd.commands.TransferStatistics;
import org.drftpd.irc.SiteBot;
import org.drftpd.irc.utils.MessageCommand;
import org.drftpd.plugins.Trial;
import org.drftpd.sitebot.IRCCommand;
import org.drftpd.usermanager.User;
import org.drftpd.usermanager.UserFileException;
import org.tanesha.replacer.ReplacerEnvironment;

/**
 * @author Teflon
 * @version $Id$
 */
public class Rank extends IRCCommand {
    private static final Logger logger = Logger.getLogger(Rank.class);
    private String _exemptGroups;

    public Rank() {
		super();
		loadConf("conf/drmods.conf");
	}

	public void loadConf(String confFile) {
        Properties cfg = new Properties();
        FileInputStream file = null;
        try {
            file = new FileInputStream(confFile);
            cfg.load(file);
            _exemptGroups = cfg.getProperty("rank.exempt");
            if (_exemptGroups == null) {
                throw new RuntimeException("Unspecified value 'rank.exempt' in " + confFile);        
            }      
        } catch (FileNotFoundException e) {
            logger.error("Error reading " + confFile,e);
            throw new RuntimeException(e.getMessage());
        } catch (IOException e) {
            logger.error("Error reading " + confFile,e);
            throw new RuntimeException(e.getMessage());
        } finally {
        	if (file != null) {
        		try {
        			file.close();
        		} catch (IOException e) {
        		}
        	}
        }
	}
	

	public ArrayList<String> doRank(String args, MessageCommand msgc) {
	    ArrayList<String> out = new ArrayList<String>();
		ReplacerEnvironment env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);
		env.add("ircnick", msgc.getSource().getNick());

		User user;
     	
	    if (args.equals("")) {
	     	user = SiteBot.getUserByNickname(msgc.getSource(), out, env, logger);
	     	if (user == null)
	            return out;
	    } else {
	        try {
                user = getGlobalContext().getUserManager().getUserByName(args);
            } catch (Exception e) {
                logger.error(args + " is not a valid username", e);
                env.add("user", args);
                out.add(ReplacerUtils.jprintf("rank.error", env, Rank.class));
                return out;
            } 
	    }

        StringTokenizer st = new StringTokenizer(_exemptGroups);
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

        boolean allow = false;
        String exempt[] = _exemptGroups.split(" ");
        ArrayList<User> filteredusers = new ArrayList<User>();
        for (User fuser : users) {
            allow = true;
            for (int i = 0; i < exempt.length; i++) {
                if (fuser.isMemberOf(exempt[i]))
                    allow = false;
            }
            if (allow && !fuser.isDeleted())
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
	
	public ArrayList<String> doTopRank(String args, MessageCommand msgc) {
	    ArrayList<String> out = new ArrayList<String>();
		ReplacerEnvironment env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);
		env.add("ircnick", msgc.getSource().getNick());

		int count = 10;
        try {
            count = Integer.parseInt(args);
        } catch (NumberFormatException e1) {
        }
        
        Collection<User> users;
        try {
            users = getGlobalContext().getUserManager().getAllUsers();
        } catch (UserFileException e) {
            out.add("Error processing userfiles");
            return out;
        }
        
        String type = "MONTHUP";
        boolean allow = false;
        String exempt[] = _exemptGroups.split(" ");
        ArrayList<User> filteredusers = new ArrayList<User>();
        for (User fuser : users) {
            allow = true;
            for (int i = 0; i < exempt.length; i++) {
                if (fuser.isMemberOf(exempt[i]))
                    allow = false;
            }
            if (allow && !fuser.isDeleted())
                filteredusers.add(fuser);
        }
        
        Collections.sort(filteredusers, new UserComparator(type));
        
        for (int pos = 0; pos < filteredusers.size(); pos++) {
            if (pos >= count) break;
            User user = filteredusers.get(pos);
            env.add("user", user.getName());
            env.add("group", user.getGroup());
            env.add("mnup", Bytes.formatBytes(user.getUploadedBytesMonth()));
            env.add("upfilesmonth", "" + user.getUploadedFilesMonth());
            env.add("mnrateup", TransferStatistics.getUpRate(user,Trial.PERIOD_MONTHLY));
            if (pos == 0) {
                out.add(ReplacerUtils.jprintf("rank.ontop", env, Rank.class));
            } else if (pos > 0) {
                User prevUser = filteredusers.get(pos-1);
                env.add("pos", ""+(pos+1));
                env.add("toup", Bytes.formatBytes(
                        	prevUser.getUploadedBytesMonth() 
                        		- user.getUploadedBytesMonth()));
                env.add("puser", prevUser.getName());
                env.add("pgroup", prevUser.getGroup());
                out.add(ReplacerUtils.jprintf("rank.losing", env, Rank.class));            
            } else {
                env.add("user", args);
                out.add(ReplacerUtils.jprintf("rank.error", env, Rank.class));
                return out;                
            }
        }
		return out;
	}
}
