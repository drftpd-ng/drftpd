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
import java.util.Iterator;
import java.util.Properties;
import java.util.StringTokenizer;

import net.sf.drftpd.FileExistsException;
import net.sf.drftpd.util.ReplacerUtils;

import org.apache.log4j.Logger;
import org.drftpd.GlobalContext;
import org.drftpd.dynamicdata.Key;
import org.drftpd.plugins.SiteBot;
import org.drftpd.remotefile.LinkedRemoteFileInterface;
import org.drftpd.sitebot.IRCCommand;
import org.drftpd.usermanager.User;
import org.tanesha.replacer.ReplacerEnvironment;

import f00f.net.irc.martyr.commands.MessageCommand;
import f00f.net.irc.martyr.util.FullNick;
/**
 * @author Kolor & Teflon
 * @version $Id$
 */
public class Request extends IRCCommand {
	private static final Logger logger = Logger.getLogger(Request.class);
    public static final Key REQUESTS = new Key(Request.class, "requests", Integer.class);
    public static final Key REQFILLED = new Key(Request.class, "reqfilled", Integer.class);
    public static final Key WEEKREQS = new Key(Request.class, "weekreq", Integer.class);

    private String _requestPath;
    
	public Request(GlobalContext gctx) {
		super(gctx);
		loadConf("conf/drmods.conf");
	}

	public void loadConf(String confFile) {
        Properties cfg = new Properties();
        FileInputStream file;
        try {
            file = new FileInputStream(confFile);
            cfg.load(file);
            _requestPath = cfg.getProperty("request.dirpath");
            file.close();
            if (_requestPath == null) {
                throw new RuntimeException("Unspecified value 'request.dirpath' in " + confFile);        
            }      
        } catch (FileNotFoundException e) {
            logger.error("Error reading " + confFile,e);
            throw new RuntimeException(e.getMessage());
        } catch (IOException e) {
            logger.error("Error reading " + confFile,e);
            throw new RuntimeException(e.getMessage());
        }
	}

    public ArrayList<String> doRequests(String args, MessageCommand msgc) {
	    ArrayList<String> out = new ArrayList<String>();
        ReplacerEnvironment env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);
        env.add("ircnick",msgc.getSource().getNick());
        
        User user = getUser(msgc.getSource());
        if (user == null) {
     	    out.add(ReplacerUtils.jprintf("ident.noident", env, SiteBot.class));
     	    return out;
        }
        env.add("ftpuser",user.getName());
        env.add("reqfilled",new StringTokenizer(msgc.getMessage()).nextToken());
        
        try {
            LinkedRemoteFileInterface rdir = getGlobalContext().getRoot().lookupFile(_requestPath);
            out.add(ReplacerUtils.jprintf("requests.header", env, Request.class));
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
                        out.add(ReplacerUtils.jprintf("requests.list", env, Request.class));	
                    }
                }
            }
            out.add(ReplacerUtils.jprintf("requests.footer", env, Request.class));
        }  catch (FileNotFoundException e) {
            env.add("rdirname",_requestPath);
            out.add(ReplacerUtils.jprintf("request.error", env, Request.class));
            return out; 
        }
        return out;
    }

    public ArrayList<String> doReqfilled(String args, MessageCommand msgc) {
	    ArrayList<String> out = new ArrayList<String>();
        ReplacerEnvironment env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);
        env.add("ircnick",msgc.getSource().getNick());
        
        User user = getUser(msgc.getSource());
        if (user == null) {
     	    out.add(ReplacerUtils.jprintf("ident.noident", env, SiteBot.class));
     	    return out;
        }
        env.add("ftpuser",user.getName());
        
        String dirName;
        try {
            dirName = args;
            if (dirName.length()==0){ 
                out.add(ReplacerUtils.jprintf("reqfilled.usage", env, Request.class)); 
                return out; 
            } 
        } catch (ArrayIndexOutOfBoundsException e) {
            logger.warn("", e);
            out.add(ReplacerUtils.jprintf("reqfilled.usage", env, Request.class));
            return out;
        } catch (StringIndexOutOfBoundsException e) {
            logger.warn("", e);
            out.add(ReplacerUtils.jprintf("reqfilled.usage", env, Request.class));
            return out;
        }
        
        env.add("fdirname",dirName);	
        
        boolean nodir = false;
        boolean fdir = false;
        
        try {
            LinkedRemoteFileInterface dir = getGlobalContext().getRoot().lookupFile(_requestPath);
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
                            out.add(ReplacerUtils.jprintf("reqfilled.success", env, Request.class));
                            break;
                        } catch (IOException e) {
                            logger.warn("", e);
                        }
                        
                        
                    } else nodir = true;
                }
            }
            
            if (nodir && !fdir) out.add(ReplacerUtils.jprintf("reqfilled.error", env, Request.class));
            
        } catch (FileNotFoundException e) {
            env.add("rdirname",_requestPath);
            out.add(ReplacerUtils.jprintf("request.error", env, Request.class));
            return out;
        }
        return out;
    }

    public ArrayList<String> doRequest(String args, MessageCommand msgc) {
	    ArrayList<String> out = new ArrayList<String>();
        ReplacerEnvironment env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);
        env.add("ircnick",msgc.getSource().getNick());
        
        User user = getUser(msgc.getSource());
        if (user == null) {
     	    out.add(ReplacerUtils.jprintf("ident.noident", env, SiteBot.class));
     	    return out;
        }
        env.add("ftpuser",user.getName());
        
        String dirName = args;
        if (dirName.length()==0) { 
            out.add(ReplacerUtils.jprintf("request.usage", env, Request.class)); 
            return out; 
        } 
            
        env.add("rdirname",dirName);
        String requser = user.getName();
        
        
        try {
            LinkedRemoteFileInterface dir = getGlobalContext().getRoot().lookupFile(_requestPath);
            dir.createDirectory("REQUEST-by." + requser + "-" + dirName);
            LinkedRemoteFileInterface reqdir = dir.getFile("REQUEST-by." + requser + "-" + dirName);
            reqdir.setOwner(requser);
            user.getKeyedMap().setObject(Request.REQUESTS, user.getKeyedMap().getObjectInt(Request.REQUESTS)+1);;
            out.add(ReplacerUtils.jprintf("request.success", env, Request.class));
        } catch (FileNotFoundException e) {
            env.add("rdirname",_requestPath);
            out.add(ReplacerUtils.jprintf("request.error", env, Request.class));
            return out;
        } catch (FileExistsException e1) {
            out.add(ReplacerUtils.jprintf("request.exists", env, Request.class));
            return out;
        } 	
        return out;
    }

    public ArrayList<String> doReqdel(String args, MessageCommand msgc) {
	    ArrayList<String> out = new ArrayList<String>();
        ReplacerEnvironment env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);
        env.add("ircnick",msgc.getSource().getNick());
        
        User user = getUser(msgc.getSource());
        if (user == null) {
     	    out.add(ReplacerUtils.jprintf("ident.noident", env, SiteBot.class));
     	    return out;
        }
        env.add("ftpuser",user.getName());
        
        String dirName = args;
        if (dirName.length()==0){ 
            out.add(ReplacerUtils.jprintf("reqdel.usage", env, Request.class)); 
            return out; 
        } 

        env.add("ddirname",dirName);	
        
        boolean nodir = false;
        boolean deldir = false;
        try {
            LinkedRemoteFileInterface dir = getGlobalContext().getRoot().lookupFile(_requestPath);
            for (Iterator iter = dir.getDirectories().iterator(); iter.hasNext();) {
                LinkedRemoteFileInterface file = (LinkedRemoteFileInterface) iter.next();
                if (file.isDirectory()) {
                    if (file.getName().endsWith(dirName)) {
                        nodir = false;
                        if (file.getUsername().equals(user.getName()) || user.isAdmin()) {
                            file.delete();
                            deldir = true;
                            out.add(ReplacerUtils.jprintf("reqdel.success", env, Request.class));
                            break;
                        } else {
                            out.add(ReplacerUtils.jprintf("reqdel.notowner", env, Request.class));
                            break;
                        }
                    } else nodir = true;
                }
            }
            
            if (nodir && !deldir) 
                out.add(ReplacerUtils.jprintf("reqdel.error", env, Request.class));

        } catch (FileNotFoundException e) {
            out.add(ReplacerUtils.jprintf("reqdel.error", env, Request.class));
            return out;
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
	
	private User getUser(FullNick fn) {
		String ident = fn.getNick() + "!" + fn.getUser() + "@" + fn.getHost();
		User user = null;
     	try {
     	    user = getGlobalContext().getUserManager().getUserByIdent(ident);
     	} catch (Exception e) {
     	    logger.warn("Could not identify " + ident);
     	}
     	return user;
	}
}
