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
    private ConnectionManager _cm;
    private SiteBot _irc;
    private SectionInterface _section;
    private String _dirName;
    public EventListener() {
    }

    public void actionPerformed(Event event) {
        if (!(event instanceof DirectoryFtpEvent))
            return;
        DirectoryFtpEvent devent = (DirectoryFtpEvent) event;
        if (!_cm.getGlobalContext().getConfig().checkPathPermission("dirlog",devent.getUser(), devent.getDirectory()))
            return;
        
        if ("MKD".equals(devent.getCommand()) ||
            "PRE".equals(devent.getCommand())) {
            _dirName = devent.getDirectory().getName();
       		_section = _cm.getGlobalContext().getSectionManager().lookup(devent.getDirectory().getPath());
       		String[] checkSections = ResourceBundle.getBundle(IMDBParser.class.getName())
       									.getString("mkdir.sections").split(";");
       		String[] excludeDirs = ResourceBundle.getBundle(IMDBParser.class.getName())
										.getString("mkdir.exclude").split(";");
       		
       		for (int i=0; i < checkSections.length; i++) {
       		    if (_section.getName().equalsIgnoreCase(checkSections[i]))
       		        break;
       		    
       		    if (i == checkSections.length-1)
       		        return;
       		}
 
       		for (int i=0; i < excludeDirs.length; i++) {
       		    if (_dirName.equalsIgnoreCase(excludeDirs[i]))
       		        return;
       		}
       		
       		Thread thread = new Thread() {
       		    public void run() {
       		        IMDBParser imdb = new IMDBParser(_dirName);
       		        if (!imdb.foundFilm()) {
       		            logger.info("No imdb info found for " + _dirName);
       		            return;
       		        }           
       		        _irc.say(_section, 
       		                ReplacerUtils.jprintf("mkdir.announce", imdb.getEnv(), IMDBParser.class));
       		        this.destroy();
       		    }
       		};
       		thread.start();
        }
    }

    public void unload() {
    }

    public void init(ConnectionManager connectionManager) {
        _cm = connectionManager;
		try {
			_irc = (SiteBot) _cm.getGlobalContext().getFtpListener(SiteBot.class);
		} catch (ObjectNotFoundException e) {
			logger.warn("Error loading IMDB sitebot component", e);
		}
    }

}
