/*
 * Created on Aug 29, 2004
 */
package net.drmods.plugins.irc;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.TimeZone;

import net.sf.drftpd.FileExistsException;
import net.sf.drftpd.Nukee;
import net.sf.drftpd.ObjectNotFoundException;
import net.sf.drftpd.event.NukeEvent;
import net.sf.drftpd.util.ReplacerUtils;

import org.apache.log4j.Logger;
import org.drftpd.GlobalContext;
import org.drftpd.commands.Nuke;
import org.drftpd.commands.UserManagement;
import org.drftpd.master.ConnectionManager;
import org.drftpd.plugins.SiteBot;
import org.drftpd.remotefile.LinkedRemoteFileInterface;
import org.drftpd.sitebot.IRCPluginInterface;
import org.drftpd.usermanager.NoSuchUserException;
import org.drftpd.usermanager.User;
import org.drftpd.usermanager.UserFileException;
import org.tanesha.replacer.ReplacerEnvironment;

import f00f.net.irc.martyr.GenericCommandAutoService;
import f00f.net.irc.martyr.InCommand;
import f00f.net.irc.martyr.commands.MessageCommand;

/**
 * @author Teflon
 * @version 1.0
 */
public class IRCNuke extends GenericCommandAutoService implements IRCPluginInterface {

	private static final Logger logger = Logger.getLogger(IRCNuke.class);
	private ConnectionManager _cm;
	private SiteBot _listener;
	
	public String getCommands() {
	    String tr = _listener.getCommandPrefix();
		return tr + "nuke " + tr + "unnuke " + tr + "nukes";
	}

	public String getCommandsHelp(User user) {
	    String pre = _listener.getCommandPrefix();
        String help = "";
        if (_listener.getIRCConfig().checkIrcPermission(pre + "nuke", user))
            help += pre + "nuke <dirname> <multiplier> <reason> : Nuke a release.\n";
        if (_listener.getIRCConfig().checkIrcPermission(pre + "unnuke", user))
            help += pre + "unnuke <dirname> [reason] : Unnuke a release.\n";
        if (_listener.getIRCConfig().checkIrcPermission(pre + "nukes", user))
            help += pre + "nukes : List current nuked releases.\n";
		return help;
	}
	public IRCNuke(SiteBot listener) {
		super(listener.getIRCConnection());
		_listener = listener;
		_cm = listener.getGlobalContext().getConnectionManager();
	}

	private GlobalContext getGlobalContext() {
		return _listener.getGlobalContext();
	}

	protected void updateCommand(InCommand command) {
		if (!(command instanceof MessageCommand))
			return;
		MessageCommand msgc = (MessageCommand) command;
		String msg = msgc.getMessage().trim();
	    String tr = _listener.getCommandPrefix();

		if (msg.startsWith(tr + "nukes")){
			doNUKES(msgc);
		} else if (msg.startsWith(tr + "nuke")) {
			doNUKE(msgc);
		} else if (msg.startsWith(tr + "unnuke")) {
			doUNNUKE(msgc);
		}
	}

	private void doNUKE(MessageCommand msgc) {
		ReplacerEnvironment env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);
		env.add("botnick",_listener.getIRCConnection().getClientState().getNick().getNick());
		env.add("ircnick",msgc.getSource().getNick());
        //Get the ftp user account based on irc ident
		User ftpuser;
		try {
            ftpuser = _listener.getIRCConfig().lookupUser(msgc.getSource());
        } catch (NoSuchUserException e) {
			_listener.sayChannel(msgc.getDest(), 
					ReplacerUtils.jprintf("ident.noident", env, SiteBot.class));
			return;
        }
		env.add("ftpuser",ftpuser.getName());

		if (!_listener.getIRCConfig().checkIrcPermission(_listener.getCommandPrefix() + "nuke",ftpuser)) {
			_listener.sayChannel(msgc.getDest(), 
					ReplacerUtils.jprintf("ident.denymsg", env, SiteBot.class));
			return;				
		}

		String msg = msgc.getMessage().trim();
		if (msg.equals("!nuke")) {
			_listener.sayChannel(msgc.getDest(), 
					ReplacerUtils.jprintf("nuke.usage", env, IRCNuke.class));
			return;
		}

		//check number of arguments
		String args[] = msg.split(" ");
		if (args.length < 4) {
			_listener.sayChannel(msgc.getDest(), 
					ReplacerUtils.jprintf("nuke.usage", env, IRCNuke.class));
			return;
		}
		
		//read parameters passed
		String searchstr = args[1];
		int nukemult;
		try {
			nukemult = Integer.parseInt(args[2]);
		} catch (NumberFormatException e2) {
			_listener.sayChannel(msgc.getDest(), 
					ReplacerUtils.jprintf("nuke.usage", env, IRCNuke.class));
			return;
		}
		String nukemsg = "";
		for (int i=3; i < args.length; i++)
			nukemsg += args[i] + " ";
		
		nukemsg = nukemsg.trim();
		env.add("searchstr",searchstr);
		
		LinkedRemoteFileInterface nukeDir = 
			findDir(_cm, _cm.getGlobalContext().getRoot(), ftpuser, searchstr);

		if (nukeDir == null){
			_listener.sayChannel(msgc.getDest(), 
					ReplacerUtils.jprintf("nuke.error", env, IRCNuke.class));
		} else {
			String nukeDirPath = nukeDir.getPath();
			env.add("nukedir",nukeDirPath);
			//get nukees with string as key
			Hashtable<String,Long> nukees = new Hashtable<String,Long>();
			Nuke.nukeRemoveCredits(nukeDir, nukees);

			//// convert key from String to User ////
			HashMap<User,Long> nukees2 = new HashMap<User,Long>(nukees.size());
			for (String username : nukees.keySet()) {

				//String username = (String) iter.next();
				User user;
				try {
					user =
						_cm.getGlobalContext().getUserManager().getUserByName(username);
				} catch (NoSuchUserException e1) {
				    _listener.sayChannel(msgc.getDest(),"Cannot remove credits from " 
						+ username + ": " + e1.getMessage());
					logger.warn("", e1);
					user = null;
				} catch (UserFileException e1) {
				    _listener.sayChannel(msgc.getDest(),"Cannot read user data for " 
						+ username + ": " + e1.getMessage());
					logger.warn("", e1);
					return;
				}
				// nukees contains credits as value
				if (user == null) {
					Long add = (Long) nukees2.get(null);
					if (add == null) {
						add = new Long(0);
					}
					nukees2.put(
						user,
						new Long(
							add.longValue()
								+ ((Long) nukees.get(username)).longValue()));
				} else {
					nukees2.put(user, (Long)nukees.get(username));
				}
			}

			//rename
			String toDirPath;
			String toName = "[NUKED]-" + nukeDir.getName();
			try {
				toDirPath = nukeDir.getParentFile().getPath();
			} catch (FileNotFoundException ex) {
				logger.fatal("", ex);
				return;
			}
			try {
				nukeDir.renameTo(toDirPath, toName);
				nukeDir.createDirectory(
					ftpuser.getName(),
					ftpuser.getGroup(),
					"REASON-" + nukemsg);
			} catch (IOException ex) {
				logger.warn("", ex);
				_listener.sayChannel(msgc.getDest(),
					" cannot rename to \""
						+ toDirPath
						+ "/"
						+ toName
						+ "\": "
						+ ex.getMessage());
				return;
			}

			long nukeDirSize = 0;
			long nukedAmount = 0;

			//update credits, nukedbytes, timesNuked, lastNuked
//			for (Iterator iter = nukees2.keySet().iterator(); iter.hasNext();) {
			for (User nukee : nukees2.keySet()) {
			    //User nukee = (User) iter.next();
				if (nukee == null)
					continue;
				long size = ((Long) nukees2.get(nukee)).longValue();

				long debt =
					Nuke.calculateNukedAmount(size, 
					        nukee.getKeyedMap().getObjectFloat(UserManagement.RATIO), nukemult);

				nukedAmount += debt;
				nukeDirSize += size;
				nukee.updateCredits(-debt);
				nukee.updateUploadedBytes(-size);
	            nukee.getKeyedMap().incrementObjectLong(Nuke.NUKEDBYTES, debt);
	            nukee.getKeyedMap().incrementObjectLong(Nuke.NUKED);
	            nukee.getKeyedMap().setObject(Nuke.LASTNUKED, new Long(System.currentTimeMillis()));
				try {
					nukee.commit();
				} catch (UserFileException e1) {
					_listener.sayChannel(msgc.getDest(),
						"Error writing userfile: " + e1.getMessage());
					logger.warn("Error writing userfile", e1);
				}
			}
			NukeEvent nuke =
				new NukeEvent(
					ftpuser,
					"NUKE",
					nukeDirPath,
					nukeDirSize,
					nukedAmount,
					nukemult,
					nukemsg,
					nukees);

			Nuke dpsn = (Nuke) 
				_cm.getCommandManagerFactory()
					.getHandlersMap()
					.get(Nuke.class);
			dpsn.getNukeLog().add(nuke);
			_cm.dispatchFtpEvent(nuke);
		}
	}

	private void doUNNUKE(MessageCommand msgc) {
		ReplacerEnvironment env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);
		env.add("botnick",_listener.getIRCConnection().getClientState().getNick().getNick());
		env.add("ircnick",msgc.getSource().getNick());
        //Get the ftp user account based on irc ident
		User ftpuser;
		try {
            ftpuser = _listener.getIRCConfig().lookupUser(msgc.getSource());
        } catch (NoSuchUserException e) {
			_listener.sayChannel(msgc.getDest(), 
					ReplacerUtils.jprintf("ident.noident", env, SiteBot.class));
			return;
        }
		env.add("ftpuser",ftpuser.getName());

		if (!_listener.getIRCConfig().checkIrcPermission(_listener.getCommandPrefix() + "unnuke",ftpuser)) {
			_listener.sayChannel(msgc.getDest(), 
					ReplacerUtils.jprintf("ident.denymsg", env, SiteBot.class));
			return;				
		}

		String msg = msgc.getMessage().trim();
		if (msg.equals("!unnuke")) {
			_listener.sayChannel(msgc.getDest(), 
					ReplacerUtils.jprintf("unnuke.usage", env, IRCNuke.class));
			return;
		}

		//check number of arguments
		String args[] = msg.split(" ");
		if (args.length < 2) {
			_listener.sayChannel(msgc.getDest(), 
					ReplacerUtils.jprintf("unnuke.usage", env, IRCNuke.class));
			return;
		}
		
		//read parameters passed
		String toName = args[1];
		String nukeName = "[NUKED]-" + toName;
		String reason = "";
		for (int i=2; i < args.length; i++)
			reason += args[i] + " ";
		
		reason = reason.trim();
		env.add("searchstr",nukeName);
		
		LinkedRemoteFileInterface nukeDir = 
			findDir(_cm, _cm.getGlobalContext().getRoot(), ftpuser, nukeName);
			
		if (nukeDir == null){
			_listener.sayChannel(msgc.getDest(), 
					ReplacerUtils.jprintf("nuke.error", env, IRCNuke.class));
			return;
		} 
		
		String toPath = nukeDir.getParentFileNull().getPath() + "/" + toName;
		String toDir = nukeDir.getParentFileNull().getPath();
		NukeEvent nuke;
		Nuke dpsn;
		try {
			 dpsn = (Nuke) 
				_cm.getCommandManagerFactory()
					.getHandlersMap()
					.get(Nuke.class);
			nuke = dpsn.getNukeLog().get(toPath);
		} catch (ObjectNotFoundException ex) {
			_listener.sayChannel(msgc.getDest(),ex.getMessage());
			logger.warn(ex);
			return;
		}
			
		for (Iterator iter = nuke.getNukees2().iterator(); iter.hasNext();) {
			Nukee nukeeObj = (Nukee) iter.next();
			String nukeeName = nukeeObj.getUsername();
			User nukee;
			try {
				nukee =
					_cm.getGlobalContext().getUserManager().getUserByName(
						nukeeName);
			} catch (NoSuchUserException e) {
			    _listener.sayChannel(msgc.getDest(),nukeeName + ": no such user");
				continue;
			} catch (UserFileException e) {
			    _listener.sayChannel(msgc.getDest(),nukeeName + ": error reading userfile");
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
				logger.fatal(
					"Eroror saving userfile for " + nukee.getName(),
					e3);
				_listener.sayChannel(msgc.getDest(),
					"Error saving userfile for " + nukee.getName());
			}
		}//for
			
		try {
			dpsn.getNukeLog().remove(toPath);
		} catch (ObjectNotFoundException e) {
			logger.warn("Error removing nukelog entry", e);
		}
		try {
			nukeDir.renameTo(toDir, toName);
		} catch (FileExistsException e1) {
		    _listener.sayChannel(msgc.getDest(),
				"Error renaming nuke, target dir already exists");
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
		_cm.dispatchFtpEvent(nuke);	
	}

	private void doNUKES(MessageCommand msgc) {
		ReplacerEnvironment env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);
		env.add("botnick",_listener.getIRCConnection().getClientState().getNick().getNick());
		env.add("ircnick",msgc.getSource().getNick());
		String msg = msgc.getMessage().trim();
        //Get the ftp user account based on irc ident
		User ftpuser;
		try {
            ftpuser = _listener.getIRCConfig().lookupUser(msgc.getSource());
        } catch (NoSuchUserException e) {
			_listener.sayChannel(msgc.getDest(), 
					ReplacerUtils.jprintf("ident.noident", env, SiteBot.class));
			return;
        }
		env.add("ftpuser",ftpuser.getName());

		if (!_listener.getIRCConfig().checkIrcPermission(_listener.getCommandPrefix() + "nukes",ftpuser)) {
			_listener.sayChannel(msgc.getDest(), 
					ReplacerUtils.jprintf("ident.denymsg", env, SiteBot.class));
			return;				
		}

		//set the maximum nuber of nukes
		int maxNukeCount = 0;
		try {
			maxNukeCount=Integer.parseInt(ReplacerUtils.jprintf("nukes.max", env, IRCNuke.class));
		} catch (NumberFormatException e3) {
			logger.warn("nukes.max in IRCNuke.properties is not set to a valid integer value.", e3);
			return;
		}
		
		//check number of arguments
		String args[] = msg.split(" ");
		int nukeCount = 0;
		if (args.length > 1) {
			try {
				nukeCount = Integer.parseInt(args[1]);
			} catch (NumberFormatException e2) {
				logger.warn("parameter passed to !nukes is not a valid Integer", e2);
				_listener.sayChannel(msgc.getDest(), 
						ReplacerUtils.jprintf("nukes.usage", env, IRCNuke.class));
				return;
			}
		}	
		if (nukeCount > maxNukeCount || nukeCount <= 0)
			nukeCount = maxNukeCount;

		Nuke dpsn;
		dpsn = (Nuke) 
				_cm.getCommandManagerFactory()
					.getHandlersMap()
					.get(Nuke.class);
		List allNukes = dpsn.getNukeLog().getAll();
		int count = 0;
		
		if (allNukes.size() == 0) {
			_listener.sayChannel(msgc.getDest(), 
					ReplacerUtils.jprintf("nukes.nonukes", env, IRCNuke.class));
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
			
				_listener.sayChannel(msgc.getDest(), 
						ReplacerUtils.jprintf("nukes.msg", env, IRCNuke.class));
				count++;
			}
		}
		
	}

	private static LinkedRemoteFileInterface findDir(
		ConnectionManager conn,
		LinkedRemoteFileInterface dir,
		User user,
		String searchstring) {

		if (!conn.getGlobalContext().getConfig().checkPathPermission("privpath",user, dir, true)) {
			logger.debug("privpath: "+dir.getPath());
			return null;
		}

		for (Iterator iter = dir.getDirectories().iterator(); iter.hasNext();) {
			LinkedRemoteFileInterface file = (LinkedRemoteFileInterface) iter.next();
			if (file.isDirectory()) {
				if (file.getName().toLowerCase().equals(searchstring.toLowerCase())) {
					logger.info("Found " + file.getPath());
					return file;
				} 
				LinkedRemoteFileInterface dir2 = findDir(conn, file, user, searchstring);
				if (dir2 != null) {
					return dir2;
				}		
			}
		}
		return null;
	}

	private void say2(String propvar, ReplacerEnvironment env) {
		_listener.sayGlobal( 
			ReplacerUtils.jprintf(propvar, env, IRCNuke.class));
	}

}
