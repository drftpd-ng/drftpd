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
package net.drmods.plugins.irc.imdb;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Properties;

import net.sf.drftpd.util.ReplacerUtils;

import org.apache.log4j.Logger;
import org.drftpd.GlobalContext;
import org.drftpd.plugins.SiteBot;
import org.drftpd.sitebot.IRCCommand;
import org.tanesha.replacer.ReplacerEnvironment;

import f00f.net.irc.martyr.commands.MessageCommand;

/**
 * @author Teflon
 */
public class IRCListener extends IRCCommand {
    private static final Logger logger = Logger.getLogger(IRCListener.class);
    private String _filters;
    
    public IRCListener(GlobalContext gctx) {
        super(gctx); 
		loadConf("conf/drmods.conf");
	}

	public void loadConf(String confFile) {
        Properties cfg = new Properties();
        FileInputStream file;
        try {
            file = new FileInputStream(confFile);
            cfg.load(file);
            file.close();
            _filters = cfg.getProperty("imdb.filter");
            if (_filters == null) {
                throw new RuntimeException("Unspecified value 'imdb.filter' in " + confFile);        
            }      
        } catch (FileNotFoundException e) {
            logger.error("Error reading " + confFile,e);
            throw new RuntimeException(e.getMessage());
        } catch (IOException e) {
            logger.error("Error reading " + confFile,e);
            throw new RuntimeException(e.getMessage());
        }
	}

    public ArrayList<String> doImdb(String command, MessageCommand msgc) {
        ArrayList<String> out = new ArrayList<String>();
        ReplacerEnvironment env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);
		env.add("ircnick", msgc.getSource().getNick());	
        
       	try { 
       		String searchStr = command.substring(command.indexOf(" ") + 1);
       		
            IMDBParser imdb = new IMDBParser(searchStr, _filters);
            if (!imdb.foundFilm()) {
                env.add("searchstr", searchStr);
                out.add(ReplacerUtils.jprintf("imdb.notfound", env, IMDBParser.class));
            } else {
                out.add(ReplacerUtils.jprintf("imdb.announce", imdb.getEnv(), IMDBParser.class));
            }
            return out;
       	} catch (StringIndexOutOfBoundsException e) { 
       		logger.warn("", e); 
       		out.add("what??");
       		return out; 
       	}       
    }
}
