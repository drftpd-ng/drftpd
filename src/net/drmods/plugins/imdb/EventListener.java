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
package net.drmods.plugins.imdb;

import java.util.ResourceBundle;

import org.apache.log4j.Logger;
import org.drftpd.plugins.SiteBot;
import org.drftpd.sections.SectionInterface;

import net.sf.drftpd.ObjectNotFoundException;
import net.sf.drftpd.event.DirectoryFtpEvent;
import net.sf.drftpd.event.Event;
import net.sf.drftpd.event.FtpListener;
import org.drftpd.master.ConnectionManager;
import net.sf.drftpd.util.ReplacerUtils;

/**
 * @author Teflon
 */
public class EventListener extends FtpListener {

    private static final Logger logger = Logger.getLogger(EventListener.class); 

    public EventListener() {
    }

    public void actionPerformed(Event event) {
        if (!(event instanceof DirectoryFtpEvent))
            return;
        DirectoryFtpEvent devent = (DirectoryFtpEvent) event;
        if (!getGlobalContext().getConfig().checkPathPermission("dirlog",devent.getUser(), devent.getDirectory()))
            return;

        if ("MKD".equals(devent.getCommand()) ||
            "PRE".equals(devent.getCommand())) {
            String dirName = devent.getDirectory().getName();
            SectionInterface sec = getGlobalContext().getSectionManager().lookup(devent.getDirectory().getPath());
       		String[] checkSections = ResourceBundle.getBundle(IMDBParser.class.getName())
       									.getString("mkdir.sections").split(";");
       		String[] excludeDirs = ResourceBundle.getBundle(IMDBParser.class.getName())
										.getString("mkdir.exclude").split(";");
       		
       		for (int i=0; i < checkSections.length; i++) {
       		    if (sec.getName().equalsIgnoreCase(checkSections[i]))
       		        break;
       		    
       		    if (i == checkSections.length-1)
       		        return;
       		}
 
       		for (int i=0; i < excludeDirs.length; i++) {
       		    if (dirName.equalsIgnoreCase(excludeDirs[i]))
       		        return;
       		}
       		
            SiteBot irc;
    		try {
    			irc = (SiteBot) getGlobalContext().getFtpListener(SiteBot.class);
    		} catch (ObjectNotFoundException e) {
    			logger.warn("Error loading IMDB sitebot component", e);
    			return;
    		}        

    		class IMDBThread extends Thread {
       		    private SiteBot _irc;
       		    private SectionInterface _section;
       		    private String _dirname;
       		    public IMDBThread(SiteBot irc, SectionInterface sec, String dirname) {
       		        _irc = irc;
       		        _section = sec;
       		        _dirname = dirname;
       		    }
       		    public void run() {
       		        IMDBParser imdb = new IMDBParser(_dirname);
       		        if (!imdb.foundFilm()) {
       		            logger.info("No imdb info found for " + _dirname);
       		            return;
       		        }           
       		        _irc.say(_section, 
       		                ReplacerUtils.jprintf("mkdir.announce", imdb.getEnv(), IMDBParser.class));
       		    }
       		};
       		IMDBThread thread = new IMDBThread(irc, sec, dirName);
       		thread.start();
         }
    }

    public void unload() {
    }

    public void init(ConnectionManager connectionManager) {
    }

}
