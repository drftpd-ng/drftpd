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

import net.sf.drftpd.FileExistsException;
import net.sf.drftpd.event.DirectoryFtpEvent;
import net.sf.drftpd.util.ReplacerUtils;

import org.apache.log4j.Logger;
import org.drftpd.GlobalContext;
import org.drftpd.plugins.SiteBot;
import org.drftpd.remotefile.LinkedRemoteFileInterface;
import org.drftpd.sitebot.IRCCommand;
import org.drftpd.usermanager.User;
import org.tanesha.replacer.ReplacerEnvironment;

import f00f.net.irc.martyr.commands.MessageCommand;
import f00f.net.irc.martyr.util.FullNick;

/**
 * @author Teflon
 * @version $Id$
 */
public class Approve extends IRCCommand {
	private static final Logger logger = Logger.getLogger(Approve.class);

	public Approve(GlobalContext gctx) {
		super(gctx);
	}

	public ArrayList<String> doApprove(String args, MessageCommand msgc) {
	    ArrayList<String> out = new ArrayList<String>();
		ReplacerEnvironment env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);
		env.add("ircnick", msgc.getSource().getNick());
		env.add("sdirname",args);

		if (args.equals("")) {
	        out.add(ReplacerUtils.jprintf("approve.usage", env, Approve.class));
	        return out;
	    }
		
		FullNick fn = msgc.getSource();
		String ident = fn.getNick() + "!" + fn.getUser() + "@" + fn.getHost();
		User user;
     	try {
     	    user = getGlobalContext().getUserManager().getUserByIdent(ident);
            env.add("ftpuser",user.getName());
     	} catch (Exception e) {
     	    logger.warn("Could not identify " + ident);
     	    out.add(ReplacerUtils.jprintf("ident.noident", env, SiteBot.class));
     	    return out;
     	}
     	
        LinkedRemoteFileInterface dir = findDir(getGlobalContext(),
												 getGlobalContext().getRoot(), user, args);
        
		if (dir!= null){
			env.add("sdirpath",dir.getPath());
			String approveDirName = ReplacerUtils.jprintf("approve.dirname", env, Approve.class);
			env.add("adirname",approveDirName);
			env.add("adirpath",dir.getPath() + "/" + approveDirName);
			try {
				LinkedRemoteFileInterface newdir = dir.createDirectory(approveDirName);
				newdir.setOwner(user.getName());
				newdir.setGroup(user.getGroup());
				out.add(ReplacerUtils.jprintf("approve.success", env, Approve.class));
				getGlobalContext().dispatchFtpEvent(
						new DirectoryFtpEvent(user, "MKD", newdir));
			} catch (FileExistsException e1) {
				out.add(ReplacerUtils.jprintf("approve.exists", env, Approve.class));
			}
		} else {
			out.add(ReplacerUtils.jprintf("approve.error", env, Approve.class));
		}
	    return out;
	}
	
	private static LinkedRemoteFileInterface findDir(
		GlobalContext gctx,
		LinkedRemoteFileInterface dir,
		User user,
		String searchstring) {

		if (!gctx.getConfig().checkPathPermission("privpath", user, dir, true)) {
			Logger.getLogger(Approve.class).debug("privpath: "+dir.getPath());
			return null;
		}

		for (Iterator iter = dir.getDirectories().iterator(); iter.hasNext();) {
			LinkedRemoteFileInterface file = (LinkedRemoteFileInterface) iter.next();
			if (file.isDirectory()) {
				if (file.getName().toLowerCase().equals(searchstring.toLowerCase())) {
					logger.info("Found " + file.getPath());
					return file;
				} 
				LinkedRemoteFileInterface dir2 = findDir(gctx, file, user, searchstring);
				if (dir2 != null) {
					return dir2;
				}		
			}
		}
		return null;
	}
}
