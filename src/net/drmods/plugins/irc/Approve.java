/* 
 * Approve plugin for drftpd 1.1.4
 * Created on Aug 14, 2004 by Teflon
 *
 */
package net.drmods.plugins.irc;

import java.util.Iterator;

import net.sf.drftpd.FileExistsException;
import net.sf.drftpd.event.DirectoryFtpEvent;
import net.sf.drftpd.util.ReplacerUtils;

import org.apache.log4j.Logger;
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
 *
 */
public class Approve extends GenericCommandAutoService implements IRCPluginInterface {

	private static final Logger logger = Logger.getLogger(Approve.class);

	private SiteBot _listener;
    private String _trigger;

	public String getCommands() {
		return _trigger + "approve";
	}

	public String getCommandsHelp() {
		return _trigger + "approve <dir>: Creates a subfolder in the given dir named Approved.by.<User>";
	}

	private ConnectionManager getConnectionManager() {
		return _listener.getConnectionManager();
	}

	public Approve(SiteBot listener) {
		super(listener.getIRCConnection());
		_listener = listener;
        _trigger = _listener.getCommandPrefix();
	}

	protected void updateCommand(InCommand command) {
		if (!(command instanceof MessageCommand)) {
			return;
		}
		MessageCommand msgc = (MessageCommand) command;
		if (msgc.isPrivateToUs(_listener.getIRCConnection().getClientState())) {
			return;
		}

		ReplacerEnvironment env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);
		env.add("ircnick",msgc.getSource().getNick());

		String msg = msgc.getMessage().trim();
		if (msg.equals(_trigger + "approve")) {
			_listener.sayChannel(msgc.getDest(), 
				ReplacerUtils.jprintf("approve.usage", env, Approve.class));			
		} else if (msg.startsWith(_trigger + "approve ")) {
			String dirName;
			try {
				dirName = msgc.getMessage().substring("!approve ".length());
			} catch (ArrayIndexOutOfBoundsException e) {
				logger.warn("", e);
				_listener.sayChannel(msgc.getDest(), 
					ReplacerUtils.jprintf("approve.usage", env, Approve.class));
				return;
			} catch (StringIndexOutOfBoundsException e) {
				logger.warn("", e);
				_listener.sayChannel(msgc.getDest(), 
					ReplacerUtils.jprintf("approve.usage", env, Approve.class));
				return;
			}
			env.add("sdirname",dirName);
			
         	String ident = msgc.getSource().getNick() + "!" 
         					+ msgc.getSource().getUser() + "@" 
         					+ msgc.getSource().getHost();
			//Get the ftp user account based on irc ident
			User user;
            try {
                user = getConnectionManager().getGlobalContext().getUserManager().getUserByIdent(ident);
            } catch (NoSuchUserException e3) {
				logger.warn("Could not identify " + ident);
				_listener.sayChannel(msgc.getDest(), 
						ReplacerUtils.jprintf("approve.noident", env, Approve.class));				
				return;
            } catch (UserFileException e3) {
				logger.warn("Could not identify " + ident);
				_listener.sayChannel(msgc.getDest(), 
						ReplacerUtils.jprintf("approve.noident", env, Approve.class));				
				return;
            }
            			
			env.add("ftpuser",user.getName());			
			if (!isAllowed(user)) {
				_listener.sayChannel(msgc.getDest(),
					ReplacerUtils.jprintf("approve.denymsg", env, Approve.class));
				return;				
			}

			LinkedRemoteFileInterface dir = findDir(getConnectionManager(),
													 getConnectionManager().getGlobalContext().getRoot(),
													 user,
													 dirName);
			if (dir!= null){
				env.add("sdirpath",dir.getPath());
				String approveDirName = ReplacerUtils.jprintf("approve.dirname", env, Approve.class);
				env.add("adirname",approveDirName);
				env.add("adirpath",dir.getPath() + "/" + approveDirName);
				try {
					LinkedRemoteFileInterface newdir = dir.createDirectory(approveDirName);
					newdir.setOwner(user.getName());
					newdir.setGroup(user.getGroup());
					_listener.sayChannel(msgc.getDest(), 
										 ReplacerUtils.jprintf("approve.success", env, Approve.class));
					getConnectionManager().dispatchFtpEvent(
							new DirectoryFtpEvent(null, "MKD", newdir));
				} catch (FileExistsException e1) {
					_listener.sayChannel(msgc.getDest(), 
										 ReplacerUtils.jprintf("approve.exists", env, Approve.class));
				}
			} else {
				_listener.sayChannel(msgc.getDest(), 
									 ReplacerUtils.jprintf("approve.error", env, Approve.class));
			}

		}
	}

	private static LinkedRemoteFileInterface findDir(
		ConnectionManager conn,
		LinkedRemoteFileInterface dir,
		User user,
		String searchstring) {

		if (!conn.getGlobalContext().getConfig().checkPathPermission("privpath", user, dir, true)) {
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
				LinkedRemoteFileInterface dir2 = findDir(conn, file, user, searchstring);
				if (dir2 != null) {
					return dir2;
				}		
			}
		}
		return null;
	}

	private boolean isAllowed(User user) {
		//check if user is in any allowed groups
		boolean allowed = false;
		String allowedgroups = ReplacerUtils.jprintf("approve.allow", null, Approve.class).trim();
		if (!allowedgroups.equals("*")) {
			String aGroups[] = allowedgroups.split(" ");
			for (int i=0; i < aGroups.length; i++) {
				if (user.isMemberOf(aGroups[i])) {
					allowed = true;
					logger.info(user.getName() + " is allowed to use !approve as he is in "
						+ "group " + aGroups[i]);
				}
			}
		} else {
			allowed = true;
		}
					
		//check if user is in any denied groups	
		String deniedgroups = ReplacerUtils.jprintf("approve.deny", null, Approve.class).trim();
		if (!deniedgroups.equals("*")){
			String dGroups[] = deniedgroups.split(" ");
			for (int i=0; i < dGroups.length; i++) {
				if (user.isMemberOf(dGroups[i])) {
					allowed = false;
					logger.info(user.getName() + "is not allowed to use !approve because "
						+ "he is in the " + dGroups[i] + " group");
				}
			}
		} else {
			allowed = false;
		}
		
		return allowed;
	}
}
