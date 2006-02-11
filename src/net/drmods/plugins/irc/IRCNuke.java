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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.TimeZone;
import java.util.Map.Entry;

import net.sf.drftpd.FileExistsException;
import net.sf.drftpd.Nukee;
import net.sf.drftpd.ObjectNotFoundException;
import net.sf.drftpd.event.NukeEvent;
import net.sf.drftpd.util.ReplacerUtils;

import org.apache.log4j.Logger;
import org.drftpd.GlobalContext;
import org.drftpd.commands.Nuke;
import org.drftpd.commands.UserManagement;
import org.drftpd.plugins.SiteBot;
import org.drftpd.remotefile.LinkedRemoteFile;
import org.drftpd.remotefile.LinkedRemoteFileInterface;
import org.drftpd.sitebot.IRCCommand;
import org.drftpd.usermanager.NoSuchUserException;
import org.drftpd.usermanager.User;
import org.drftpd.usermanager.UserFileException;
import org.tanesha.replacer.ReplacerEnvironment;

import f00f.net.irc.martyr.commands.MessageCommand;
import f00f.net.irc.martyr.util.FullNick;

/**
 * @author Teflon
 * @version $Id$
 */
public class IRCNuke extends IRCCommand {
	private static final Logger logger = Logger.getLogger(IRCNuke.class);
	private int _maxNukes;
	
	public IRCNuke(GlobalContext gctx) {
		super(gctx);
		loadConf("conf/drmods.conf");
	}

	private void loadConf(String confFile) {
        Properties cfg = new Properties();
        FileInputStream file = null;
        try {
            file = new FileInputStream(confFile);
            cfg.load(file);
            String maxNukes = cfg.getProperty("nukes.max");
            if (maxNukes == null) {
                throw new RuntimeException("Unspecified value 'nukes.max' in " + confFile);        
            }
            _maxNukes = Integer.parseInt(maxNukes);
        } catch (Exception e) {
            logger.error("Error reading " + confFile,e);
            throw new RuntimeException(e.getMessage());
        } finally {
        	try {
        		if (file != null) {
        			file.close();
        		}
        	} catch (IOException e) {
        	}
        }
	}

	public ArrayList<String> doNuke(String args, MessageCommand msgc) {
		ArrayList<String> out = new ArrayList<String>();
		ReplacerEnvironment env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);
		env.add("ircnick", msgc.getSource().getNick());

		User ftpuser = getUser(msgc.getSource());
		if (ftpuser == null) {
			out.add(ReplacerUtils.jprintf("ident.noident", env, SiteBot.class));
			return out;
		}
		env.add("ftpuser", ftpuser.getName());

		StringTokenizer st = new StringTokenizer(args);
		// check number of arguments
		if (st.countTokens() < 3) {
			out.add(ReplacerUtils.jprintf("nuke.usage", env, IRCNuke.class));
			return out;
		}

		// read parameters passed
		String searchstr = st.nextToken();
		env.add("searchstr", searchstr);
		int nukemult;
		try {
			nukemult = Integer.parseInt(st.nextToken());
		} catch (NumberFormatException e2) {
			out.add(ReplacerUtils.jprintf("nuke.usage", env, IRCNuke.class));
			return out;
		}
		String nukemsg = st.nextToken("").trim();

		LinkedRemoteFileInterface nukeDir;
		try {
			nukeDir = LinkedRemoteFile.findLatestDir(getGlobalContext()
					.getConnectionManager(), getGlobalContext().getRoot(),
					ftpuser, searchstr);
		} catch (ObjectNotFoundException e) {
			out.add(ReplacerUtils.jprintf("nuke.error", env, IRCNuke.class));
			return out;
		}
		String nukeDirPath = nukeDir.getPath();
		env.add("nukedir", nukeDirPath);
		// get nukees with string as key
		Hashtable<String, Long> nukees = new Hashtable<String, Long>();
		Nuke.nukeRemoveCredits(nukeDir, nukees);

		// // convert key from String to User ////
		HashMap<User, Long> nukees2 = new HashMap<User, Long>(nukees.size());
		for (String username : nukees.keySet()) {

			// String username = (String) iter.next();
			User user;
			try {
				user = getGlobalContext().getUserManager().getUserByName(
						username);
			} catch (NoSuchUserException e1) {
				out.add("Cannot remove credits from " + username + ": "
						+ e1.getMessage());
				logger.warn("", e1);
				user = null;
			} catch (UserFileException e1) {
				out.add("Cannot read user data for " + username + ": "
						+ e1.getMessage());
				logger.warn("", e1);
				return out;
			}
			// nukees contains credits as value
			//if (user == null) we don't do anything below anyway
			if (user != null) {
				nukees2.put(user, (Long) nukees.get(username));
			}
		}

		// rename
		String toDirPath;
		String toName = "[NUKED]-" + nukeDir.getName();
		try {
			toDirPath = nukeDir.getParentFile().getPath();
		} catch (FileNotFoundException ex) {
			logger.fatal("", ex);
			out.add("FileNotFoundException");
			return out;
		}
		try {
			nukeDir.renameTo(toDirPath, toName);
			nukeDir.createDirectory(ftpuser.getName(), ftpuser.getGroup(),
					"REASON-" + nukemsg);
		} catch (IOException ex) {
			logger.warn("", ex);
			out.add(" cannot rename to \"" + toDirPath + "/" + toName + "\": "
					+ ex.getMessage());
			return out;
		}

		long nukeDirSize = 0;
		long nukedAmount = 0;

		// update credits, nukedbytes, timesNuked, lastNuked
		// for (Iterator iter = nukees2.keySet().iterator(); iter.hasNext();) {
		for (Entry<User, Long> nukeeEntry : nukees2.entrySet()) {
			// User nukee = (User) iter.next();
			User nukee = nukeeEntry.getKey();
			long size = nukeeEntry.getValue().longValue();

			long debt = Nuke.calculateNukedAmount(size, nukee.getKeyedMap()
					.getObjectFloat(UserManagement.RATIO), nukemult);

			nukedAmount += debt;
			nukeDirSize += size;
			nukee.updateCredits(-debt);
			nukee.updateUploadedBytes(-size);
			nukee.getKeyedMap().incrementObjectLong(Nuke.NUKEDBYTES, debt);
			nukee.getKeyedMap().incrementObjectLong(Nuke.NUKED);
			nukee.getKeyedMap().setObject(Nuke.LASTNUKED,
					new Long(System.currentTimeMillis()));
			try {
				nukee.commit();
			} catch (UserFileException e1) {
				out.add("Error writing userfile: " + e1.getMessage());
				logger.warn("Error writing userfile", e1);
			}
		}
		NukeEvent nuke = new NukeEvent(ftpuser, "NUKE", nukeDirPath,
				nukeDirSize, nukedAmount, nukemult, nukemsg, nukees);

		Nuke.getNukeLog().add(nuke);
		getGlobalContext().getConnectionManager().dispatchFtpEvent(nuke);
		return out;
	}

	public ArrayList<String> doUnnuke(String args, MessageCommand msgc) {
	    ArrayList<String> out = new ArrayList<String>();
		ReplacerEnvironment env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);
		env.add("ircnick", msgc.getSource().getNick());
		
        User ftpuser = getUser(msgc.getSource());
        if (ftpuser == null) {
     	    out.add(ReplacerUtils.jprintf("ident.noident", env, SiteBot.class));
     	    return out;
        }
        env.add("ftpuser",ftpuser.getName());

        StringTokenizer st = new StringTokenizer(args);
		//check number of arguments
		if (st.countTokens() < 1) {
			out.add(ReplacerUtils.jprintf("unnuke.usage", env, IRCNuke.class));
			return out;
		}
		
		//read parameters passed
		String toName = st.nextToken();
		String nukeName = "[NUKED]-" + toName;
		String reason = st.hasMoreTokens() ? st.nextToken("").trim() : "";

		env.add("searchstr",nukeName);
		
		LinkedRemoteFileInterface nukeDir;
		try {
			nukeDir = LinkedRemoteFile.findLatestDir(getGlobalContext().getConnectionManager(), getGlobalContext().getRoot(), ftpuser, nukeName);
		} catch (ObjectNotFoundException e2) {
			out.add(ReplacerUtils.jprintf("nuke.error", env, IRCNuke.class));
			return out;
		}
		
		String toPath = nukeDir.getParentFileNull().getPath() + "/" + toName;
		String toDir = nukeDir.getParentFileNull().getPath();
		NukeEvent nuke;
		try {
			nuke = Nuke.getNukeLog().get(toPath);
		} catch (ObjectNotFoundException ex) {
			out.add(ex.getMessage());
			logger.warn(ex);
			return out;
		}
			
		for (Iterator iter = nuke.getNukees2().iterator(); iter.hasNext();) {
			Nukee nukeeObj = (Nukee) iter.next();
			String nukeeName = nukeeObj.getUsername();
			User nukee;
			try {
				nukee = getGlobalContext().getUserManager().getUserByName(nukeeName);
			} catch (NoSuchUserException e) {
			    out.add(nukeeName + ": no such user");
				continue;
			} catch (UserFileException e) {
			    out.add(nukeeName + ": error reading userfile");
				logger.fatal("error reading userfile", e);
				continue;
			}
			long nukedAmount =
				Nuke.calculateNukedAmount(
					nukeeObj.getAmount(),
					nukee.getKeyedMap().getObjectFloat(UserManagement.RATIO),
					nuke.getMultiplier());

			nukee.updateCredits(nukedAmount);
			nukee.updateUploadedBytes(nukeeObj.getAmount());
            nukee.getKeyedMap().incrementObjectInt(Nuke.NUKED, -1);

			try {
				nukee.commit();
			} catch (UserFileException e3) {
				logger.fatal("Eroror saving userfile for " + nukee.getName(),e3);
				out.add("Error saving userfile for " + nukee.getName());
			}
		}//for
			
		try {
			Nuke.getNukeLog().remove(toPath);
		} catch (ObjectNotFoundException e) {
			logger.warn("Error removing nukelog entry", e);
		}
		try {
			nukeDir.renameTo(toDir, toName);
		} catch (FileExistsException e1) {
		    out.add("Error renaming nuke, target dir already exists");
		} catch (IOException e1) {
			//response.addComment("Error: " + e1.getMessage());
			logger.fatal(
				"Illegaltargetexception: means parent doesn't exist",
				e1);
		}
			
		try {
			LinkedRemoteFileInterface reasonDir =
				nukeDir.getFile("REASON-" + nuke.getReason());
			if (reasonDir.isDirectory())
				reasonDir.delete();
		} catch (FileNotFoundException e3) {
			logger.debug(
				"Failed to delete 'REASON-" + nuke.getReason() + "' dir in UNNUKE",
				e3);
		}

		nuke.setCommand("UNNUKE");
		nuke.setReason(reason);
		nuke.setUser(ftpuser);
		getGlobalContext().getConnectionManager().dispatchFtpEvent(nuke);	
		return out;
	}

	public ArrayList<String> doNukes(String args, MessageCommand msgc) {
	    ArrayList<String> out = new ArrayList<String>();
		ReplacerEnvironment env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);
		env.add("ircnick", msgc.getSource().getNick());

		//check number of arguments
		int nukeCount = 0;
		if (!args.equals("")) {
			try {
				nukeCount = Integer.parseInt(args);
			} catch (NumberFormatException e2) {
				logger.warn("parameter passed to !nukes is not a valid Integer", e2);
				out.add(ReplacerUtils.jprintf("nukes.usage", env, IRCNuke.class));
				return out;
			}
		}
		if (nukeCount > _maxNukes || nukeCount <= 0)
			nukeCount = _maxNukes;

		List allNukes = Nuke.getNukeLog().getAll();
		int count = 0;
		
		if (allNukes.size() == 0) {
			out.add(ReplacerUtils.jprintf("nukes.nonukes", env, IRCNuke.class));
		} else {
			for (int i = allNukes.size()-1; i >= 0; i--) {
			//for (Iterator iter = allNukes.iterator(); iter.hasNext(); ) {
				if (count >= nukeCount)
					break;
				NukeEvent nuke = (NukeEvent) allNukes.get(i); //iter.next();
				env.add("nukepath", nuke.getPath());
				env.add("nukereason", nuke.getReason());
				env.add("nukemult", Integer.toString(nuke.getMultiplier()));
				env.add("nuker", nuke.getUser().getName());
				SimpleDateFormat dFormat = new SimpleDateFormat("MM/dd/yyyy h:mm a zzz");
				dFormat.setTimeZone(TimeZone.getDefault());
				env.add("nuketime", dFormat.format(new Date(nuke.getTime())));
			
				out.add(ReplacerUtils.jprintf("nukes.msg", env, IRCNuke.class));
				count++;
			}
		}
		return out;
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
