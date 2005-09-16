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

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Properties;

import net.sf.drftpd.FileExistsException;
import net.sf.drftpd.util.ReplacerUtils;

import org.apache.log4j.Logger;
import org.drftpd.GlobalContext;
import org.drftpd.plugins.SiteBot;
import org.drftpd.remotefile.LinkedRemoteFileInterface;
import org.drftpd.sitebot.IRCCommand;
import org.drftpd.usermanager.User;
import org.tanesha.replacer.FormatterException;
import org.tanesha.replacer.ReplacerEnvironment;
import org.tanesha.replacer.SimplePrintf;

import f00f.net.irc.martyr.commands.MessageCommand;
import f00f.net.irc.martyr.util.FullNick;

/**
 * @author Teflon
 * @version $Id$
 */
public class Approve extends IRCCommand {
	private static final Logger logger = Logger.getLogger(Approve.class);
	private String _dirName;
	
	public Approve() {
		super();
		loadConf("conf/drmods.conf");
	}

	public void loadConf(String confFile) {
        Properties cfg = new Properties();
        FileInputStream file = null;
        try {
            file = new FileInputStream(confFile);
            cfg.load(file);
            _dirName = cfg.getProperty("approve.dirname");
            if (_dirName == null) {
                throw new RuntimeException("Unspecified value 'approve.dirname' in " + confFile);        
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
     	    user = GlobalContext.getGlobalContext().getUserManager().getUserByIdent(ident);
            env.add("ftpuser",user.getName());
     	} catch (Exception e) {
     	    logger.warn("Could not identify " + ident);
     	    out.add(ReplacerUtils.jprintf("ident.noident", env, SiteBot.class));
     	    return out;
     	}
     	
        LinkedRemoteFileInterface dir = findDir(GlobalContext.getGlobalContext().getRoot(), user, args);
        
		if (dir!= null){
			env.add("sdirpath",dir.getPath());
			String approveDirName;
            try {
                approveDirName = SimplePrintf.jprintf(_dirName, env);
            } catch (FormatterException e) {
                out.add(e.getMessage());
                return out;
            }
            env.add("adirname",approveDirName);
			env.add("adirpath",dir.getPath() + "/" + approveDirName);
			try {
				LinkedRemoteFileInterface newdir = dir.createDirectory(approveDirName);
				newdir.setOwner(user.getName());
				newdir.setGroup(user.getGroup());
				out.add(ReplacerUtils.jprintf("approve.success", env, Approve.class));
			} catch (FileExistsException e1) {
				out.add(ReplacerUtils.jprintf("approve.exists", env, Approve.class));
			}
		} else {
			out.add(ReplacerUtils.jprintf("approve.error", env, Approve.class));
		}
	    return out;
	}
	
	private static LinkedRemoteFileInterface findDir(
		LinkedRemoteFileInterface dir,
		User user,
		String searchstring) {

		if (!GlobalContext.getGlobalContext().getConfig().checkPathPermission("privpath", user, dir, true)) {
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
				LinkedRemoteFileInterface dir2 = findDir(file, user, searchstring);
				if (dir2 != null) {
					return dir2;
				}		
			}
		}
		return null;
	}
}
