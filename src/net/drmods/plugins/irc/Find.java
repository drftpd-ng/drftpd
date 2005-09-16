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
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.Properties;
import java.util.StringTokenizer;

import net.sf.drftpd.master.config.FtpConfig;
import net.sf.drftpd.util.ReplacerUtils;

import org.apache.log4j.Logger;
import org.apache.oro.text.GlobCompiler;
import org.drftpd.GlobalContext;
import org.drftpd.permissions.GlobPathPermission;
import org.drftpd.plugins.SiteBot;
import org.drftpd.remotefile.LinkedRemoteFileInterface;
import org.drftpd.sitebot.IRCCommand;
import org.drftpd.usermanager.User;
import org.tanesha.replacer.ReplacerEnvironment;

import f00f.net.irc.martyr.commands.MessageCommand;
import f00f.net.irc.martyr.util.FullNick;

/**
 * @author N3m3Sis-succo . UPDATES by SMAN
 * @version $Id: Find.java,v 1.2 2-21-2004 12:16:00 
 * 
 */
public class Find extends IRCCommand {
    private static final Logger logger = Logger.getLogger(Find.class);
    
    public Find() {
		super();
		loadConf("conf/drmods.conf");
	}

	public void loadConf(String confFile) {
        Properties cfg = new Properties();
        FileInputStream file = null;
        try {
            file = new FileInputStream(confFile);
            cfg.load(file);
            String perm;
            for (int i=1;; i++) {
                perm  = cfg.getProperty("find.perms." + i);
                if (perm == null)
                    break;
                StringTokenizer st = new StringTokenizer(perm);
                
                GlobalContext.getGlobalContext().getConfig().addPathPermission("ircfind", 
                       new GlobPathPermission(new GlobCompiler().compile(st.nextToken()), 
                               FtpConfig.makeUsers(st)));
                
            }
        } catch (Exception e) {
            logger.error("Error reading " + confFile,e);
            throw new RuntimeException(e.getMessage());
        } finally {
        	try {
        		file.close();
        	} catch (IOException e) {
        	}
        }
	}
    
    private void findFile(LinkedRemoteFileInterface dir, ArrayList<String> results,
            Collection searchstrings, User user, boolean files, boolean dirs) {
        if (!GlobalContext.getGlobalContext().getConfig().checkPathPermission("ircfind", user, dir, true))
            return;
        
        if (!GlobalContext.getGlobalContext().getConfig().checkPathPermission("privpath", user, dir, true))
            return;

        for (Iterator iter = dir.getFiles().iterator(); iter.hasNext();) {
            LinkedRemoteFileInterface file = (LinkedRemoteFileInterface) iter.next();
            if (results.size() >= 5)
                return;
            if (file.isDirectory()) {
                findFile(file, results, searchstrings, user, files, dirs);
            }
            boolean isFind = false;
            boolean allFind = true;
            if (dirs && file.isDirectory() || files && file.isFile()) { 
                for (Iterator iterator = searchstrings.iterator(); iterator.hasNext(); ){ 
                    String searchstring = (String) iterator.next(); 
                    if (file.getName().toLowerCase().indexOf(searchstring.toLowerCase()) != -1)  
                        isFind = true;
                    else
                        allFind = false;
                } 
                
                if (isFind && allFind){ 
                    ReplacerEnvironment env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);
                    env.add("path", file.getPath()); 
                    results.add(ReplacerUtils.jprintf("find.result", env, Find.class));
                } 
            } 
        }
    }
    
	public ArrayList<String> doFind(String args, MessageCommand msgc) {
	    ArrayList<String> out = new ArrayList<String>();
	    ReplacerEnvironment env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);
        env.add("ircnick",msgc.getSource().getNick());
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
		if (args.equals("")) {
	         out.add(ReplacerUtils.jprintf("find.syntax",env,Find.class));
	         return out;
	    }
        
        Collection searchStrings = Arrays.asList(args.split(" "));
        ArrayList<String> results = new ArrayList<String>();
 
        findFile(GlobalContext.getGlobalContext().getRoot(), results, searchStrings, user, false, true);

        if (!results.isEmpty()) {
            for (String res : results)
                out.add(res);
        } else {
            out.add(ReplacerUtils.jprintf("find.nodirs", env, Find.class)); 
            findFile(GlobalContext.getGlobalContext().getRoot(), results, searchStrings, user, true, false);   
            if (results.isEmpty())
                out.add(ReplacerUtils.jprintf("find.noresult", env, Find.class));
            else
                for (String res : results)
                    out.add(res);
        }
        
        if (results.size() >= 5) {
            out.add("Search limit reached, refine your search!");    
        }
        return out;
	}
}