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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Iterator;
import java.util.StringTokenizer;

import net.sf.drftpd.FileExistsException;
import net.sf.drftpd.util.ReplacerUtils;

import org.apache.log4j.Logger;
import org.drftpd.GlobalContext;
import org.drftpd.dynamicdata.Key;
import org.drftpd.master.ConnectionManager;
import org.drftpd.plugins.SiteBot;
import org.drftpd.remotefile.LinkedRemoteFileInterface;
import org.drftpd.sitebot.IRCPluginInterface;
import org.drftpd.usermanager.NoSuchUserException;
import org.drftpd.usermanager.User;
import org.tanesha.replacer.ReplacerEnvironment;

import f00f.net.irc.martyr.GenericCommandAutoService;
import f00f.net.irc.martyr.InCommand;
import f00f.net.irc.martyr.commands.MessageCommand;
/**
 * @author Kolor & Teflon
 * @version $Id$
 */
public class Request extends GenericCommandAutoService implements IRCPluginInterface {

	private static final Logger logger = Logger.getLogger(Request.class);
    public static final Key REQUESTS = new Key(Request.class, "requests", Integer.class);
    public static final Key REQFILLED = new Key(Request.class, "reqfilled", Integer.class);
    public static final Key WEEKREQS = new Key(Request.class, "weekreq", Integer.class);

	private SiteBot _listener;
	String allow = "";
	String deny = "";

	public String getCommands() {
		return _listener.getCommandPrefix() + "requests " +
				_listener.getCommandPrefix() + "request " + 
				_listener.getCommandPrefix() + "reqfilled " + 
				_listener.getCommandPrefix() + "reqdel";
	}

    public String getCommandsHelp(User user) {
        String help = "";
        if (_listener.getIRCConfig().checkIrcPermission(_listener.getCommandPrefix() + "requests", user))
            help += _listener.getCommandPrefix() 
            		+ "requests : Displays the current requests ont the site.\n";
        if (_listener.getIRCConfig().checkIrcPermission(_listener.getCommandPrefix() + "request", user))
            help += _listener.getCommandPrefix() 
            		+ "request <reqname> : Request <reqname>.\n";
        if (_listener.getIRCConfig().checkIrcPermission(_listener.getCommandPrefix() + "reqfilled", user))
            help += _listener.getCommandPrefix() 
            		+ "reqfilled <reqname> : Marks the request <reqname> as filled.\n";
        if (_listener.getIRCConfig().checkIrcPermission(_listener.getCommandPrefix() + "reqdel", user))
            help += _listener.getCommandPrefix() 
            		+ "reqdel <reqname> : Deletes the request <reqname>.\n";
		return help;
    }  
	
	private ConnectionManager getConnectionManager() {
		return _listener.getGlobalContext().getConnectionManager();
	}

	public Request(SiteBot listener) {
		super(listener.getIRCConnection());
		_listener = listener;
	}

	protected void updateCommand(InCommand command) {
		if (!(command instanceof MessageCommand)) {
			return;
		}
		MessageCommand msgc = (MessageCommand) command;
		if (msgc.isPrivateToUs(_listener.getIRCConnection().getClientState())) {
			return;
		}
		
		String msg = msgc.getMessage().trim();
		if (msg.startsWith(_listener.getCommandPrefix() + "requests"))
		    doREQUESTS(msgc);
		else if (msg.startsWith(_listener.getCommandPrefix() + "request"))
		    doREQUEST(msgc);
		else if (msg.startsWith(_listener.getCommandPrefix() + "reqfilled"))
		    doREQFILLED(msgc);
		else if (msg.startsWith(_listener.getCommandPrefix() + "reqdel"))
		    doREQDEL(msgc);
	}

    private void doREQUESTS(MessageCommand msgc) {
        ReplacerEnvironment env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);
        env.add("botnick",_listener.getIRCConnection().getClientState().getNick().getNick());
        env.add("ircnick",msgc.getSource().getNick());
        
        User user;
        try {
            user = _listener.getIRCConfig().lookupUser(msgc.getSource());
        } catch (NoSuchUserException e) {
            _listener.say(msgc.getDest(), 
                    ReplacerUtils.jprintf("ident.noident", env, SiteBot.class));
            return;
        }
        env.add("ftpuser",user.getName());
        env.add("reqfilled",_listener.getCommandPrefix() + "reqfilled");
        
        if (!_listener.getIRCConfig().checkIrcPermission(_listener.getCommandPrefix() + "requests",user)) {
            _listener.say(msgc.getDest(), 
                    ReplacerUtils.jprintf("ident.denymsg", env, SiteBot.class));
            return;				
        }
        
        
        try {
            LinkedRemoteFileInterface rdir = getConnectionManager().getGlobalContext().getRoot().getFile(ReplacerUtils.jprintf("request.dirpath", env, Request.class));
            _listener.say(msgc.getDest(),
                    ReplacerUtils.jprintf("requests.header", env, Request.class));
            int i=1;
            for (Iterator iter = rdir.getDirectories().iterator(); iter.hasNext();) {
                LinkedRemoteFileInterface file = (LinkedRemoteFileInterface) iter.next();
                if (file.isDirectory()) {
                    //	if (file.getName().startsWith("REQUEST")) {
                    StringTokenizer st =
                        new StringTokenizer(file.getName(), "-");
                    if (st.nextToken().equals("REQUEST")) {
                        String byuser = st.nextToken();
                        String request = st.nextToken();
                        while (st.hasMoreTokens()) {
                            request = request+"-"+st.nextToken();
                        }
                        byuser = byuser.replace('.',' ');
                        String num = Integer.toString(i);
                        env.add("num",num);
                        env.add("requser",byuser.replaceAll("by ",""));
                        env.add("reqrequest",request);
                        i=i+1;
                        _listener.say(msgc.getDest(),ReplacerUtils.jprintf("requests.list", env, Request.class));	
                    }
                }
            }
            _listener.say(msgc.getDest(),
                    ReplacerUtils.jprintf("requests.footer", env, Request.class));
        }  catch (FileNotFoundException e) {
            return; 
        }
        
    }

    private void doREQFILLED(MessageCommand msgc) {
        ReplacerEnvironment env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);
        env.add("botnick",_listener.getIRCConnection().getClientState().getNick().getNick());
        env.add("ircnick",msgc.getSource().getNick());
        
        User user;
        try {
            user = _listener.getIRCConfig().lookupUser(msgc.getSource());
        } catch (NoSuchUserException e) {
            _listener.say(msgc.getDest(), 
                    ReplacerUtils.jprintf("ident.noident", env, SiteBot.class));
            return;
        }
        env.add("ftpuser",user.getName());
        
        if (!_listener.getIRCConfig().checkIrcPermission(_listener.getCommandPrefix() + "reqfilled",user)) {
            _listener.say(msgc.getDest(), 
                    ReplacerUtils.jprintf("ident.denymsg", env, SiteBot.class));
            return;				
        }
        
        String dirName;
        try {
            dirName = msgc.getMessage().substring((_listener.getCommandPrefix() + "reqfilled ").length()).trim();
            if (dirName.length()==0) 
            { 
                _listener.say(msgc.getDest(), 
                        ReplacerUtils.jprintf("reqfilled.usage", env, Request.class)); 
                return; 
            } 
            
        } catch (ArrayIndexOutOfBoundsException e) {
            logger.warn("", e);
            _listener.say(msgc.getDest(), 
                    ReplacerUtils.jprintf("reqfilled.usage", env, Request.class));
            return;
        } catch (StringIndexOutOfBoundsException e) {
            logger.warn("", e);
            _listener.say(msgc.getDest(), 
                    ReplacerUtils.jprintf("reqfilled.usage", env, Request.class));
            return;
        }
        
        env.add("fdirname",dirName);	
        
        String DirPath = ReplacerUtils.jprintf("request.dirpath", env, Request.class);
        
        boolean nodir = false;
        boolean fdir = false;
        
        try {
            LinkedRemoteFileInterface dir = getConnectionManager().getGlobalContext().getRoot().getFile(DirPath);
            for (Iterator iter = dir.getDirectories().iterator(); iter.hasNext();) {
                LinkedRemoteFileInterface file = (LinkedRemoteFileInterface) iter.next();
                if (file.isDirectory()) {
                    if (file.getName().endsWith(dirName)) {
                        nodir = false;
                        String fdirname = file.getName().toString();
                        fdirname = fdirname.replaceAll("REQUEST-by.","FILLED-for.");
                        user.getKeyedMap().setObject(Request.REQFILLED, 
                                user.getKeyedMap().getObjectInt(Request.REQFILLED)+1);;
                        try {
                            file.renameTo(file.getParentFile().getPath(),fdirname);
                            fdir = true;
                            _listener.sayGlobal(ReplacerUtils.jprintf("reqfilled.success", env, Request.class));
                            break;
                        } catch (IOException e) {
                            logger.warn("", e);
                        }
                        
                        
                    } else nodir = true;
                }
            }
            
            if (nodir && !fdir) _listener.say(msgc.getDest(),ReplacerUtils.jprintf("reqfilled.error", env, Request.class));
            
        } catch (FileNotFoundException e) {
            _listener.say(msgc.getDest(),ReplacerUtils.jprintf("reqfilled.error", env, Request.class));
            return;
        }
        
    }

    private void doREQUEST(MessageCommand msgc) {
        ReplacerEnvironment env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);
        env.add("botnick",_listener.getIRCConnection().getClientState().getNick().getNick());
        env.add("ircnick",msgc.getSource().getNick());
        
        User user;
        try {
            user = _listener.getIRCConfig().lookupUser(msgc.getSource());
        } catch (NoSuchUserException e) {
            _listener.say(msgc.getDest(), 
                    ReplacerUtils.jprintf("ident.noident", env, SiteBot.class));
            return;
        }
        env.add("ftpuser",user.getName());
        
        if (!_listener.getIRCConfig().checkIrcPermission(_listener.getCommandPrefix() + "request",user)) {
            _listener.say(msgc.getDest(), 
                    ReplacerUtils.jprintf("ident.denymsg", env, SiteBot.class));
            return;				
        }
        
        String dirName;
        try {
            dirName = msgc.getMessage().substring("!request ".length()).trim();
            if (dirName.length()==0) 
            { 
                _listener.say(msgc.getDest(), 
                        ReplacerUtils.jprintf("request.usage", env, Request.class)); 
                return; 
            } 
            
        } catch (ArrayIndexOutOfBoundsException e) {
            logger.warn("", e);
            _listener.say(msgc.getDest(), 
                    ReplacerUtils.jprintf("request.usage", env, Request.class));
            return;
        } catch (StringIndexOutOfBoundsException e) {
            logger.warn("", e);
            _listener.say(msgc.getDest(), 
                    ReplacerUtils.jprintf("request.usage", env, Request.class));
            return;
        }
        env.add("rdirname",dirName);
        String requser = user.getName();
        
            String DirPath = ReplacerUtils.jprintf("request.dirpath", env, Request.class);
            
            try {
                LinkedRemoteFileInterface dir = getConnectionManager().getGlobalContext().getRoot().getFile(DirPath);
                dir.createDirectory("REQUEST-by." + requser + "-" + dirName);
                LinkedRemoteFileInterface reqdir = dir.getFile("REQUEST-by." + requser + "-" + dirName);
                reqdir.setOwner(requser);
                user.getKeyedMap().setObject(Request.REQUESTS, user.getKeyedMap().getObjectInt(Request.REQUESTS)+1);;
                _listener.sayGlobal(ReplacerUtils.jprintf("request.success", env, Request.class));
            } catch (FileNotFoundException e) {
                _listener.say(msgc.getDest(),DirPath + " doesn't exist!");
                return;
            } catch (FileExistsException e1) {
                _listener.say(msgc.getDest(),ReplacerUtils.jprintf("request.exists", env, Request.class));
            } 	
        }

    private void doREQDEL(MessageCommand msgc) {
        ReplacerEnvironment env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);
        env.add("botnick",_listener.getIRCConnection().getClientState().getNick().getNick());
        env.add("ircnick",msgc.getSource().getNick());
        
        User user;
        try {
            user = _listener.getIRCConfig().lookupUser(msgc.getSource());
        } catch (NoSuchUserException e) {
            _listener.say(msgc.getDest(), 
                    ReplacerUtils.jprintf("ident.noident", env, SiteBot.class));
            return;
        }
        env.add("ftpuser",user.getName());
        
        if (!_listener.getIRCConfig().checkIrcPermission(_listener.getCommandPrefix() + "reqdel",user)) {
            _listener.say(msgc.getDest(), 
                    ReplacerUtils.jprintf("ident.denymsg", env, SiteBot.class));
            return;				
        }
        
        
        String dirName;
        try {
            dirName = msgc.getMessage().substring((_listener.getCommandPrefix() + "reqdel ").length()).trim();
            if (dirName.length()==0) 
            { 
                _listener.say(msgc.getDest(), 
                        ReplacerUtils.jprintf("reqdel.usage", env, Request.class)); 
                return; 
            } 
            
        } catch (ArrayIndexOutOfBoundsException e) {
            logger.warn("", e);
            _listener.say(msgc.getDest(), 
                    ReplacerUtils.jprintf("reqdel.usage", env, Request.class));
            return;
        } catch (StringIndexOutOfBoundsException e) {
            logger.warn("", e);
            _listener.say(msgc.getDest(), 
                    ReplacerUtils.jprintf("reqdel.usage", env, Request.class));
            return;
        }
        
        env.add("ddirname",dirName);	
        
        String DirPath = ReplacerUtils.jprintf("request.dirpath", env, Request.class);
        
        boolean nodir = false;
        boolean deldir = false;
        try {
            LinkedRemoteFileInterface dir = getConnectionManager().getGlobalContext().getRoot().getFile(DirPath);
            for (Iterator iter = dir.getDirectories().iterator(); iter.hasNext();) {
                LinkedRemoteFileInterface file = (LinkedRemoteFileInterface) iter.next();
                if (file.isDirectory()) {
                    if (file.getName().endsWith(dirName)) {
                        nodir = false;
                        if (file.getUsername().equals(user.getName()))
                        {
                            file.delete();
                            deldir = true;
                            _listener.sayGlobal(ReplacerUtils.jprintf("reqdel.success", env, Request.class));
                            break;
                            
                        } else {
                            _listener.sayGlobal(ReplacerUtils.jprintf("reqdel.notowner", env, Request.class));
                            break;
                        }
                        
                        
                        
                    } else nodir = true;
                }
            }
            
            if (nodir && !deldir) _listener.say(msgc.getDest(),ReplacerUtils.jprintf("reqdel.error", env, Request.class));
            
            
        } catch (FileNotFoundException e) {
            _listener.say(msgc.getDest(),ReplacerUtils.jprintf("reqdel.error", env, Request.class));
            return;
        }
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
