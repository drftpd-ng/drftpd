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

import org.drftpd.sitebot.IRCPluginInterface;
import org.drftpd.usermanager.NoSuchUserException;
import org.drftpd.usermanager.User;
import org.drftpd.master.ConnectionManager;
import net.sf.drftpd.util.ReplacerUtils;

import org.apache.log4j.Logger;
import org.drftpd.plugins.SiteBot;
import org.tanesha.replacer.ReplacerEnvironment;

import f00f.net.irc.martyr.GenericAutoService;
import f00f.net.irc.martyr.InCommand;
import f00f.net.irc.martyr.State;
import f00f.net.irc.martyr.commands.MessageCommand;

/**
 * @author Teflon
 */
public class IRCListener extends GenericAutoService implements IRCPluginInterface {

    private static final Logger logger = Logger.getLogger(IRCListener.class); 
    private SiteBot _listener; 
    private ConnectionManager _cm;
    
    public IRCListener(SiteBot listener) {
        super(listener.getIRCConnection()); 
        _listener = listener;
    }

    public String getCommands() {
        return _listener.getCommandPrefix() + "imdb";
    }

    public String getCommandsHelp(User user) {
        String help = "";
        if (_listener.getIRCConfig().checkIrcPermission(_listener.getCommandPrefix() + "imdb", user))
                help += _listener.getCommandPrefix() + "imdb <title> - look up the movie <title> in the imdb database.";
		return help;
    }

    protected void updateCommand(InCommand inCommand) {
        if (!(inCommand instanceof MessageCommand)) 
            return; 
        MessageCommand msgc = (MessageCommand) inCommand; 
        if(msgc.isPrivateToUs(_listener.getIRCConnection().getClientState())) 
            return; 
        String msg = msgc.getMessage(); 
        if (msg.startsWith(_listener.getCommandPrefix() + "imdb")) { 
            ReplacerEnvironment env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);
    		env.add("botnick",_listener.getIRCConnection().getClientState().getNick().getNick());
    		env.add("ircnick",msgc.getSource().getNick());	
    		try {
                if (!_listener.getIRCConfig().checkIrcPermission(
                        _listener.getCommandPrefix() + "imdb",msgc.getSource())) {
                	_listener.sayChannel(msgc.getDest(), 
                			ReplacerUtils.jprintf("ident.denymsg", env, SiteBot.class));
                	return;				
                }
            } catch (NoSuchUserException e) {
    			_listener.sayChannel(msgc.getDest(), 
    					ReplacerUtils.jprintf("ident.noident", env, SiteBot.class));
    			return;
            }

           	try { 
           		String searchStr = msgc.getMessage().substring(
           		        			(_listener.getCommandPrefix() + "imdb ").length());
           		
                IMDBParser imdb = new IMDBParser(searchStr);
                if (!imdb.foundFilm()) {
                    env.add("searchstr", searchStr);
                    _listener.sayChannel(msgc.getDest(),
                            ReplacerUtils.jprintf("imdb.notfound", env, IMDBParser.class));
                } else {
               		_listener.sayChannel(msgc.getDest(),
                  		     ReplacerUtils.jprintf("imdb.announce", imdb.getEnv(), IMDBParser.class));
                    
                }
           	} catch (StringIndexOutOfBoundsException e) { 
           		logger.warn("", e); 
           		_listener.sayChannel(msgc.getDest(), "!imdb what??"); 
           		return; 
           	}          
        }   
    }

    protected void updateState(State arg0) {}
    public void unload() {}
    public void init(ConnectionManager connectionManager) {
        _cm = connectionManager;
    }


}
