/*
 * Created on Dec 6, 2004
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package org.drftpd.sitebot;

import f00f.net.irc.martyr.GenericCommandAutoService;
import f00f.net.irc.martyr.InCommand;
import f00f.net.irc.martyr.commands.MessageCommand;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import org.drftpd.dynamicdata.Key;
import org.drftpd.master.ConnectionManager;
import org.drftpd.plugins.SiteBot;

import org.drftpd.usermanager.NoSuchUserException;
import org.drftpd.usermanager.User;
import org.drftpd.usermanager.UserFileException;

/**
 * @author Teflon
 */
public class Ident extends GenericCommandAutoService implements
		IRCPluginInterface {
    private static final Logger logger = Logger.getLogger(Ident.class);
	private ConnectionManager _cm;
	private SiteBot _irc;

	public Ident(SiteBot ircListener) {
		super(ircListener.getIRCConnection());
		_cm = ircListener.getConnectionManager();
		_irc = ircListener;
	}
	
	public String getCommands() {
        return "!ident(msg)";
    }

	protected void updateCommand(InCommand command) {
        if (!(command instanceof MessageCommand)) {
        	//logger.info("not a MessageCommand");
            return;
        }

        MessageCommand msgc = (MessageCommand) command;
        String msg = msgc.getMessage();
        if (msg.startsWith("!ident ") &&
                msgc.isPrivateToUs(this.getConnection().getClientState())) {

        	String[] args = msg.split(" ");
            if (args.length < 2) {
             	return;
            }
            User user;
            try {
                user = _cm.getGlobalContext().getUserManager().getUserByName(args[1]);
            } catch (NoSuchUserException e) {
                logger.log(Level.WARN, args[1] + " " + e.getMessage(), e);

                return;
            } catch (UserFileException e) {
                logger.log(Level.WARN, "", e);

                return;
            }

            boolean success = user.checkPassword(args[2]);
            
            if (success) {
            	Key key = new Key(this.getClass(),"IRCIdent",String.class);
            	String ident = msgc.getSource().getNick() + "!" 
								+ msgc.getSource().getUser() + "@" 
								+ msgc.getSource().getHost();
            	user.getKeyedMap().setObject(key,ident);
            	try {
					user.commit();
		           	logger.info("Set IRC ident to '"+ident+"' for "+user.getName());
	            	_irc.sayChannel(msgc.getSource().getNick(),"Set IRC ident to '"+ident+"' for "+user.getName());
				} catch (UserFileException e1) {
					logger.warn("Error saving userfile for "+user.getName(),e1);
					_irc.sayChannel(msgc.getSource().getNick(),"Error saving userfile for "+user.getName());
				}
             }
        }
	}


}
