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
import java.util.Calendar;
import java.util.Iterator;

import net.sf.drftpd.ObjectNotFoundException;
import net.sf.drftpd.master.BaseFtpConnection;
import net.sf.drftpd.util.ReplacerUtils;

import org.apache.log4j.Logger;
import org.drftpd.Bytes;
import org.drftpd.GlobalContext;
import org.drftpd.irc.SiteBot;
import org.drftpd.irc.utils.MessageCommand;
import org.drftpd.plugins.Trial;
import org.drftpd.plugins.Trial.Limit;
import org.drftpd.usermanager.User;
import org.tanesha.replacer.ReplacerEnvironment;


public class Trials extends IRCCommand {
    private static final Logger logger = Logger.getLogger(Trials.class);

    public Trials() {
		super();
    }

    protected String jprintf(String key, User user, Limit limit, long bytesleft, boolean unique) {
        ReplacerEnvironment env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);
        env.add("user", user.getName());

        if (limit != null) {
            env.add("period", Trial.getPeriodName(limit.getPeriod()));
            env.add("name", limit.getName());

            if (unique) {
                env.add("bonusexpires",
                    Trial.getCalendarForEndOfBonus(user, limit.getPeriod())
                         .getTime());
                env.add("uniqueexpires",
                    Trial.getCalendarForEndOfFirstPeriod(user, limit.getPeriod())
                         .getTime());
            }

            env.add("expires",
                Trial.getCalendarForEndOfPeriod(limit.getPeriod()).getTime());
        }

        env.add("bytesleft", Bytes.formatBytes(bytesleft));
        env.add("bytespassed", Bytes.formatBytes(-bytesleft));

        return BaseFtpConnection.jprintf(Trials.class, key, env, user);
    }
    
	public ArrayList<String> doPassed(String args, MessageCommand msgc) {
	    ArrayList<String> out = new ArrayList<String>();
		ReplacerEnvironment env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);
		
		Trial trial;
		try {
		    trial = (Trial) getGlobalContext().getFtpListener(Trial.class);
        } catch (ObjectNotFoundException e) {
            logger.warn("Trial plugin not loaded ... !passed will be disabled.");
            trial = null;
        }

		if (trial == null) {
		    out.add(ReplacerUtils.jprintf("disabled", env, Trials.class));
		    return out;
		}
		
		User user;	
	    if (args.equals("")) {
	    	user = SiteBot.getUserByNickname(msgc.getSource(), out, env, logger);
			
	     	if (user == null)
	            return out;
	    } else {
	        try {
                user = getGlobalContext().getUserManager().getUserByName(args);
            } catch (Exception e) {
                env.add("user", args);
                out.add(ReplacerUtils.jprintf("nosuchuser", env, Trials.class));
                return out;
            } 
	    }
		env.add("user", user.getName());

        int i = 0;

        for (Iterator iter = trial.getLimits().iterator(); iter.hasNext();) {
            Limit limit = (Limit) iter.next();

            if (limit.getPerm().check(user)) {
                i++;

                Calendar endofbonus = Trial.getCalendarForEndOfBonus(user, limit.getPeriod());

                if (System.currentTimeMillis() <= endofbonus.getTimeInMillis()) {
                    //in bonus or unique period
                    long bytesleft = limit.getBytes() - user.getUploadedBytes();

                    if (bytesleft <= 0) {
                        //in bonus or passed
                        out.add(jprintf("passedunique", user, limit, bytesleft, true));
                    } else {
                        //in unique period
                        out.add(jprintf("trialunique", user, limit, bytesleft, true));
                    }
                } else {
                    //plain trial
                    long bytesleft = limit.getBytes() -
                        Trial.getUploadedBytesForPeriod(user, limit.getPeriod());

                    if (bytesleft <= 0) {
                        out.add(jprintf("passed", user, limit, bytesleft, false));
                    } else {
                        out.add(jprintf("trial", user, limit, bytesleft, false));
                    }
                }
            }
        }

        if (i == 0) {
            out.add(jprintf("exempt", user, null, 0, false));
        }
		
		return out;
	}
}
